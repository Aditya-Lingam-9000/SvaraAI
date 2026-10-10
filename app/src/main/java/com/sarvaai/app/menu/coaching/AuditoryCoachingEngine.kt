package com.sarvaai.app.menu.coaching

import com.sarvaai.app.menu.analyzer.CoachingCue

object AuditoryCoachingEngine {

    fun getSpokenPrompt(cue: CoachingCue, language: String): String {
        return when (language.lowercase()) {
            "te" -> when (cue) {
                CoachingCue.PERFECT_ALIGNMENT -> "స్థిరంగా ఉంచండి, మెనూ బాగా కనిపిస్తోంది."
                CoachingCue.TOO_DARK -> "వెలుతురు తక్కువగా ఉంది, కాంతి వైపు జరపండి."
                CoachingCue.TOO_BRIGHT -> "మెరుపు ఎక్కువగా ఉంది, ఫోన్‌ను కొద్దిగా వంచండి."
                CoachingCue.BLURRY_HOLD_STEADY -> "ఫోన్‌ను కదల్చకుండా స్థిరంగా ఉంచండి."
                CoachingCue.MOVE_UP -> "పై భాగం కట్ అవుతోంది, ఫోన్‌ను పైకి జరపండి."
                CoachingCue.MOVE_DOWN -> "క్రింది భాగం కట్ అవుతోంది, ఫోన్‌ను క్రిందికి జరపండి."
                CoachingCue.MOVE_LEFT -> "ఫోన్‌ను ఎడమ వైపు జరపండి."
                CoachingCue.MOVE_RIGHT -> "ఫోన్‌ను కుడి వైపు జరపండి."
            }

            "hi" -> when (cue) {
                CoachingCue.PERFECT_ALIGNMENT -> "स्थिर रखें, मेनू साफ़ दिख रहा है।"
                CoachingCue.TOO_DARK -> "रोशनी कम है, फोन को प्रकाश की ओर ले जाएं।"
                CoachingCue.TOO_BRIGHT -> "चमक ज्यादा है, फोन को थोड़ा तिरछा करें।"
                CoachingCue.BLURRY_HOLD_STEADY -> "धुंधला है, फोन को स्थिर रखें।"
                CoachingCue.MOVE_UP -> "ऊपर का हिस्सा कट रहा है, फोन ऊपर करें।"
                CoachingCue.MOVE_DOWN -> "नीचे का हिस्सा कट रहा है, फोन नीचे करें।"
                CoachingCue.MOVE_LEFT -> "फोन बाईं ओर ले जाएं।"
                CoachingCue.MOVE_RIGHT -> "फोन दाईं ओर ले जाएं।"
            }

            else -> when (cue) {
                CoachingCue.PERFECT_ALIGNMENT -> "Hold steady, menu is framed well."
                CoachingCue.TOO_DARK -> "Lighting is too dim, move closer to light."
                CoachingCue.TOO_BRIGHT -> "Too much glare, tilt phone slightly."
                CoachingCue.BLURRY_HOLD_STEADY -> "Image is blurry, please hold steady."
                CoachingCue.MOVE_UP -> "Menu cut off at top, move phone up."
                CoachingCue.MOVE_DOWN -> "Menu cut off at bottom, move phone down."
                CoachingCue.MOVE_LEFT -> "Move phone left."
                CoachingCue.MOVE_RIGHT -> "Move phone right."
            }
        }
    }
}
