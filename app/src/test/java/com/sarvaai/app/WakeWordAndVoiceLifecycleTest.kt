package com.sarvaai.app

import com.sarvaai.app.service.wakeword.WakeWordDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WakeWordDetector:
 * - Multilingual wake word detection ('Hey Svara', 'హే స్వరా', 'हे स्वरा')
 * - Voice shutdown and close commands across English, Telugu, Hindi
 * - False positive rejection for normal conversation and restaurant ordering sentences
 */
class WakeWordAndVoiceLifecycleTest {

    @Test
    fun testEnglishWakeWords() {
        val variations = listOf(
            "Hey Svara",
            "hey svara",
            "HEY SVARA",
            "Hey Swara",
            "hey swara",
            "svara",
            "swara",
            "Svara",
            "Swara",
            "Ok Svara",
            "ok swara",
            "Hi Svara",
            "hi swara",
            "Hello Svara",
            "Svara AI",
            "Hey Svara, what is on the menu?",
            "Hey swara, order two masala dosas please!",
            "svara can you help me?"
        )

        for (phrase in variations) {
            assertTrue("Expected wake word detected for: '$phrase'", WakeWordDetector.isWakeWord(phrase))
        }
    }

    @Test
    fun testTeluguWakeWords() {
        val variations = listOf(
            "ఓయ్ స్వర",
            "ఓయ్ స్వరా",
            "హే స్వరా",
            "హే స్వర",
            "స్వర",
            "స్వరా",
            "నమస్కారం స్వర",
            "నమస్కారం స్వరా",
            "స్వరా విను",
            "హే స్వరా, ఈరోజు స్పెషల్స్ ఏంటి?",
            "ఓయ్ స్వర నాకు ఇడ్లీ కావాలి"
        )

        for (phrase in variations) {
            assertTrue("Expected Telugu wake word detected for: '$phrase'", WakeWordDetector.isWakeWord(phrase))
        }
    }

    @Test
    fun testHindiWakeWords() {
        val variations = listOf(
            "हे स्वरा",
            "हे स्वर",
            "स्वरा",
            "ओए स्वरा",
            "अरे स्वरा",
            "सुनो स्वरा",
            "स्वरा सुनो",
            "नमस्ते स्वरा",
            "हे स्वरा, आज मेनू में क्या है?",
            "स्वरा मुझे दो डोसा चाहिए"
        )

        for (phrase in variations) {
            assertTrue("Expected Hindi wake word detected for: '$phrase'", WakeWordDetector.isWakeWord(phrase))
        }
    }

    @Test
    fun testEnglishCloseCommands() {
        val variations = listOf(
            "close app",
            "CLOSE APP",
            "close the app",
            "close application",
            "exit application",
            "exit app",
            "exit",
            "close",
            "quit",
            "quit app",
            "shut down",
            "shutdown",
            "shut down app",
            "goodbye",
            "bye svara",
            "goodbye svara",
            "svara close app",
            "please close app",
            "hey svara exit application"
        )

        for (cmd in variations) {
            assertTrue("Expected English close command detected for: '$cmd'", WakeWordDetector.isCloseCommand(cmd))
        }
    }

    @Test
    fun testTeluguCloseCommands() {
        val variations = listOf(
            "యాప్ మూసివేయి",
            "యాప్ మూసేయి",
            "యాప్ క్లోజ్ చేయి",
            "క్లోజ్ చేయి",
            "మూసివేయి",
            "మూసేయి",
            "నిష్క్రమించు",
            "యాప్ నిష్క్రమించు",
            "యాప్ నుండి నిష్క్రమించు",
            "సెలవు",
            "యాప్ ఆపు",
            "యాప్ బంద్ చేయి",
            "దయచేసి యాప్ మూసివేయి",
            "స్వరా యాప్ క్లోజ్ చేయి"
        )

        for (cmd in variations) {
            assertTrue("Expected Telugu close command detected for: '$cmd'", WakeWordDetector.isCloseCommand(cmd))
        }
    }

    @Test
    fun testHindiCloseCommands() {
        val variations = listOf(
            "ऐप बंद करो",
            "ऐप बंद कीजिए",
            "ऐप बंद करें",
            "बंद करो",
            "बंद कीजिए",
            "बंद करें",
            "बाहर निकलो",
            "ऐप से बाहर निकलो",
            "अलविदा",
            "बाय",
            "बाय स्वरा",
            "एप्लिकेशन बंद करो",
            "एप्लिकेशन बंद कीजिए",
            "कृपया ऐप बंद करो",
            "स्वरा ऐप बंद करो"
        )

        for (cmd in variations) {
            assertTrue("Expected Hindi close command detected for: '$cmd'", WakeWordDetector.isCloseCommand(cmd))
        }
    }

    @Test
    fun testNonWakeSentencesAreNotFalselyDetected() {
        val regularSentences = listOf(
            "I want to order two masala dosas",
            "How much is the total bill?",
            "Can you show me the vegetarian items?",
            "Please bring extra sambar and chutney",
            "Is the filter coffee hot and strong?",
            "Where is the restroom located?",
            "Thank you very much for the service",
            "Could you split the bill equally between three people?",
            "రెండు మసాలా దోశలు మరియు ఒక కాఫీ ఇవ్వండి",
            "మొత్తం బిల్లు ఎంత అయ్యింది?",
            "ఇక్కడ శాఖాహార భోజనం దొరుకుతుందా?",
            "నాకు కొంచెం మంచి నీళ్ళు కావాలి",
            "ధన్యవాదాలు",
            "मुझे दो प्लेट इडली और एक चाय चाहिए",
            "कुल बिल कितना हुआ?",
            "क्या यहाँ शुद्ध शाकाहारी खाना मिलता है?",
            "कृपया थोड़ा पानी दीजिए",
            "बहुत बहुत धन्यवाद"
        )

        for (sentence in regularSentences) {
            assertFalse(
                "Sentence should NOT be detected as wake word: '$sentence'",
                WakeWordDetector.isWakeWord(sentence)
            )
            assertFalse(
                "Sentence should NOT be detected as close command: '$sentence'",
                WakeWordDetector.isCloseCommand(sentence)
            )
        }
    }

    @Test
    fun testPromptMessages() {
        // Listening prompts
        assertTrue(WakeWordDetector.getListeningMessage("en").contains("listening", ignoreCase = true))
        assertTrue(WakeWordDetector.getListeningMessage("te").contains("వింటున్నాను"))
        assertTrue(WakeWordDetector.getListeningMessage("hi").contains("सुन रहा हूँ"))

        // Goodbye messages
        assertTrue(WakeWordDetector.getGoodbyeMessage("en").contains("Goodbye", ignoreCase = true))
        assertTrue(WakeWordDetector.getGoodbyeMessage("te").contains("సెలవు"))
        assertTrue(WakeWordDetector.getGoodbyeMessage("hi").contains("अलविदा"))
    }

    @Test
    fun testConstantsAndNormalization() {
        assertEquals("com.sarvaai.app.ACTION_CLOSE_APP", WakeWordDetector.ACTION_CLOSE_APP)
        assertEquals("EXTRA_AUTO_START_LISTENING", WakeWordDetector.EXTRA_AUTO_START_LISTENING)

        assertEquals("hey svara", WakeWordDetector.normalize("  Hey, Svara!  "))
        assertEquals("ऐप बंद करो", WakeWordDetector.normalize("ऐप बंद करो।"))
        assertEquals("యాప్ మూసివేయి", WakeWordDetector.normalize("యాప్, మూసివేయి!"))
    }
}
