package com.sarvaai.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "menu_items",
    foreignKeys = [
        ForeignKey(
            entity = MenuSectionEntity::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionId")]
)
data class MenuItemEntity(
    @PrimaryKey val itemId: String,
    val sectionId: String,
    val nameEnglish: String,
    val nameHindi: String? = null,
    val nameTelugu: String? = null,
    val transliteration: String? = null,
    val description: String? = null,
    val pricePaise: Long, // e.g. 25000L for ₹250.00
    val isVegetarian: Boolean,
    val isJain: Boolean = false,
    val isVegan: Boolean = false,
    val isNutFree: Boolean = true,
    val spiceLevel: Int = 1, // 0 = Mild, 1 = Medium, 2 = Spicy, 3 = Very Spicy
    val isChefSpecial: Boolean = false
) {
    val priceRupees: Long
        get() = pricePaise / 100
}
