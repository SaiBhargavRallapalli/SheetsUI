package com.rsb.sheetsui.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rsb.sheetsui.data.local.entity.SheetCacheEntity

@Dao
interface SheetCacheDao {

    @Query("SELECT * FROM sheet_cache WHERE cacheKey = :key")
    suspend fun getByKey(key: String): SheetCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: SheetCacheEntity)

    @Query("DELETE FROM sheet_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sheet_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
