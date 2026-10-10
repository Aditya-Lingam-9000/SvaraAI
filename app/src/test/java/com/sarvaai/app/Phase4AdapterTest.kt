package com.sarvaai.app

import com.sarvaai.app.adapt.OutputAdapter
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.llm.models.StructuredAiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4AdapterTest {

    @Test
    fun testNumberVerbalizerTelugu() {
        val priceTe = NumberVerbalizer.verbalizePrice(460, "te")
        assertEquals("నాలుగు వందల అరవై రూపాయలు", priceTe)

        val priceTeSingle = NumberVerbalizer.verbalizePrice(1, "te")
        assertEquals("ఒకటి రూపాయలు", priceTeSingle)
    }

    @Test
    fun testNumberVerbalizerHindi() {
        val priceHi = NumberVerbalizer.verbalizePrice(280, "hi")
        assertEquals("दो सौ अस्सी रुपये", priceHi)
    }

    @Test
    fun testNumberVerbalizerEnglish() {
        val priceEn = NumberVerbalizer.verbalizePrice(320, "en")
        assertEquals("three hundred and twenty rupees", priceEn)
    }

    @Test
    fun testOutputAdapterSanitizesMarkdownAndEmojis() {
        val rawResponse = StructuredAiResponse(
            intent = "COMPARE_DISHES",
            headline = "**Kadai Paneer vs Butter Masala**",
            spoken = "Kadai Paneer costs ₹280 [🌶️ Hot]. Paneer Butter Masala costs ₹320 [🧈 Mild]. Total is ₹460.",
            caption = "Kadai Paneer ₹280 vs Butter Masala ₹320. Total ₹460.",
            bullets = listOf("* Kadai Paneer: ₹280", "* Paneer Butter Masala: ₹320")
        )

        val adapted = OutputAdapter.adapt(rawResponse, "en")
        assertFalse(adapted.spokenText.contains("🌶️"))
        assertFalse(adapted.spokenText.contains("🧈"))
        assertFalse(adapted.spokenText.contains("**"))
        assertFalse(adapted.spokenText.contains("*"))
        assertTrue(adapted.spokenText.contains("two hundred and eighty rupees"))
        assertTrue(adapted.spokenText.contains("three hundred and twenty rupees"))
        assertTrue(adapted.spokenText.contains("four hundred and sixty rupees"))
    }
}
