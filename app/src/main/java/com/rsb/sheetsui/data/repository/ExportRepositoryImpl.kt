package com.rsb.sheetsui.data.repository

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.rsb.sheetsui.data.remote.api.GoogleDriveService
import com.rsb.sheetsui.domain.repository.ExportFormat
import com.rsb.sheetsui.domain.repository.ExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExportRepository"

@Singleton
class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val driveService: GoogleDriveService
) : ExportRepository {

    override suspend fun exportAndSave(
        spreadsheetId: String,
        spreadsheetName: String,
        format: ExportFormat
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val response = driveService.exportFile(spreadsheetId, format.mimeType)
            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()
                Log.e(TAG, "Drive export failed ${response.code()}: $err")
                throw Exception("Export failed (${response.code()})")
            }

            val body = response.body() ?: throw Exception("Empty export response")
            val bytes = body.bytes()

            val sanitizedName = spreadsheetName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(100)
            val fileName = "${sanitizedName}.${format.extension}"

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = context.contentResolver.insert(collection, contentValues)
                    ?: throw Exception("Failed to create file in Downloads")
                context.contentResolver.openOutputStream(itemUri)?.use { os ->
                    os.write(bytes)
                }
                itemUri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val file = java.io.File(downloadsDir, fileName)
                file.outputStream().use { it.write(bytes) }
                Uri.fromFile(file)
            }

            Log.d(TAG, "Exported to $uri")
            uri
        }
    }
}
