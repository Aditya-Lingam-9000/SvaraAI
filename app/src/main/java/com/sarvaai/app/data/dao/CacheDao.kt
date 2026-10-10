package com.sarvaai.app.data.dao

import androidx.room.*
import com.sarvaai.app.data.entities.AudioCacheEntity

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioCache(cache: AudioCacheEntity)

    @Query("SELECT * FROM audio_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getAudioCache(cacheKey: String): AudioCacheEntity?

    @Query("DELETE FROM audio_cache WHERE createdAt < :olderThanEpochMs")
    suspend fun pruneOldCache(olderThanEpochMs: Long)
}
