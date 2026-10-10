package com.sarvaai.app

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.llm.GroqClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7VoiceCartTest {

    private val groqClient = GroqClient(context = null)

    @Test
    fun testVoiceCartAdditionIntents() {
        // English order with quantity
        val resEn = groqClient.generateLocalMultilingualResponse("2 Masala Dosa", "en")
        assertEquals("ORDER_ITEMS", resEn.intent)
        assertEquals("ADD_TO_CART", resEn.actions.first().actionType)
        assertEquals("Masala Dosa:2:", resEn.actions.first().payload)

        // Telugu order
        val resTe = groqClient.generateLocalMultilingualResponse("ఒక ఫిల్టర్ కాఫీ", "te")
        assertEquals("ORDER_ITEMS", resTe.intent)
        assertEquals("ADD_TO_CART", resTe.actions.first().actionType)
        assertTrue(resTe.spoken.contains("ఫిల్టర్ కాఫీ") || resTe.spoken.contains("చేర్చాను"))

        // Hindi order
        val resHi = groqClient.generateLocalMultilingualResponse("दो समोसा", "hi")
        assertEquals("ORDER_ITEMS", resHi.intent)
        assertEquals("ADD_TO_CART", resHi.actions.first().actionType)
        assertEquals("समोसा:2:", resHi.actions.first().payload)
    }

    @Test
    fun testVoiceCartCustomizationIntents() {
        val resCust = groqClient.generateLocalMultilingualResponse("Less spicy with extra butter", "en")
        assertEquals("CUSTOMIZATION", resCust.intent)
        assertEquals("ADD_TO_CART", resCust.actions.first().actionType)
        assertTrue(resCust.actions.first().payload?.contains("Less spicy") == true)
    }

    @Test
    fun testVoiceCartRemovalAndClearIntents() {
        // Remove item
        val resRem = groqClient.generateLocalMultilingualResponse("remove dosa", "en")
        assertEquals("REMOVE_ITEMS", resRem.intent)
        assertEquals("REMOVE_FROM_CART", resRem.actions.first().actionType)
        assertEquals("dosa", resRem.actions.first().payload)

        // Clear cart
        val resClear = groqClient.generateLocalMultilingualResponse("clear cart", "en")
        assertEquals("CLEAR_CART", resClear.intent)
        assertEquals("CLEAR_CART", resClear.actions.first().actionType)
    }

    @Test
    fun testVoiceCartBillSplitIntents() {
        // Split for 3 people in English
        val resSplitEn = groqClient.generateLocalMultilingualResponse("split for 3 people", "en")
        assertEquals("SPLIT_BILL", resSplitEn.intent)
        assertEquals("SPLIT_BILL", resSplitEn.actions.first().actionType)
        assertEquals("3", resSplitEn.actions.first().payload)

        // Split for 4 people in Telugu
        val resSplitTe = groqClient.generateLocalMultilingualResponse("4 మందికి విభజించండి", "te")
        assertEquals("SPLIT_BILL", resSplitTe.intent)
        assertEquals("4", resSplitTe.actions.first().payload)
    }

    @Test
    fun testVoiceCartOrderConfirmationIntent() {
        val resConf = groqClient.generateLocalMultilingualResponse("confirm order", "en")
        assertEquals("CONFIRM_ORDER", resConf.intent)
        assertEquals("CONFIRM_ORDER", resConf.actions.first().actionType)
    }

    @Test
    fun testFinancialBreakdownAndBillCalculations() {
        val cartItems = listOf(
            CartItemEntity(cartItemId = "c1", itemId = "i1", name = "Masala Dosa", unitPricePaise = 10000L, quantity = 2), // ₹200
            CartItemEntity(cartItemId = "c2", itemId = "i2", name = "Filter Coffee", unitPricePaise = 4000L, quantity = 1)   // ₹40
        )

        val subtotalPaise = cartItems.sumOf { it.totalPricePaise } // 24000 paise (₹240)
        assertEquals(24000L, subtotalPaise)

        val cgstPaise = (subtotalPaise * 25) / 1000 // 2.5% = 600 paise (₹6.00)
        val sgstPaise = (subtotalPaise * 25) / 1000 // 2.5% = 600 paise (₹6.00)
        val grandTotalPaise = subtotalPaise + cgstPaise + sgstPaise // 25200 paise (₹252)

        assertEquals(600L, cgstPaise)
        assertEquals(600L, sgstPaise)
        assertEquals(25200L, grandTotalPaise)

        val grandTotalRupees = grandTotalPaise / 100
        assertEquals(252L, grandTotalRupees)

        // Split for 3 people
        val splitCount = 3
        val perPersonRupees = grandTotalRupees / splitCount
        assertEquals(84L, perPersonRupees)

        // Verbalize per-person amount
        val verbalizedTe = NumberVerbalizer.verbalizePrice(perPersonRupees, "te")
        assertTrue(verbalizedTe.contains("ఎనభై నాలుగు") || verbalizedTe.contains("రూపాయలు"))
    }
}
