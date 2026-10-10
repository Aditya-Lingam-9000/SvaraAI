package com.sarvaai.app.bhashini.matrix

data class LanguageCapability(
    val code: String,
    val nameEnglish: String,
    val nameNative: String,
    val supportsAsr: Boolean = true,
    val supportsNmt: Boolean = true,
    val supportsTts: Boolean = true,
    val supportsTransliteration: Boolean = true
)

object CapabilityMatrix {

    val SUPPORTED_LANGUAGES = listOf(
        LanguageCapability(
            code = "en",
            nameEnglish = "English",
            nameNative = "English",
            supportsAsr = true,
            supportsNmt = true,
            supportsTts = true,
            supportsTransliteration = true
        ),
        LanguageCapability(
            code = "hi",
            nameEnglish = "Hindi",
            nameNative = "हिन्दी",
            supportsAsr = true,
            supportsNmt = true,
            supportsTts = true,
            supportsTransliteration = true
        ),
        LanguageCapability(
            code = "te",
            nameEnglish = "Telugu",
            nameNative = "తెలుగు",
            supportsAsr = true,
            supportsNmt = true,
            supportsTts = true,
            supportsTransliteration = true
        )
    )

    fun getLanguageByCode(code: String): LanguageCapability {
        return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES[0]
    }

    fun isLanguageSupported(code: String): Boolean {
        return SUPPORTED_LANGUAGES.any { it.code.equals(code, ignoreCase = true) }
    }
}
