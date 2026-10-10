package com.sarvaai.app

import com.sarvaai.app.menu.analyzer.CoachingCue
import com.sarvaai.app.menu.coaching.AuditoryCoachingEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5CoachingTest {

    @Test
    fun testAuditoryCoachingPromptsTelugu() {
        val cueUp = AuditoryCoachingEngine.getSpokenPrompt(CoachingCue.MOVE_UP, "te")
        assertTrue(cueUp.contains("పైకి"))

        val cuePerfect = AuditoryCoachingEngine.getSpokenPrompt(CoachingCue.PERFECT_ALIGNMENT, "te")
        assertTrue(cuePerfect.contains("స్థిరంగా") || cuePerfect.contains("కనిపిస్తోంది"))
    }

    @Test
    fun testAuditoryCoachingPromptsHindi() {
        val cueDown = AuditoryCoachingEngine.getSpokenPrompt(CoachingCue.MOVE_DOWN, "hi")
        assertTrue(cueDown.contains("नीचे"))

        val cueDark = AuditoryCoachingEngine.getSpokenPrompt(CoachingCue.TOO_DARK, "hi")
        assertTrue(cueDark.contains("रोशनी") || cueDark.contains("अंधेरा"))
    }

    @Test
    fun testAuditoryCoachingPromptsEnglish() {
        val cueLeft = AuditoryCoachingEngine.getSpokenPrompt(CoachingCue.MOVE_LEFT, "en")
        assertTrue(cueLeft.contains("left", ignoreCase = true))

        val cuePerfect = AuditoryCoachingEngine.getSpokenPrompt(CoachingCue.PERFECT_ALIGNMENT, "en")
        assertTrue(cuePerfect.contains("steady", ignoreCase = true) || cuePerfect.contains("framed", ignoreCase = true))
    }
}
