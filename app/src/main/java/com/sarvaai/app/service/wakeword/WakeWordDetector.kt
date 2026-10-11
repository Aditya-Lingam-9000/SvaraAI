package com.sarvaai.app.service.wakeword

import java.util.Locale

/**
 * Multilingual Wake-Word and Shutdown Command Engine for SvaraAI / SarvaAI.
 * Accurately recognizes "Hey Svara" / "Svara" and shutdown/exit commands across English,
 * Telugu, and Hindi using phonetic normalization, token matching, and fuzzy edit distance.
 */
object WakeWordDetector {

    const val ACTION_CLOSE_APP = "com.sarvaai.app.ACTION_CLOSE_APP"
    const val EXTRA_AUTO_START_LISTENING = "EXTRA_AUTO_START_LISTENING"

    // Canonical Wake Word Phrases
    private val WAKE_PHRASES_EN = listOf(
        "hey svara",
        "svara",
        "hey swara",
        "swara",
        "hello svara",
        "hello swara",
        "hi svara",
        "hi swara",
        "ok svara",
        "okay svara",
        "ok swara",
        "svara ai",
        "swara ai",
        "sarva",
        "hey sarva",
        "sarva ai",
        "svaara",
        "swarah",
        "shwara",
        "shvara",
        "heyy swara",
        "heyy svara",
        "hey shvara",
        "hello swarah"
    )

    private val WAKE_PHRASES_TE = listOf(
        "ఓయ్ స్వర",
        "ఓయ్ స్వరా",
        "హే స్వర",
        "హే స్వరా",
        "స్వర",
        "స్వరా",
        "హలో స్వర",
        "నమస్తే స్వర",
        "నమస్కారం స్వర",
        "నమస్కారం స్వరా",
        "స్వర విను",
        "స్వరా విను",
        "హే సర్వ",
        "హే సర్వా",
        "ఓయ్ సర్వ",
        "ఓయ్ సర్వా",
        "సర్వ",
        "సర్వా"
    )

    private val WAKE_PHRASES_HI = listOf(
        "हे स्वरा",
        "स्वरा",
        "नमस्ते स्वरा",
        "हैलो स्वरा",
        "हे स्वर",
        "स्वर",
        "ओए स्वरा",
        "अरे स्वरा",
        "सुनो स्वरा",
        "स्वरा सुनो",
        "हे सर्व",
        "सर्व",
        "हे सर्वा",
        "सर्वा"
    )

    val ALL_WAKE_WORDS: List<String> = (WAKE_PHRASES_EN + WAKE_PHRASES_TE + WAKE_PHRASES_HI).distinct()

    // Canonical Close / Shutdown Phrases
    private val CLOSE_PHRASES_EN = listOf(
        "close app",
        "close the app",
        "close application",
        "exit app",
        "exit application",
        "exit",
        "close",
        "quit",
        "quit app",
        "shutdown",
        "shut down",
        "shut down app",
        "stop app",
        "terminate app",
        "bye svara",
        "goodbye svara",
        "goodbye",
        "good bye",
        "svara close",
        "hey svara close",
        "close svara",
        "exit svara"
    )

    private val CLOSE_PHRASES_TE = listOf(
        "యాప్ మూసివేయి",
        "యాప్ మూసేయి",
        "యాప్ క్లోజ్ చేయి",
        "క్లోజ్ చేయి",
        "మూసివేయి",
        "మూసేయి",
        "యాప్ ఆపు",
        "ఆపు",
        "నిష్క్రమించు",
        "యాప్ నిష్క్రమించు",
        "యాప్ నుండి నిష్క్రమించు",
        "సెలవు",
        "యాప్ బంద్ చేయి",
        "బంద్ చేయి"
    )

    private val CLOSE_PHRASES_HI = listOf(
        "ऐप बंद करो",
        "ऐप बंद कीजिए",
        "ऐप बंद करें",
        "बंद करो",
        "बंद कीजिए",
        "बंद करें",
        "बाहर निकलो",
        "ऐप से बाहर निकलो",
        "अलविदा",
        "एग्जिट",
        "बाय",
        "बाय स्वरा",
        "एप्लिकेशन बंद करो",
        "एप्लिकेशन बंद कीजिए",
        "एप्लिकेशन बंद करें",
        "बंद कर दो",
        "ऐप बंद कर दो"
    )

    val ALL_CLOSE_COMMANDS: List<String> = (CLOSE_PHRASES_EN + CLOSE_PHRASES_TE + CLOSE_PHRASES_HI).distinct()

