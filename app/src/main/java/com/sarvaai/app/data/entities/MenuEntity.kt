package com.sarvaai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "menus")
data class MenuEntity(
    @PrimaryKey val menuId: String,
    val restaurantName: String,
    val captureTimestamp: Long = System.currentTimeMillis(),
    val pageCount: Int = 1,
    val rawOcrSummary: String? = null
)
