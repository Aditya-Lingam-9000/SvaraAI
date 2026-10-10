package com.sarvaai.app.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.sarvaai.app.data.entities.CartItemEntity

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY addedTimestamp ASC")
    fun getCartItems(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items ORDER BY addedTimestamp ASC")
    suspend fun getCartItemsDirect(): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getCartItemByItemId(itemId: String): CartItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: CartItemEntity)

    @Query("UPDATE cart_items SET quantity = :quantity WHERE cartItemId = :cartItemId")
    suspend fun updateQuantity(cartItemId: String, quantity: Int)

    @Query("DELETE FROM cart_items WHERE cartItemId = :cartItemId")
    suspend fun deleteCartItem(cartItemId: String)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()
}
