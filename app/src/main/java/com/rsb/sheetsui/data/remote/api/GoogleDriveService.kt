package com.rsb.sheetsui.data.remote.api

import com.rsb.sheetsui.data.remote.dto.DriveFileDto
import com.rsb.sheetsui.data.remote.dto.DriveFileListDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit interface for Google Drive API v3.
 * Base URL: https://www.googleapis.com
 *
 * Used to fetch a list of spreadsheets from the user's Google Drive.
 * All endpoints require OAuth 2.0 authentication.
 */
interface GoogleDriveService {

    /**
     * Lists the user's files filtered by mimeType (spreadsheets).
     * GET https://www.googleapis.com/drive/v3/files
     *
     * @param query Search query. Use mimeType='application/vnd.google-apps.spreadsheet' for spreadsheets
     * @param pageSize Max number of files to return (1-1000)
     * @param pageToken Token for pagination (from previous response)
     * @param orderBy Sort order (e.g. "modifiedTime desc", "name")
     * @param fields Comma-separated list of fields to return (reduces payload size)
     */
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String = "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false",
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String = "modifiedTime desc",
        @Query("fields") fields: String = "nextPageToken,files(id,name,mimeType,modifiedTime,createdTime,webViewLink)",
        @Query("corpora") corpora: String = "user"
    ): Response<DriveFileListDto>

    /**
     * Lists the user's Google Forms.
     * Uses mimeType='application/vnd.google-apps.form'
     */
    @GET("drive/v3/files")
    suspend fun listForms(
        @Query("q") query: String = "mimeType='application/vnd.google-apps.form' and trashed=false",
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String = "modifiedTime desc",
        @Query("fields") fields: String = "nextPageToken,files(id,name,mimeType,modifiedTime,createdTime,webViewLink)",
        @Query("corpora") corpora: String = "user"
    ): Response<DriveFileListDto>

    /**
     * Permanently deletes a file (spreadsheet) from Drive.
     * DELETE https://www.googleapis.com/drive/v3/files/{fileId}
     */
    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(@Path("fileId") fileId: String): Response<Unit>

    @GET("drive/v3/files/{fileId}")
    suspend fun getFile(
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "modifiedTime"
    ): Response<DriveFileDto>

    /**
     * Exports a Google Workspace file (e.g. Sheets) to the given MIME type.
     * GET drive/v3/files/{fileId}/export?mimeType=...
     */
    @Streaming
    @GET("drive/v3/files/{fileId}/export")
    suspend fun exportFile(
        @Path("fileId") fileId: String,
        @Query("mimeType") mimeType: String
    ): Response<ResponseBody>
}
