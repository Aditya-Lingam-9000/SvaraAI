package com.sarvaai.app.service.bill

import com.sarvaai.app.data.entities.CartItemEntity

data class BillBreakdown(
    val subtotalPaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val serviceChargePaise: Long,
    val totalPaise: Long
) {
    val subtotalRupees: Long get() = subtotalPaise / 100
    val cgstRupees: Long get() = cgstPaise / 100
    val sgstRupees: Long get() = sgstPaise / 100
    val serviceChargeRupees: Long get() = serviceChargePaise / 100
    val totalRupees: Long get() = totalPaise / 100
}

data class DinerShare(
    val participantIndex: Int,
    val label: String,
    val sharePaise: Long,
    val assignedItems: List<CartItemEntity> = emptyList()
) {
    val shareRupees: Long get() = sharePaise / 100
}

object BillCalculator {

    /**
     * Exact deterministic tax calculation under Indian GST laws:
     * - CGST: 2.5% = 25 / 1000
     * - SGST: 2.5% = 25 / 1000
     * - Optional Service Charge: 5% = 50 / 1000
     */
    fun calculateBill(
        itemTotalPaise: Long,
        applyServiceCharge: Boolean = false
    ): BillBreakdown {
        val cgst = (itemTotalPaise * 25L) / 1000L
        val sgst = (itemTotalPaise * 25L) / 1000L
        val serviceCharge = if (applyServiceCharge) (itemTotalPaise * 50L) / 1000L else 0L
        val grandTotal = itemTotalPaise + cgst + sgst + serviceCharge

        return BillBreakdown(
            subtotalPaise = itemTotalPaise,
            cgstPaise = cgst,
            sgstPaise = sgst,
            serviceChargePaise = serviceCharge,
            totalPaise = grandTotal
        )
    }

    /**
     * Splits total bill equally among N participants without losing remainder paise.
     * Remainder paise are distributed fairly to the first N participants so the sum equals 100% of totalPaise.
     */
    fun splitEqually(totalPaise: Long, participantCount: Int): List<DinerShare> {
        val count = participantCount.coerceAtLeast(1)
        val baseShare = totalPaise / count
        val remainder = totalPaise % count

        return List(count) { index ->
            val exactShare = if (index < remainder) baseShare + 1L else baseShare
            DinerShare(
                participantIndex = index + 1,
                label = "Person ${index + 1}",
                sharePaise = exactShare
            )
        }
    }

    /**
     * Item-by-item split calculation:
     * Calculates tax proportionally on assigned items for each participant.
     */
    fun splitByItemAssignment(
        cartItems: List<CartItemEntity>,
        assignments: Map<Int, List<String>>, // participantIndex -> list of cartItemIds
        participantCount: Int,
        applyServiceCharge: Boolean = false
    ): List<DinerShare> {
        val count = participantCount.coerceAtLeast(1)
        val itemsMap = cartItems.associateBy { it.cartItemId }

        return (1..count).map { personIdx ->
            val assignedIds = assignments[personIdx] ?: emptyList()
            val assigned = assignedIds.mapNotNull { itemsMap[it] }
            val personSubtotal = assigned.sumOf { it.totalPricePaise }
            val breakdown = calculateBill(personSubtotal, applyServiceCharge)

            DinerShare(
                participantIndex = personIdx,
                label = "Person $personIdx",
                sharePaise = breakdown.totalPaise,
                assignedItems = assigned
            )
        }
    }
}
