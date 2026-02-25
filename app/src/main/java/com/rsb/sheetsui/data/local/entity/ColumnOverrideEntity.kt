package com.rsb.sheetsui.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User override for column type inference.
 * Key: spreadsheetId + sheetName + columnIndex
 */
@Entity(
    tableName = "column_overrides",
    primaryKeys = ["spreadsheetId", "columnIndex"],
    indices = [Index("spreadsheetId")]
)
data class ColumnOverrideEntity(
    val spreadsheetId: String,
    val columnIndex: Int,
    val fieldType: String,
    val updatedAt: Long = System.currentTimeMillis()
)
