package com.rsb.sheetsui.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rsb.sheetsui.data.local.entity.ColumnOverrideEntity

@Dao
interface ColumnOverrideDao {

    @Query("SELECT * FROM column_overrides WHERE spreadsheetId = :spreadsheetId")
    suspend fun getOverridesForSpreadsheet(spreadsheetId: String): List<ColumnOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(override: ColumnOverrideEntity)

    @Query("DELETE FROM column_overrides WHERE spreadsheetId = :spreadsheetId AND columnIndex = :columnIndex")
    suspend fun delete(spreadsheetId: String, columnIndex: Int)
}
