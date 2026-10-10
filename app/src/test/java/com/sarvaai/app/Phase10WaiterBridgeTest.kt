package com.sarvaai.app

import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.service.waiter.PhraseBoard
import com.sarvaai.app.service.waiter.WaiterBridgeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase10WaiterBridgeTest {

    @Test
    fun testPhraseBoardCategoriesAndLanguageRetrieval() {
        val disabilityPhrases = PhraseBoard.getPhrasesByCategory("DISABILITY")
        assertTrue(disabilityPhrases.isNotEmpty())
        val deafNotice = disabilityPhrases.first { it.id == "deaf_notice" }

        // Test multi-lingual text retrieval
        assertEquals("I have hearing loss. Please point, gesture or write.", deafNotice.getTextForLanguage("en"))
        assertTrue(deafNotice.getTextForLanguage("hi").contains("सुनने में कठिनाई") || deafNotice.getTextForLanguage("hi").contains("दिखाकर बताएं"))
        assertTrue(deafNotice.getTextForLanguage("te").contains("వినికిడి లోపం") || deafNotice.getTextForLanguage("te").contains("చూపించి"))

        // Test essential phrases
        val essentials = PhraseBoard.getPhrasesByCategory("ESSENTIALS")
        val billPhrase = essentials.first { it.id == "bill_please" }
        assertTrue(billPhrase.getTextForLanguage("hi").contains("बिल"))
        assertTrue(billPhrase.getTextForLanguage("te").contains("బిల్లు"))

        // Test food preference phrases
        val foodPrefs = PhraseBoard.getPhrasesByCategory("FOOD_PREF")
        val spicyPhrase = foodPrefs.first { it.id == "less_spicy" }
        assertTrue(spicyPhrase.getTextForLanguage("hi").contains("कम तीखा"))
        assertTrue(spicyPhrase.getTextForLanguage("te").contains("కారం తక్కువగా"))
    }

    @Test
    fun testWaiterBridgeControllerPhraseSelectionAndState() {
        val controller = WaiterBridgeController(
            asrNmtClient = AsrNmtClient(),
            groqClient = GroqClient(null)
        )

        // Set Diner = Telugu ('te'), Staff = Hindi ('hi')
        controller.setDinerLanguage("te")
        controller.setStaffLanguage("hi")
        assertEquals("te", controller.state.value.dinerLanguage)
        assertEquals("hi", controller.state.value.staffLanguage)

        // Select "Water Please"
        val waterPhrase = PhraseBoard.DINING_PHRASES.first { it.id == "water_please" }
        var spokenTextSent = ""
        var spokenLangSent = ""

        controller.selectPhrase(waterPhrase) { text, lang ->
            spokenTextSent = text
            spokenLangSent = lang
        }

        // Staff card must show Hindi
        assertEquals(waterPhrase.hindiText, controller.state.value.activeStaffCardText)
        assertEquals("hi", spokenLangSent)
        assertEquals(waterPhrase.hindiText, spokenTextSent)

        // Diner caption must show Telugu
        assertEquals(waterPhrase.teluguText, controller.state.value.liveDinerCaption)
    }

    @Test
    fun testWaiterBridgeFlipToggle() {
        val controller = WaiterBridgeController(
            asrNmtClient = AsrNmtClient(),
            groqClient = GroqClient(null)
        )

        assertEquals(false, controller.state.value.isFlippedForStaff)
        controller.toggleFlipForStaff()
        assertEquals(true, controller.state.value.isFlippedForStaff)
        controller.toggleFlipForStaff()
        assertEquals(false, controller.state.value.isFlippedForStaff)
    }

    @Test
    fun testWaiterBridgeFallbackTranslation() {
        val controller = WaiterBridgeController(
            asrNmtClient = AsrNmtClient(),
            groqClient = GroqClient(null)
        )

        // Translate waiter response in Hindi ("दो मिनट में ला रहा हूँ") -> Telugu
        val teResult = controller.fallbackTranslate("दो मिनट में ला रहा हूँ", "hi", "te")
        assertTrue(teResult.contains("నిమిషాల్లో") || teResult.contains("తీసుకువస్తాను"))

        // Translate diner request in Telugu ("దయచేసి మంచి నీరు తీసుకురండి") -> Hindi
        val hiResult = controller.fallbackTranslate("దయచేసి మంచి నీరు తీసుకురండి", "te", "hi")
        assertTrue(hiResult.contains("पानी") || hiResult.contains("पीने का पानी"))
    }
}
