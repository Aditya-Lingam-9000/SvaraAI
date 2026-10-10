package com.sarvaai.app.adapt

import com.sarvaai.app.adapt.transliteration.IndicDishTransliterationProtector
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.llm.models.StructuredAiResponse

data class AccessibleOutput(
    val spokenText: String,
    val captionText: String,
    val accessibilityAnnouncement: String,
    val formattedHeadline: String,
    val formattedBullets: List<String>
)

object OutputAdapter {

    private val MARKDOWN_REGEX = Regex("[*_#`~<>\\[\\]{}]")
    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cs}\\p{Cn}\\uFE0F]")
    private val PRICE_PATTERN = Regex("₹\\s*(\\d+)")

    /**
     * Adapts raw structured AI output into accessible formats (TTS speech, Captions, TalkBack).
     */
    fun adapt(response: StructuredAiResponse, language: String): AccessibleOutput {
        val lang = language.lowercase()

        // 1. Sanitize raw text
        var rawSpoken = response.spoken
        var rawCaption = response.caption.ifBlank { response.spoken }
        var rawHeadline = response.headline

        // 2. Verbalize any currency numbers embedded in text (e.g. ₹250 -> 'two hundred fifty rupees' / 'రెండు వందల యాభై రూపాయలు')
        rawSpoken = PRICE_PATTERN.replace(rawSpoken) { matchResult ->
            val amount = matchResult.groupValues[1].toLongOrNull() ?: 0L
            " " + NumberVerbalizer.verbalizePrice(amount, lang) + " "
        }

        // 3. Strip unwanted markdown, emojis and special formatting
        val cleanSpoken = rawSpoken
            .replace(EMOJI_REGEX, "")
            .replace(MARKDOWN_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val cleanCaption = rawCaption
            .replace(EMOJI_REGEX, "")
            .replace(MARKDOWN_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val cleanHeadline = rawHeadline
            .replace(EMOJI_REGEX, "")
            .replace(MARKDOWN_REGEX, "")
            .trim()

        val formattedBullets = response.bullets.map { bullet ->
            PRICE_PATTERN.replace(bullet) { matchResult ->
                val amount = matchResult.groupValues[1].toLongOrNull() ?: 0L
                NumberVerbalizer.verbalizePrice(amount, lang)
            }.replace(EMOJI_REGEX, "").replace(MARKDOWN_REGEX, "").trim()
        }

        // 4. Construct TalkBack announcement
        val announcement = StringBuilder()
        announcement.append(cleanHeadline)
        if (cleanHeadline.isNotEmpty() && !cleanHeadline.endsWith(".")) {
            announcement.append(". ")
        }
        announcement.append(cleanSpoken)

        if (response.actions.isNotEmpty()) {
            val actionLabels = response.actions.joinToString(", ") { it.label }
            announcement.append(" Available actions: $actionLabels.")
        }

        return AccessibleOutput(
            spokenText = cleanSpoken,
            captionText = cleanCaption,
            accessibilityAnnouncement = announcement.toString(),
            formattedHeadline = cleanHeadline,
            formattedBullets = formattedBullets
        )
    }
}
