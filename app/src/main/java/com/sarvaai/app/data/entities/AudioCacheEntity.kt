package com.sarvaai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "audio_cache")
data class AudioCacheEntity(
    @PrimaryKey val cacheKey: String, // hash(text + "_" + language + "_" + gender)
    val textHash: String,
    val language: String,
    val audioBase64: String,
    val createdAt: Long = System.currentTimeMillis()
)