    /**
     * Normalizes text by removing punctuation, collapsing whitespace, and lowercasing.
     */
    fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        return text
            .lowercase(Locale.ROOT)
            .replace(Regex("[.,/#!$%^&*;:{}=\\-_`~()?\\[\\]\"'।॥]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Backward-compatible alias for normalizeText.
     */
    fun normalize(text: String): String = normalizeText(text)

    /**
     * Converts Latin characters to a simplified phonetic representation (e.g. 'w' -> 'v').
     */
    fun toPhoneticRepresentation(text: String): String {
        val norm = normalizeText(text)
        return norm
            .replace('w', 'v')
            .replace("ph", "f")
            .replace("sh", "s")
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("oo", "u")
            .replace(Regex("([a-z])\\1+"), "$1")
    }

    /**
     * Computes Levenshtein edit distance.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        var prev = IntArray(s2.length + 1) { it }
        var curr = IntArray(s2.length + 1)

        for (i in 1..s1.length) {
            curr[0] = i
            val c1 = s1[i - 1]
            for (j in 1..s2.length) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[s2.length]
    }

    /**
     * Checks if the given spoken text contains or matches any 'Hey Svara' wake phrase.
     */
    fun isWakeWord(text: String): Boolean {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return false

        val phonetic = toPhoneticRepresentation(normalized)

        // 1. Direct match with any canonical wake word
        for (wakeWord in ALL_WAKE_WORDS) {
            val normWake = normalizeText(wakeWord)
            if (normalized == normWake ||
                normalized.startsWith("$normWake ") ||
                normalized.endsWith(" $normWake") ||
                normalized.contains(" $normWake ")
            ) {
                return true
            }

            val phoneticWake = toPhoneticRepresentation(normWake)
            if (phonetic == phoneticWake ||
                phonetic.startsWith("$phoneticWake ") ||
                phonetic.endsWith(" $phoneticWake") ||
                phonetic.contains(" $phoneticWake ")
            ) {
                return true
            }
        }

        // 2. Token-level single word check ("svara", "swara", "sarva", "స్వర", "స్వరా", "సర్వ", "स्वरा", "सर्व")
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        val phoneticTokens = phonetic.split(" ").filter { it.isNotBlank() }

        val targetKeywords = listOf("svara", "swara", "sarva", "స్వర", "స్వరా", "సర్వ", "సర్వా", "स्वरा", "सर्व", "स्वर", "सर्वा")
        for (token in tokens) {
            for (keyword in targetKeywords) {
                if (token == keyword) return true
                if (token.length >= 4 && keyword.length >= 4 && levenshteinDistance(token, keyword) <= 1) {
                    return true
                }
            }
        }

        for (pToken in phoneticTokens) {
            if (pToken == "svara" || pToken == "sarva") return true
            if (pToken.length >= 4 && levenshteinDistance(pToken, "svara") <= 1) return true
        }

        // 3. Prefix + Wake Word combination (e.g. "hey", "hello", "hi", "ok", "oye" + token ~ "svara")
        val prefixes = listOf("hey", "heyy", "hello", "hi", "ok", "okay", "oy", "oye", "హే", "ఓయ్", "హలో", "నమస్తే", "हे", "ओए", "अरे", "सुनो", "नमस्ते")
        for (i in 0 until tokens.size - 1) {
            val prefix = tokens[i]
            val nextToken = tokens[i + 1]
            if (prefixes.contains(prefix)) {
                for (keyword in targetKeywords) {
                    if (nextToken == keyword || (nextToken.length >= 4 && levenshteinDistance(nextToken, keyword) <= 1)) {
                        return true
                    }
                }
                val pNext = toPhoneticRepresentation(nextToken)
                if (pNext == "svara" || pNext == "sarva" || (pNext.length >= 4 && levenshteinDistance(pNext, "svara") <= 1)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Checks if the given spoken text contains or matches an application shutdown / close command.
     */
    fun isCloseCommand(text: String): Boolean {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return false

        // 1. Direct exact match or phrase-boundary containment with any canonical close phrase
        for (closeCmd in ALL_CLOSE_COMMANDS) {
            val normCmd = normalizeText(closeCmd)
            if (normalized == normCmd) {
                return true
            }
            // If closeCmd has multiple words (e.g. "close app", "exit application", "యాప్ మూసివేయి", "ऐप बंद करो")
            if (normCmd.contains(" ")) {
                if (normalized.startsWith("$normCmd ") ||
                    normalized.endsWith(" $normCmd") ||
                    normalized.contains(" $normCmd ")
                ) {
                    return true
                }
            }
        }

        // 2. Strip conversational / polite prefixes or suffixes and check exact match again
        val stripWords = listOf(
            "please", "can you", "could you", "hey", "heyy",
            "svara", "swara", "sarva", "hey svara", "hey swara", "hey sarva", "heyy swara",
            "దయచేసి", "స్వర", "స్వరా", "సర్వ", "సర్వా", "ఓయ్ స్వర", "ఓయ్ సర్వ", "హే స్వర",
            "कृपया", "स्वरा", "सर्व", "हे स्वरा", "हे सर्व", "सुनो"
        )
        var stripped = normalized
        for (word in stripWords) {
            val p = normalizeText(word)
            if (stripped.startsWith("$p ")) {
                stripped = stripped.removePrefix("$p ").trim()
            }
            if (stripped.endsWith(" $p")) {
                stripped = stripped.removeSuffix(" $p").trim()
            }
        }

        for (closeCmd in ALL_CLOSE_COMMANDS) {
            val normCmd = normalizeText(closeCmd)
            if (stripped == normCmd) {
                return true
            }
        }

        // 3. Exact single-word commands (only when utterance has exactly 1 token or after stripping courtesies)
        val tokens = stripped.split(" ").filter { it.isNotBlank() }
        if (tokens.size == 1) {
            val single = tokens.first()
            if (single in listOf("exit", "close", "quit", "shutdown", "మూసివేయి", "మూసేయి", "నిష్క్రమించు", "సెలవు", "अलविदा", "बाय")) {
                return true
            }
        }

        return false
    }

    /**
     * Spoken goodbye confirmation messages per language.
     */
    fun getGoodbyeMessage(lang: String): String {
        return when (lang.lowercase(Locale.ROOT)) {
            "te" -> "సెలవు! సర్వ ఏఐ మూసివేయబడుతోంది."
            "hi" -> "अलविदा! सर्व एआई बंद किया जा रहा है।"
            else -> "Goodbye! Closing SarvaAI."
        }
    }

    /**
     * Spoken listening acknowledgment prompts per language.
     */
    fun getListeningMessage(lang: String): String {
        return when (lang.lowercase(Locale.ROOT)) {
            "te" -> "నేను వింటున్నాను. మీరు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారు?"
            "hi" -> "मैं सुन रहा हूँ। आप क्या ऑर्डर करना चाहेंगे?"
            else -> "I am listening. How can I help you today?"
        }
    }
}
