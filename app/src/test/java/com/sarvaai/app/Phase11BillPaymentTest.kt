package com.sarvaai.app

import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.service.bill.BillCalculator
import com.sarvaai.app.service.bill.BillSpeechNarrator
import com.sarvaai.app.service.upi.UpiIntentGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase11BillPaymentTest {

    @Test
    fun testIndianGstCalculationsExactness() {
        // ₹1000 item total = 100,000 paise
        val breakdownNoService = BillCalculator.calculateBill(100000L, applyServiceCharge = false)
        assertEquals(100000L, breakdownNoService.subtotalPaise)
        assertEquals(2500L, breakdownNoService.cgstPaise) // 2.5% = ₹25.00
        assertEquals(2500L, breakdownNoService.sgstPaise) // 2.5% = ₹25.00
        assertEquals(0L, breakdownNoService.serviceChargePaise)
        assertEquals(105000L, breakdownNoService.totalPaise) // ₹1050.00

        assertEquals(1000L, breakdownNoService.subtotalRupees)
        assertEquals(25L, breakdownNoService.cgstRupees)
        assertEquals(25L, breakdownNoService.sgstRupees)
        assertEquals(1050L, breakdownNoService.totalRupees)

        // With 5% service charge
        val breakdownWithService = BillCalculator.calculateBill(100000L, applyServiceCharge = true)
        assertEquals(5000L, breakdownWithService.serviceChargePaise) // 5% = ₹50.00
        assertEquals(110000L, breakdownWithService.totalPaise) // ₹1100.00
    }

    @Test
    fun testEqualSplittingRemainderDistribution() {
        // ₹100 = 10,000 paise split 3 ways
        val totalPaise = 10000L
        val shares3 = BillCalculator.splitEqually(totalPaise, 3)
        assertEquals(3, shares3.size)
        assertEquals(3334L, shares3[0].sharePaise) // Gets 1 remainder paisa
        assertEquals(3333L, shares3[1].sharePaise)
        assertEquals(3333L, shares3[2].sharePaise)
        assertEquals(totalPaise, shares3.sumOf { it.sharePaise }) // 100% exact sum

        // ₹250 = 25,000 paise split 7 ways
        val totalPaise7 = 25000L
        val shares7 = BillCalculator.splitEqually(totalPaise7, 7)
        assertEquals(7, shares7.size)
        assertEquals(totalPaise7, shares7.sumOf { it.sharePaise }) // 100% exact sum
    }

    @Test
    fun testItemByItemSplitCalculation() {
        val cartItems = listOf(
            CartItemEntity(cartItemId = "item_1", itemId = "i1", name = "Masala Dosa", unitPricePaise = 10000L, quantity = 1), // ₹100
            CartItemEntity(cartItemId = "item_2", itemId = "i2", name = "Filter Coffee", unitPricePaise = 4000L, quantity = 1)   // ₹40
        )

        // Person 1 ate Dosa, Person 2 drank Coffee
        val assignments = mapOf(
            1 to listOf("item_1"),
            2 to listOf("item_2")
        )

        val itemShares = BillCalculator.splitByItemAssignment(cartItems, assignments, 2, applyServiceCharge = false)
        assertEquals(2, itemShares.size)

        // Person 1: ₹100 + 5% GST = ₹105 (10500 paise)
        assertEquals(10500L, itemShares[0].sharePaise)
        assertEquals(105L, itemShares[0].shareRupees)
        assertEquals("Masala Dosa", itemShares[0].assignedItems.first().name)

        // Person 2: ₹40 + 5% GST = ₹42 (4200 paise)
        assertEquals(4200L, itemShares[1].sharePaise)
        assertEquals(42L, itemShares[1].shareRupees)
        assertEquals("Filter Coffee", itemShares[1].assignedItems.first().name)
    }

    @Test
    fun testSpokenBillBreakdownNarrations() {
        // Test Equal Split Spoken String in English, Telugu, Hindi
        val speechEn = BillSpeechNarrator.narrateEqualSplit(1050L, 350L, 3, "en")
        assertTrue(speechEn.contains("1050") || speechEn.contains("thousand") || speechEn.contains("rupees"))
        assertTrue(speechEn.contains("350") || speechEn.contains("hundred"))

        val speechTe = BillSpeechNarrator.narrateEqualSplit(1050L, 350L, 3, "te")
        assertTrue(speechTe.contains("మొత్తం బిల్లు") && speechTe.contains("వాటా"))

        val speechHi = BillSpeechNarrator.narrateEqualSplit(1050L, 350L, 3, "hi")
        assertTrue(speechHi.contains("कुल बिल") && speechHi.contains("हिस्सा"))
    }

    @Test
    fun testUpiUriAndIntentGeneration() {
        val uriString = UpiIntentGenerator.buildUpiUriString(
            payeeVpa = "sarvaai@okhdfcbank",
            payeeName = "Sarva Bhavan",
            amountRupees = 350.00,
            transactionNote = "Table 4 Bill Share"
        )

        assertTrue(uriString.startsWith("upi://pay?"))
        assertTrue(uriString.contains("pa=sarvaai@okhdfcbank"))
        assertTrue(uriString.contains("pn=Sarva+Bhavan") || uriString.contains("pn=Sarva%20Bhavan"))
        assertTrue(uriString.contains("am=350.00"))
        assertTrue(uriString.contains("cu=INR"))
        assertTrue(uriString.contains("tn=Table+4+Bill+Share") || uriString.contains("tn=Table%204%20Bill%20Share"))
    }
}
