package com.rsb.sheetsui.worker

import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rsb.sheetsui.data.local.dao.PendingActionDao
import com.rsb.sheetsui.data.local.entity.PendingActionEntity
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: android.content.Context,
    @Assisted params: WorkerParameters,
    private val pendingActionDao: PendingActionDao,
    private val repository: SpreadsheetRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val pending = pendingActionDao.getAllPendingSync()
        if (pending.isEmpty()) return Result.success()

        var anySuccess = false
        for (action in pending) {
            try {
                when (action.actionType) {
                    "APPEND" -> {
                        val row = parseRowData(action.rowData)
                        repository.appendRow(
                            action.spreadsheetId,
                            action.sheetName,
                            row
                        ).getOrThrow()
                    }
                    "UPDATE" -> {
                        val row = parseRowData(action.rowData)
                        val rowIndex = action.rowIndex ?: return Result.failure()
                        repository.updateRow(
                            action.spreadsheetId,
                            action.sheetName,
                            rowIndex,
                            row,
                            mergeRanges = null,
                            headerRowIndex = 0,
                            auditInfo = null
                        ).getOrThrow()
                    }
                    else -> {
                        pendingActionDao.deleteById(action.id)
                        continue
                    }
                }
                pendingActionDao.deleteById(action.id)
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for action ${action.id}", e)
                pendingActionDao.updateError(
                    action.id,
                    action.retryCount + 1,
                    e.message
                )
            }
        }
        return if (anySuccess) Result.success() else Result.retry()
    }

    private fun parseRowData(json: String): List<Any?> {
        val arr = gson.fromJson(json, JsonArray::class.java)
        return (0 until arr.size()).map { i ->
            val el = arr.get(i)
            when {
                el.isJsonNull -> null
                el.isJsonPrimitive -> el.asString
                else -> el.toString()
            }
        }
    }
}
