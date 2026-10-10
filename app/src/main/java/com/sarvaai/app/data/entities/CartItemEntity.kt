package com.sarvaai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val cartItemId: String,
    val itemId: String,
    val name: String,
    val unitPricePaise: Long,
    val quantity: Int,
    val specialNotes: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val totalPricePaise: Long
        get() = unitPricePaise * quantity

    val unitPriceRupees: Long
        get() = unitPricePaise / 100

    val totalPriceRupees: Long
        get() = totalPricePaise / 100
}
