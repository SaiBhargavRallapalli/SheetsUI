package com.rsb.sheetsui.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rsb.sheetsui.data.local.entity.CachedSpreadsheetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpreadsheetDao {

    @Query("SELECT * FROM cached_spreadsheets ORDER BY modifiedTime DESC")
    fun getAllSpreadsheets(): Flow<List<CachedSpreadsheetEntity>>

    @Query("SELECT * FROM cached_spreadsheets ORDER BY modifiedTime DESC")
    suspend fun getAllCached(): List<CachedSpreadsheetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(spreadsheets: List<CachedSpreadsheetEntity>)

    @Delete
    suspend fun delete(spreadsheet: CachedSpreadsheetEntity)

    @Query("DELETE FROM cached_spreadsheets")
    suspend fun clearAll()

    @Query("DELETE FROM cached_spreadsheets WHERE id = :id")
    suspend fun deleteById(id: String)
}
