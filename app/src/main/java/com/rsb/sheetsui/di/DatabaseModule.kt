package com.rsb.sheetsui.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rsb.sheetsui.data.local.AppDatabase
import com.rsb.sheetsui.data.local.dao.ColumnOverrideDao
import com.rsb.sheetsui.data.local.dao.PendingActionDao
import com.rsb.sheetsui.data.local.dao.SheetCacheDao
import com.rsb.sheetsui.data.local.dao.SpreadsheetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                actionType TEXT NOT NULL,
                spreadsheetId TEXT NOT NULL,
                sheetName TEXT NOT NULL,
                rowIndex INTEGER,
                rowData TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0,
                lastError TEXT
            )
        """.trimIndent())
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS column_overrides (
                spreadsheetId TEXT NOT NULL,
                columnIndex INTEGER NOT NULL,
                fieldType TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY (spreadsheetId, columnIndex)
            )
        """.trimIndent())
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sheet_cache (
                cacheKey TEXT PRIMARY KEY NOT NULL,
                dataHash TEXT NOT NULL,
                headersJson TEXT NOT NULL,
                rowsJson TEXT NOT NULL,
                formulaRowsJson TEXT NOT NULL,
                mergeRangesJson TEXT NOT NULL,
                columnValidationsJson TEXT NOT NULL,
                lastModifiedTime TEXT,
                isStructuredTable INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sheet_cache ADD COLUMN headerRowIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sheet_cache ADD COLUMN separatorRowIndicesJson TEXT NOT NULL DEFAULT '[]'")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "sheetsui.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSpreadsheetDao(db: AppDatabase): SpreadsheetDao = db.spreadsheetDao()

    @Provides
    fun providePendingActionDao(db: AppDatabase): PendingActionDao = db.pendingActionDao()

    @Provides
    fun provideColumnOverrideDao(db: AppDatabase): ColumnOverrideDao = db.columnOverrideDao()

    @Provides
    fun provideSheetCacheDao(db: AppDatabase): SheetCacheDao = db.sheetCacheDao()
}
