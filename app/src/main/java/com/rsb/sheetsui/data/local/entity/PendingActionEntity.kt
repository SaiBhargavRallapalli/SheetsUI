package com.rsb.sheetsui.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a pending offline change to be synced to Google Sheets.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val spreadsheetId: String,
    val sheetName: String,
    val rowIndex: Int?,
    val rowData: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)
