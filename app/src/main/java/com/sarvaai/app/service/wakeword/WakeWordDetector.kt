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
        "svare",
        "swara",
        "sware",
        "hey swara",
        "hey svare",
        "hey sware",
        "svara open",
        "hey svara open",
        "swara open",
        "hey swara open",
        "svare open",
        "hey svare open",
        "hello svara",
        "hello swara",
        "hello svare",
        "hi svara",
        "hi swara",
        "hi svare",
        "ok svara",
        "okay svara",
        "ok swara",
        "okay swara",
        "ok svare",
        "svara ai",
        "swara ai",
        "svare ai",
        "sarva",
        "hey sarva",
        "sarva ai",
        "sarva open",
        "hey sarva open",
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
        "స్వర ఓపెన్ చేయి",
        "స్వరా ఓపెన్ చేయి",
        "స్వర ఓపెన్",
        "స్వరా ఓపెన్",
        "హలో స్వర",
        "హలో స్వరా",
        "నమస్తే స్వర",
        "నమస్తే స్వరా",
        "నమస్కారం స్వర",
        "నమస్కారం స్వరా",
        "స్వర విను",
        "స్వరా విను",
        "హే సర్వ",
        "హే సర్వా",
        "ఓయ్ సర్వ",
        "ఓయ్ సర్వా",
        "సర్వ",
        "సర్వా",
        "సర్వ ఓపెన్ చేయి",
        "సర్వా ఓపెన్ చేయి"
    )

    private val WAKE_PHRASES_HI = listOf(
        "हे स्वरा",
        "स्वरा",
        "स्वरा खोलो",
        "स्वरा ओपन करो",
        "हे स्वर",
        "स्वर",
        "स्वर खोलो",
        "स्वर ओपन करो",
        "नमस्ते स्वरा",
        "हैलो स्वरा",
        "ओए स्वरा",
        "अरे स्वरा",
        "सुनो स्वरा",
        "स्वरा सुनो",
        "हे सर्व",
        "सर्व",
        "सर्व खोलो",
        "सर्व ओपन करो",
        "हे सर्वा",
        "सर्वा"
    )

    val ALL_WAKE_WORDS: List<String> = (WAKE_PHRASES_EN + WAKE_PHRASES_TE + WAKE_PHRASES_HI).distinct()

    // Canonical Close / Shutdown Phrases
    private val CLOSE_PHRASES_EN = listOf(
        "close app",
        "close the app",
        "close application",
        "close the application",
        "exit app",
        "exit the app",
        "exit application",
        "exit the application",
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
        "bye swara",
        "bye svare",
        "goodbye svara",
        "goodbye swara",
        "goodbye",
        "good bye",
        "bye",
        "svara close",
        "swara close",
        "svare close",
        "hey svara close",
        "hey swara close",
        "hey svare close",
        "svara close app",
        "hey svara close app",
        "swara close app",
        "hey swara close app",
        "svara close the application",
        "svare close the application",
        "swara close the application",
        "hey svara close the application",
        "hey svare close the application",
        "hey swara close the application",
        "svara exit application",
        "hey svara exit application",
        "close svara",
        "close swara",
        "close svare",
        "exit svara",
        "exit swara",
        "app close"
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
        "బంద్ చేయి",
        "క్లోజ్ చెయ్యి",
        "యాప్ క్లోజ్ చెయ్యి",
        "close cheyyi",
        "app close cheyyi",
        "క్లోజ్ యాప్",
        "క్లోజ్ అప్లికేషన్",
        "క్లోజ్",
        "క్లోస్",
        "ఎగ్జిట్",
        "క్లోజ్ చేయండి",
        "మూసివేయండి",
        "బంద్ చేయండి"
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
        "ऐप बंद कर दो",
        "app close karo",
        "app band karo",
        "band karo",
        "band kar do",
        "band kijiye",
        "close karo",
        "क्लोज ऐप",
        "क्लोज एप",
        "क्लोज",
        "क्लोस",
        "क्लोज एप्लीकेशन",
        "क्लोज दी एप्लीकेशन",
        "क्लोज करो",
        "क्लोज कीजिए",
        "क्लोज करें",
        "एक्जिट",
        "बाय बाय"
    )

    val ALL_CLOSE_COMMANDS: List<String> = (CLOSE_PHRASES_EN + CLOSE_PHRASES_TE + CLOSE_PHRASES_HI).distinct()

    private val CONVERSATIONAL_STRIP_TERMS = listOf(
        // English phrases & words
        "hey svara", "hey swara", "hey svare", "hey sarva", "heyy swara", "heyy svara",
        "can you please", "could you please", "can you", "could you", "please",
        "hey", "heyy", "hello", "hi", "svara", "swara", "svare", "sware", "sarva", "sarve", "now",
        // Telugu phrases & words
        "దయచేసి", "ఓయ్ స్వర", "ఓయ్ స్వరా", "ఓయ్ సర్వ", "ఓయ్ సర్వా", "హే స్వర", "హే స్వరా", "హే సర్వ", "హే సర్వా",
        "స్వర", "స్వరా", "సర్వ", "సర్వా", "హే", "ఓయ్", "హలో", "నమస్తే", "నమస్కారం",
        // Hindi phrases & words
        "कृपया", "हे स्वरा", "हे स्वर", "हे सर्व", "हे सर्वा", "ओए स्वरा", "अरे स्वरा",
        "स्वरा", "स्वर", "सर्व", "सर्वा", "सुनो", "हे", "ओए", "अरे", "नमस्ते"
    )

    private val FOOD_ORDER_KEYWORDS = listOf(
        "order", "dosa", "idli", "coffee", "cart", "food", "item", "dish", "menu", "bill", "split",
        "ఆర్డర్", "బిల్లు", "ఆహారం",
        "खाना", "ऑर्डर", "बिल"
    )

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
     * Strips conversational and polite prefixes/suffixes iteratively from normalized text.
     */
    fun stripConversationalTerms(text: String): String {
        var result = text.trim()
        var changed = true
        while (changed) {
            changed = false
            for (term in CONVERSATIONAL_STRIP_TERMS) {
                val normTerm = normalizeText(term)
                if (normTerm.isEmpty()) continue
                if (result == normTerm) {
                    continue // Do not strip everything to empty
                }
                if (result.startsWith("$normTerm ")) {
                    result = result.removePrefix("$normTerm ").trim()
                    changed = true
                }
                if (result.endsWith(" $normTerm")) {
                    result = result.removeSuffix(" $normTerm").trim()
                    changed = true
                }
            }
        }
        return result
    }

    /**
     * Checks if text contains any food/ordering keywords.
     */
    fun containsFoodOrOrderKeywords(text: String): Boolean {
        val norm = normalizeText(text)
        if (norm.isEmpty()) return false
        val tokens = norm.split(" ").filter { it.isNotBlank() }
        for (keyword in FOOD_ORDER_KEYWORDS) {
            val normKeyword = normalizeText(keyword)
            if (normKeyword.isEmpty()) continue
            // Check Indic script substrings
            if (normKeyword.any { it.code > 127 }) {
                if (norm.contains(normKeyword)) return true
            } else {
                // Check English word tokens / plurals / prefixes
                for (token in tokens) {
                    if (token == normKeyword ||
                        (normKeyword.length >= 4 && token.startsWith(normKeyword)) ||
                        (normKeyword == "item" && token.startsWith("item")) ||
                        (normKeyword == "dish" && token.startsWith("dish")) ||
                        (normKeyword == "bill" && token.startsWith("bill"))
                    ) {
                        return true
                    }
                }
            }
        }
        return false
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

        // 2. Token-level single word check ("svara", "swara", "svare", "sware", "sarva", "స్వర", "స్వరా", "సర్వ", "स्वरा", "सर्व")
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        val phoneticTokens = phonetic.split(" ").filter { it.isNotBlank() }

        val targetKeywords = listOf(
            "svara", "swara", "svare", "sware", "sarva", "sarve", "svaara", "swarah", "shvara", "shwara",
            "స్వర", "స్వరా", "సర్వ", "సర్వా",
            "स्वरा", "सर्व", "स्वर", "सर्वा"
        )
        for (token in tokens) {
            for (keyword in targetKeywords) {
                if (token == keyword) return true
                if (token.length >= 4 && keyword.length >= 4 && levenshteinDistance(token, keyword) <= 1) {
                    return true
                }
            }
        }

        for (pToken in phoneticTokens) {
            if (pToken in listOf("svara", "svare", "sarva", "sarve")) return true
            if (pToken.length >= 4 && (levenshteinDistance(pToken, "svara") <= 1 || levenshteinDistance(pToken, "svare") <= 1 || levenshteinDistance(pToken, "sarva") <= 1)) return true
        }

        // 3. Prefix + Wake Word combination (e.g. "hey", "hello", "hi", "ok", "oye" + token ~ "svara")
        val prefixes = listOf(
            "hey", "heyy", "hello", "hi", "ok", "okay", "oy", "oye",
            "హే", "ఓయ్", "హలో", "నమస్తే", "నమస్కారం",
            "हे", "ओए", "अरे", "सुनो", "नमस्ते"
        )
        for (i in 0 until tokens.size - 1) {
            val prefix = tokens[i]
            val nextToken = tokens[i + 1]
            if (prefixes.contains(prefix)) {
                for (keyword in targetKeywords) {
                    if (nextToken == keyword || (nextToken.length >= 4 && keyword.length >= 4 && levenshteinDistance(nextToken, keyword) <= 1)) {
                        return true
                    }
                }
                val pNext = toPhoneticRepresentation(nextToken)
                if (pNext in listOf("svara", "svare", "sarva", "sarve") ||
                    (pNext.length >= 4 && (levenshteinDistance(pNext, "svara") <= 1 || levenshteinDistance(pNext, "svare") <= 1 || levenshteinDistance(pNext, "sarva") <= 1))
                ) {
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

        val hasFoodKeywords = containsFoodOrOrderKeywords(normalized)

        // 1. Direct exact match or phrase-boundary containment with any canonical close phrase
        for (closeCmd in ALL_CLOSE_COMMANDS) {
            val normCmd = normalizeText(closeCmd)
            if (normalized == normCmd) {
                return true
            }
            if (!hasFoodKeywords && normCmd.contains(" ")) {
                if (normalized.startsWith("$normCmd ") ||
                    normalized.endsWith(" $normCmd") ||
                    normalized.contains(" $normCmd ")
                ) {
                    return true
                }
            }
        }

        // 2. Strip conversational / polite prefixes or suffixes and check match again
        val stripped = stripConversationalTerms(normalized)
        for (closeCmd in ALL_CLOSE_COMMANDS) {
            val normCmd = normalizeText(closeCmd)
            if (stripped == normCmd) {
                return true
            }
            if (!hasFoodKeywords && normCmd.contains(" ")) {
                if (stripped.startsWith("$normCmd ") ||
                    stripped.endsWith(" $normCmd") ||
                    stripped.contains(" $normCmd ")
                ) {
                    return true
                }
            }
        }

        // 3. Exact single-word commands (only when utterance has exactly 1 token after stripping courtesies and no food keywords)
        if (!hasFoodKeywords) {
            val tokens = stripped.split(" ").filter { it.isNotBlank() }
            if (tokens.size == 1) {
                val single = tokens.first()
                if (single in listOf(
                        "exit", "close", "quit", "shutdown", "bye", "goodbye",
                        "ఆపు", "మూసివేయి", "మూసేయి", "నిష్క్రమించు", "సెలవు",
                        "अलविदा", "एग्जिट", "बाय", "क्लोज", "क्लोस", "క్లోజ్", "క్లోస్", "ఎగ్జిట్"
                    )
                ) {
                    return true
                }
            }

            // 4. Phonetic & Fuzzy Edit Distance matching on short utterances
            val phonetic = toPhoneticRepresentation(stripped)
            val phoneticClosePhrases = listOf("clos ap", "klos ap", "exit ap", "kvit ap", "sut daun ap", "sutdaun ap", "band karo", "band kar do", "klos karo", "klos cheyyi")
            for (pCmd in phoneticClosePhrases) {
                if (phonetic == pCmd || phonetic.startsWith("$pCmd ") || phonetic.endsWith(" $pCmd") || phonetic.contains(" $pCmd ")) {
                    return true
                }
            }

            if (tokens.size <= 3) {
                for (token in tokens) {
                    if (token in listOf("close", "exit", "quit", "shutdown") ||
                        (token.length >= 4 && (levenshteinDistance(token, "close") <= 1 || levenshteinDistance(token, "exit") <= 1 || levenshteinDistance(token, "quit") <= 1))
                    ) {
                        if (tokens.size == 1 || tokens.any { it in listOf("app", "application", "svara", "swara", "svare", "sarva", "this", "it") }) {
                            return true
                        }
                    }
                }
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
