package com.sarvaai.app

import com.sarvaai.app.service.wakeword.WakeWordDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectorTest {

    @Test
    fun testEnglishWakeWords() {
        assertTrue(WakeWordDetector.isWakeWord("Hey Svara"))
        assertTrue(WakeWordDetector.isWakeWord("hey svara"))
        assertTrue(WakeWordDetector.isWakeWord("Svara"))
        assertTrue(WakeWordDetector.isWakeWord("svara"))
        assertTrue(WakeWordDetector.isWakeWord("Hey Swara"))
        assertTrue(WakeWordDetector.isWakeWord("Swara"))
        assertTrue(WakeWordDetector.isWakeWord("Hello Svara"))
        assertTrue(WakeWordDetector.isWakeWord("Hello Swara"))
        assertTrue(WakeWordDetector.isWakeWord("Hi Svara"))
        assertTrue(WakeWordDetector.isWakeWord("OK Svara"))
        assertTrue(WakeWordDetector.isWakeWord("svara ai"))
    }

    @Test
    fun testTeluguWakeWords() {
        assertTrue(WakeWordDetector.isWakeWord("ఓయ్ స్వర"))
        assertTrue(WakeWordDetector.isWakeWord("హే స్వర"))
        assertTrue(WakeWordDetector.isWakeWord("స్వర"))
        assertTrue(WakeWordDetector.isWakeWord("హే సర్వ"))
        assertTrue(WakeWordDetector.isWakeWord("ఓయ్ సర్వ"))
    }

    @Test
    fun testHindiWakeWords() {
        assertTrue(WakeWordDetector.isWakeWord("हे स्वरा"))
        assertTrue(WakeWordDetector.isWakeWord("स्वरा"))
        assertTrue(WakeWordDetector.isWakeWord("नमस्ते स्वरा"))
        assertTrue(WakeWordDetector.isWakeWord("हे सर्व"))
        assertTrue(WakeWordDetector.isWakeWord("सर्व"))
    }

    @Test
    fun testFuzzyAndPhoneticVariations() {
        // Phonetic variations: "svaara", "shwara", "swra", "swarah"
        assertTrue(WakeWordDetector.isWakeWord("svaara"))
        assertTrue(WakeWordDetector.isWakeWord("heyy swara"))
        assertTrue(WakeWordDetector.isWakeWord("hey shvara"))
        assertTrue(WakeWordDetector.isWakeWord("hello swarah"))

        // Wake word inside a conversational sentence
        assertTrue(WakeWordDetector.isWakeWord("Hey Svara please show me the menu"))
        assertTrue(WakeWordDetector.isWakeWord("Can you help me Svara"))
        assertTrue(WakeWordDetector.isWakeWord("హే స్వర మెనూ చూపించు"))
        assertTrue(WakeWordDetector.isWakeWord("हे स्वरा मुझे डोसा चाहिए"))
    }

    @Test
    fun testEnglishCloseCommands() {
        assertTrue(WakeWordDetector.isCloseCommand("close app"))
        assertTrue(WakeWordDetector.isCloseCommand("close the app"))
        assertTrue(WakeWordDetector.isCloseCommand("close application"))
        assertTrue(WakeWordDetector.isCloseCommand("exit app"))
        assertTrue(WakeWordDetector.isCloseCommand("exit the application"))
        assertTrue(WakeWordDetector.isCloseCommand("svara close"))
        assertTrue(WakeWordDetector.isCloseCommand("hey svara close"))
        assertTrue(WakeWordDetector.isCloseCommand("close svara"))
        assertTrue(WakeWordDetector.isCloseCommand("quit app"))
        assertTrue(WakeWordDetector.isCloseCommand("shutdown app"))
    }

    @Test
    fun testTeluguCloseCommands() {
        assertTrue(WakeWordDetector.isCloseCommand("యాప్ మూసివేయి"))
        assertTrue(WakeWordDetector.isCloseCommand("యాప్ క్లోజ్ చేయి"))
        assertTrue(WakeWordDetector.isCloseCommand("మూసివేయి"))
        assertTrue(WakeWordDetector.isCloseCommand("క్లోజ్ చేయి"))
        assertTrue(WakeWordDetector.isCloseCommand("యాప్ ఆపు"))
        assertTrue(WakeWordDetector.isCloseCommand("యాప్ బంద్ చేయి"))
    }

    @Test
    fun testHindiCloseCommands() {
        assertTrue(WakeWordDetector.isCloseCommand("ऐप बंद करो"))
        assertTrue(WakeWordDetector.isCloseCommand("बंद करो"))
        assertTrue(WakeWordDetector.isCloseCommand("ऐप बंद कीजिए"))
        assertTrue(WakeWordDetector.isCloseCommand("बंद कर दो"))
        assertTrue(WakeWordDetector.isCloseCommand("बाहर निकलो"))
        assertTrue(WakeWordDetector.isCloseCommand("बाय स्वरा"))
    }

    @Test
    fun testNegativeCases() {
        assertFalse(WakeWordDetector.isWakeWord("I want two masala dosas"))
        assertFalse(WakeWordDetector.isWakeWord("Bring filter coffee"))
        assertFalse(WakeWordDetector.isWakeWord("What is the total price?"))
        assertFalse(WakeWordDetector.isWakeWord(""))
        assertFalse(WakeWordDetector.isWakeWord("   "))

        assertFalse(WakeWordDetector.isCloseCommand("Add more butter"))
        assertFalse(WakeWordDetector.isCloseCommand("Show items in menu"))
        assertFalse(WakeWordDetector.isCloseCommand("Hello how are you"))

        // Ambiguous single words MUST NOT trigger close command
        assertFalse(WakeWordDetector.isCloseCommand("exit"))
        assertFalse(WakeWordDetector.isCloseCommand("close"))
        assertFalse(WakeWordDetector.isCloseCommand("quit"))
        assertFalse(WakeWordDetector.isCloseCommand("shutdown"))
        assertFalse(WakeWordDetector.isCloseCommand("bye"))
        assertFalse(WakeWordDetector.isCloseCommand("goodbye"))
        assertFalse(WakeWordDetector.isCloseCommand("ఆపు"))
        assertFalse(WakeWordDetector.isCloseCommand("సెలవు"))
        assertFalse(WakeWordDetector.isCloseCommand("अलविदा"))
        assertFalse(WakeWordDetector.isCloseCommand("बाय"))
        assertFalse(WakeWordDetector.isCloseCommand("एग्जिट"))
    }
}
