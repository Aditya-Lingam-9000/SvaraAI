package com.sarvaai.app.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.sarvaai.app.data.entities.OrderAuditEntity

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderAuditEntity)

    @Query("SELECT * FROM order_audits ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderAuditEntity>>

    @Query("SELECT * FROM order_audits WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: String): OrderAuditEntity?

    @Query("SELECT * FROM order_audits ORDER BY timestamp DESC LIMIT 1")
    fun getLatestOrder(): Flow<OrderAuditEntity?>
}
