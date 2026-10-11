package com.sarvaai.app.bhashini.asr

/**
 * Domain-specific Restaurant Lexical Normalizer.
 * Bridges generic governmental/literary ASR and NMT models with everyday Indian restaurant dining vocabulary.
 * Corrects semantic collisions (e.g., Telugu 'దోశలు' mistranslated by generic NMT as 'mistakes/faults').
 */
object RestaurantLexicalNormalizer {

    /**
     * Post-processes NMT English translation by cross-referencing recognized native speech tokens.
     */
    fun postProcessTranslation(
        rawEnglishTranslation: String,
        nativeTranscript: String,
        sourceLanguage: String
    ): String {
        var english = rawEnglishTranslation.trim()
        val native = nativeTranscript.trim().lowercase()

        when (sourceLanguage.lowercase()) {
            "te" -> {
                // 1. Dosa / Dosalu mistranslation corrections
                if (native.contains("దోశ") || native.contains("దోసె")) {
                    english = english.replace(Regex("(?i)\\b(mistakes|faults|flaws)\\b"), "dosas")
                    english = english.replace(Regex("(?i)\\b(mistake|fault|flaw)\\b"), "dosa")
                    english = english.replace(Regex("(?i)\\b(two spices|spices)\\b"), "masala dosas")
                    english = english.replace(Regex("(?i)\\b(two spice|spice)\\b"), "masala dosa")

                    if (!english.contains("dosa", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " dosas."
                    }
                }

                // 2. Idli corrections
                if (native.contains("ఇడ్లీ") || native.contains("ఇడ్లి")) {
                    if (!english.contains("idli", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " idli."
                    }
                }

                // 3. Poori / Vada / Sambar corrections
                if (native.contains("పూరి") || native.contains("పూరీ")) {
                    english = english.replace(Regex("(?i)\\b(complete|total|entire)\\b"), "poori")
                }
                if (native.contains("వడ") || native.contains("గారె")) {
                    english = english.replace(Regex("(?i)\\b(wind|breeze)\\b"), "vada")
                }

                // 4. Conversational tone smoothing ("You have to have" -> "I want")
                if (english.startsWith("You have to have", ignoreCase = true)) {
                    english = english.replaceFirst(Regex("(?i)You have to have"), "I want")
                } else if (english.startsWith("You need to have", ignoreCase = true)) {
                    english = english.replaceFirst(Regex("(?i)You need to have"), "I want")
                } else if (english.startsWith("You should have", ignoreCase = true)) {
                    english = english.replaceFirst(Regex("(?i)You should have"), "I want")
                }
            }

            "hi" -> {
                // 1. Dosa / Masala Dosa corrections
                if (native.contains("डोसा") || native.contains("डोसे")) {
                    english = english.replace(Regex("(?i)\\b(faults|mistakes)\\b"), "dosas")
                    english = english.replace(Regex("(?i)\\b(fault|mistake)\\b"), "dosa")
                    if (!english.contains("dosa", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " dosas."
                    }
                }

                // 2. Chai / Coffee corrections
                if (native.contains("चाय") && !english.contains("tea", ignoreCase = true) && !english.contains("chai", ignoreCase = true)) {
                    english = english.replace(Regex("\\.+$"), "") + " tea."
                }
            }

            "ta" -> {
                // Tamil: தோசை (Dosa), இட்லி (Idli), வடை (Vada), காபி (Coffee)
                if (native.contains("தோசை") || native.contains("தோச")) {
                    if (!english.contains("dosa", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " dosa."
                    }
                }
                if (native.contains("இட்லி")) {
                    if (!english.contains("idli", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " idli."
                    }
                }
                if (native.contains("வடை")) {
                    if (!english.contains("vada", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " vada."
                    }
                }
            }

            "kn" -> {
                // Kannada: ದೋಸೆ (Dosa), ಇಡ್ಲಿ (Idli), ವಡೆ (Vada), ಕಾಫಿ (Coffee)
                if (native.contains("ದೋಸೆ") || native.contains("ದೋಸ")) {
                    if (!english.contains("dosa", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " dosa."
                    }
                }
                if (native.contains("ಇಡ್ಲಿ")) {
                    if (!english.contains("idli", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " idli."
                    }
                }
                if (native.contains("ವಡೆ")) {
                    if (!english.contains("vada", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " vada."
                    }
                }
            }

            "ml" -> {
                // Malayalam: ദോശ (Dosa), ഇഡ്ഡലി (Idli), വട (Vada), ചായ (Chai)
                if (native.contains("ദോശ") || native.contains("ദോസ")) {
                    if (!english.contains("dosa", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " dosa."
                    }
                }
                if (native.contains("ഇഡ്ഡലി") || native.contains("ഇഡ്‌ലി")) {
                    if (!english.contains("idli", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " idli."
                    }
                }
                if (native.contains("വട")) {
                    if (!english.contains("vada", ignoreCase = true)) {
                        english = english.replace(Regex("\\.+$"), "") + " vada."
                    }
                }
            }
        }

        // Clean double periods and weird punctuation
        english = english.replace(Regex("\\.+"), ".")
        return english.trim()
    }
}
