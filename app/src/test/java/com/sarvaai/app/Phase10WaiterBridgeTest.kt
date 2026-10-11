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

    @Test
    fun testComplaintFeedbackBoardItems() {
        val items = com.sarvaai.app.service.waiter.ComplaintFeedbackBoard.ITEMS
        assertEquals(6, items.size)

        val coldFood = items.first { it.id == "cold_food" }
        assertEquals("Food is Cold / Reheat", coldFood.title)
        assertTrue(coldFood.getTextForLanguage("hi").contains("ठंडा") || coldFood.getTextForLanguage("hi").contains("गर्म"))
        assertTrue(coldFood.getTextForLanguage("te").contains("చల్లగా") || coldFood.getTextForLanguage("te").contains("వేడి"))

        val orderDelay = items.first { it.id == "delay_order" }
        assertEquals("Delay in Order / Follow Up", orderDelay.title)
        assertTrue(orderDelay.getTextForLanguage("hi").contains("देरी"))
        assertTrue(orderDelay.getTextForLanguage("te").contains("ఆలస్య"))

        val missingItem = items.first { it.id == "missing_item" }
        assertEquals("Missing Item from Order", missingItem.title)
        assertTrue(missingItem.getTextForLanguage("hi").contains("छूट") || missingItem.getTextForLanguage("hi").contains("आइटम"))
        assertTrue(missingItem.getTextForLanguage("te").contains("రాలేదు") || missingItem.getTextForLanguage("te").contains("ఐటమ్"))

        val tooSpicy = items.first { it.id == "too_spicy" }
        assertEquals("Too Spicy / Less Spice", tooSpicy.title)
        assertTrue(tooSpicy.getTextForLanguage("hi").contains("तीखा"))
        assertTrue(tooSpicy.getTextForLanguage("te").contains("కారం"))

        val cleanTable = items.first { it.id == "clean_table_cutlery" }
        assertEquals("Clean Table / Cutlery Request", cleanTable.title)
        assertTrue(cleanTable.getTextForLanguage("hi").contains("साफ") || cleanTable.getTextForLanguage("hi").contains("कटलरी"))
        assertTrue(cleanTable.getTextForLanguage("te").contains("శుభ్రం") || cleanTable.getTextForLanguage("te").contains("చెంచాలు"))

        val compliment = items.first { it.id == "compliment_chef" }
        assertEquals("Compliment Chef / Food is Delicious", compliment.title)
        assertTrue(compliment.isCompliment)
        assertTrue(compliment.getTextForLanguage("hi").contains("स्वादिष्ट") || compliment.getTextForLanguage("hi").contains("शेफ"))
        assertTrue(compliment.getTextForLanguage("te").contains("రుచి") || compliment.getTextForLanguage("te").contains("చెఫ్"))
    }

    @Test
    fun testComplaintFeedbackFallbackTranslation() {
        val controller = WaiterBridgeController(
            asrNmtClient = AsrNmtClient(),
            groqClient = GroqClient(null)
        )

        // Cold food translation
        val coldHi = controller.fallbackTranslate("The food is cold, please reheat", "en", "hi")
        assertTrue(coldHi.contains("ठंडा") || coldHi.contains("गर्म"))

        val coldTe = controller.fallbackTranslate("खाना ठंडा हो गया है", "hi", "te")
        assertTrue(coldTe.contains("చల్లగా") || coldTe.contains("వేడి"))

        // Delay translation
        val delayHi = controller.fallbackTranslate("There is delay in order", "en", "hi")
        assertTrue(delayHi.contains("देरी") || delayHi.contains("जल्दी"))

        val delayTe = controller.fallbackTranslate("ऑर्डर में देरी हो रही है", "hi", "te")
        assertTrue(delayTe.contains("ఆలస్యం") || delayTe.contains("త్వరగా"))

        // Compliment translation
        val complimentHi = controller.fallbackTranslate("The food is delicious, compliments to chef", "en", "hi")
        assertTrue(complimentHi.contains("स्वादिष्ट") || complimentHi.contains("धन्यवाद"))
    }
}
