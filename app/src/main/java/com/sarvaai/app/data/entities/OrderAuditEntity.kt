package com.sarvaai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "order_audits")
data class OrderAuditEntity(
    @PrimaryKey val orderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val itemsSummary: String, // JSON serialized items array
    val subtotalPaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val serviceChargePaise: Long,
    val totalPaise: Long,
    val status: String // "CONFIRMED", "SERVED", "BILLED", "CANCELLED"
) {
    val totalRupees: Long
        get() = totalPaise / 100
}
