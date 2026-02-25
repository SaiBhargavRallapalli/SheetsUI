package com.rsb.sheetsui.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rsb.sheetsui.data.local.entity.PendingActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingActionDao {

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    fun getAllPending(): Flow<List<PendingActionEntity>>

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAllPendingSync(): List<PendingActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: PendingActionEntity)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_actions SET retryCount = :retryCount, lastError = :lastError WHERE id = :id")
    suspend fun updateError(id: Long, retryCount: Int, lastError: String?)

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun getPendingCount(): Int
}
