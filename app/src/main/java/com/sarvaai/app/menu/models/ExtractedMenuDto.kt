package com.sarvaai.app.menu.models

import kotlinx.serialization.Serializable

@Serializable
data class ExtractedMenuDto(
    val restaurantName: String = "Restaurant Menu",
    val sections: List<ExtractedSectionDto> = emptyList()
)

@Serializable
data class ExtractedSectionDto(
    val sectionName: String,
    val items: List<ExtractedItemDto> = emptyList()
)

@Serializable
data class ExtractedItemDto(
    val name: String,
    val price: Long = 0L, // Price in whole rupees
    val isVegetarian: Boolean = true,
    val isJain: Boolean = false,
    val isVegan: Boolean = false,
    val isNutFree: Boolean = true,
    val spiceLevel: Int = 1, // 0 = Mild, 1 = Medium, 2 = Spicy, 3 = Very Spicy
    val description: String? = null,
    val isChefSpecial: Boolean = false
)
