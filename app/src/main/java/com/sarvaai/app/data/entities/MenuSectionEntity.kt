package com.sarvaai.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "menu_sections",
    foreignKeys = [
        ForeignKey(
            entity = MenuEntity::class,
            parentColumns = ["menuId"],
            childColumns = ["menuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("menuId")]
)
data class MenuSectionEntity(
    @PrimaryKey val sectionId: String,
    val menuId: String,
    val titleEnglish: String,
    val titleNative: String? = null,
    val displayOrder: Int = 0
)
