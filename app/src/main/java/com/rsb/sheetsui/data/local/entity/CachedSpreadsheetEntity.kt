package com.rsb.sheetsui.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching spreadsheet metadata offline.
 */
@Entity(tableName = "cached_spreadsheets")
data class CachedSpreadsheetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modifiedTime: String?,
    val createdAt: Long,
    val lastSyncedAt: Long?
)
