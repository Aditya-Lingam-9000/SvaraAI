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
    fun testNumberVerbalizerTamil() {
        val priceTa = NumberVerbalizer.verbalizePrice(280, "ta")
        assertEquals("இரண்டு நூறு எண்பது ரூபாய்", priceTa)

        val priceTaSingle = NumberVerbalizer.verbalizePrice(1, "ta")
        assertEquals("ஒன்று ரூபாய்", priceTaSingle)
    }

    @Test
    fun testNumberVerbalizerKannada() {
        val priceKn = NumberVerbalizer.verbalizePrice(280, "kn")
        assertEquals("ಎರಡು ನೂರು ಎಂಬತ್ತು ರೂಪಾಯಿ", priceKn)

        val priceKnSingle = NumberVerbalizer.verbalizePrice(1, "kn")
        assertEquals("ಒಂದು ರೂಪಾಯಿ", priceKnSingle)
    }

    @Test
    fun testNumberVerbalizerMalayalam() {
        val priceMl = NumberVerbalizer.verbalizePrice(280, "ml")
        assertEquals("രണ്ട് നൂറ് എൺപത് രൂപ", priceMl)

        val priceMlSingle = NumberVerbalizer.verbalizePrice(1, "ml")
        assertEquals("ഒന്ന് രൂപ", priceMlSingle)
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
