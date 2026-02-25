package com.rsb.sheetsui.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rsb.sheetsui.data.local.dao.ColumnOverrideDao
import com.rsb.sheetsui.data.local.dao.PendingActionDao
import com.rsb.sheetsui.data.local.dao.SheetCacheDao
import com.rsb.sheetsui.data.local.dao.SpreadsheetDao
import com.rsb.sheetsui.data.local.entity.CachedSpreadsheetEntity
import com.rsb.sheetsui.data.local.entity.ColumnOverrideEntity
import com.rsb.sheetsui.data.local.entity.PendingActionEntity
import com.rsb.sheetsui.data.local.entity.SheetCacheEntity

@Database(
    entities = [
        CachedSpreadsheetEntity::class,
        PendingActionEntity::class,
        ColumnOverrideEntity::class,
        SheetCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spreadsheetDao(): SpreadsheetDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun columnOverrideDao(): ColumnOverrideDao
    abstract fun sheetCacheDao(): SheetCacheDao
}
