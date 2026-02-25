package com.rsb.sheetsui.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached sheet data for smart caching.
 * Full data stored as JSON; hash and lastModifiedTime used to avoid redundant fetches.
 */
@Entity(tableName = "sheet_cache")
data class SheetCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val dataHash: String,
    val headersJson: String,
    val rowsJson: String,
    val formulaRowsJson: String,
    val mergeRangesJson: String,
    val columnValidationsJson: String,
    val lastModifiedTime: String?,
    val isStructuredTable: Boolean,
    val fetchedAt: Long,
    val headerRowIndex: Int = 0,
    val separatorRowIndicesJson: String = "[]"
)
