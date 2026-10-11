package com.sarvaai.app

import com.sarvaai.app.ui.screens.onboarding.ONBOARDING_CARDS
import com.sarvaai.app.ui.screens.onboarding.OnboardingPreferences
import org.junit.Assert.*
import org.junit.Test

class OnboardingScreenTest {

    @Test
    fun testOnboardingCardsCountAndIds() {
        assertEquals("There must be exactly 9 sliding cards", 9, ONBOARDING_CARDS.size)
        ONBOARDING_CARDS.forEachIndexed { index, card ->
            assertEquals("Card id must be sequential 1-based index", index + 1, card.id)
            assertTrue("Card title must not be blank", card.title.isNotBlank())
            assertTrue("Card useCase must not be blank", card.useCase.isNotBlank())
            assertTrue("Card howToUse must not be blank", card.howToUse.isNotBlank())
            assertTrue("Card bhashiniAiHighlight must not be blank", card.bhashiniAiHighlight.isNotBlank())
            assertTrue("Card keyCapabilities must not be empty", card.keyCapabilities.isNotEmpty())
            assertNotNull("Card sample voice prompt must not be null", card.sampleVoicePrompt)
        }
    }

    @Test
    fun testRequiredCardTopicsPresent() {
        val titles = ONBOARDING_CARDS.map { it.title }

        assertTrue("Card 1 Voice-First Restaurant Assistant present",
            titles.contains("Voice-First Restaurant Assistant"))
        assertTrue("Card 2 Bhashini Multi-Indic ASR & TTS present",
            titles.contains("Bhashini Multi-Indic ASR & TTS"))
        assertTrue("Card 3 CameraX Menu Vision Scanner present",
            titles.contains("CameraX Menu Vision Scanner"))
        assertTrue("Card 4 Smart Dish Comparison Matrix present",
            titles.contains("Smart Dish Comparison Matrix"))
        assertTrue("Card 5 Dietary & Allergen Safety Guard present",
            titles.contains("Dietary & Allergen Safety Guard"))
        assertTrue("Card 6 Voice Ordering Cart & Tax Engine present",
            titles.contains("Voice Ordering Cart & Tax Engine"))
        assertTrue("Card 7 Multilingual Waiter Bridge present",
            titles.contains("Multilingual Waiter Bridge"))
        assertTrue("Card 8 Instant Staff Feedback & Escalation present",
            titles.contains("Instant Staff Feedback & Escalation"))
        assertTrue("Card 9 Smart Bill Splitter present",
            titles.contains("Smart Bill Splitter"))
    }

    @Test
    fun testOnboardingPreferencesConstants() {
        assertEquals("svara_prefs", OnboardingPreferences.PREFS_NAME)
        assertEquals("KEY_ONBOARDING_COMPLETED", OnboardingPreferences.KEY_ONBOARDING_COMPLETED)
    }
}
