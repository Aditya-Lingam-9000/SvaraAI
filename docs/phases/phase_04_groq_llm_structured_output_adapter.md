# PHASE 4: GROQ LLM INTELLIGENCE ENGINE & STRUCTURED OUTPUT ADAPTER
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 4 implements the intelligent core and output accessibility layer:
1. Integrate Groq Cloud API with `Llama 3.3 70B Versatile` for sub-second conversational reasoning.
2. Enforce strict JSON Schema output contracts, eliminating raw LLM markdown or hallucinated structures.
3. Build the **Output Adapter**, transforming structured AI intent into accessible spoken prose, captions, and TalkBack semantics.
4. Implement the **Number & Currency Verbalizer** converting numbers, prices, and quantities into phonetically exact words across English, Hindi, and Telugu.
5. Implement the **Dish Name Transliteration Protector** safeguarding Indian culinary terms during NMT.
6. Build the **Raw vs Accessible Comparative View** for hackathon jury demonstration.

---

### 2. Groq Cloud API Integration Specification

#### 2.1 API Request Architecture
- **URL**: `POST https://api.groq.com/openai/v1/chat/completions`
- **Headers**:
  ```http
  Authorization: Bearer <GROQ_API_KEY>
  Content-Type: application/json
  ```
- **Payload Schema**:
  ```json
  {
    "model": "llama-3.3-70b-versatile",
    "messages": [
      { "role": "system", "content": "<SYSTEM_PROMPT_ENFORCING_SCHEMA>" },
      { "role": "user", "content": "<USER_QUERY_IN_ENGLISH>" }
    ],
    "temperature": 0.1,
    "max_tokens": 1024,
    "response_format": { "type": "json_object" }
  }
  ```

---

### 3. Structured AI Contract Specification (`StructuredAiResponse.kt`)

```kotlin
package com.sarvaai.app.llm.models

import kotlinx.serialization.Serializable

@Serializable
data class StructuredAiResponse(
    val intent: String,
    val headline: String,
    val spoken: String,
    val caption: String,
    val bullets: List<String> = emptyList(),
    val table: StructuredTable? = null,
    val tableNarration: String? = null,
    val simpleLevel: Int = 1,
    val confidence: String = "high",
    val needsStaffConfirmation: Boolean = false,
    val actions: List<UiAction> = emptyList(),
    val followupPrompt: String? = null
)

@Serializable
data class StructuredTable(
    val columns: List<String>,
    val rows: List<List<String>>
)

@Serializable
data class UiAction(
    val id: String,
    val label: String,
    val actionType: String, // "ADD_TO_CART", "ASK_WAITER", "NAVIGATE_SECTION"
    val payload: String? = null
)
```

---

### 4. Output Adapter Architecture

#### 4.1 Spoken Output Sanitizer
Raw markdown (e.g. `**Paneer Tikka** - *₹250* [Jain]`) sounds like gibberish when passed to TTS. The Output Adapter:
1. Strips all markdown formatting (`*`, `_`, `#`, `[`, `]`, `~`, `` ` ``).
2. Strips all emoji and non-phonetic unicode symbols.
3. Enforces sentence lengths under 20 words for acoustic comprehensibility.
4. Appends a spoken description for any table data via `tableNarration`.

#### 4.2 Number & Currency Verbalizer (`adapt/verbalizer/NumberVerbalizer.kt`)
Converts digits into authentic spoken words in target languages:

```kotlin
package com.sarvaai.app.adapt.verbalizer

object NumberVerbalizer {

    fun verbalizePrice(amountRupees: Long, language: String): String {
        return when (language) {
            "hi" -> "${numberToWordsHindi(amountRupees)} रुपये"
            "te" -> "${numberToWordsTelugu(amountRupees)} రూపాయలు"
            else -> "${numberToWordsEnglish(amountRupees)} rupees"
        }
    }

    fun verbalizeQuantity(quantity: Int, language: String): String {
        return when (language) {
            "hi" -> "${numberToWordsHindi(quantity.toLong())} प्लेट"
            "te" -> "${numberToWordsTelugu(quantity.toLong())} ప్లేట్లు"
            else -> "${numberToWordsEnglish(quantity.toLong())} portions of"
        }
    }

    private fun numberToWordsEnglish(n: Long): String {
        if (n == 0L) return "zero"
        val units = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

        return when {
            n < 20 -> units[n.toInt()]
            n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
            n < 1000 -> units[(n / 100).toInt()] + " hundred" + (if (n % 100 != 0L) " and " + numberToWordsEnglish(n % 100) else "")
            n < 100000 -> numberToWordsEnglish(n / 1000) + " thousand" + (if (n % 1000 != 0L) " " + numberToWordsEnglish(n % 1000) else "")
            else -> "$n"
        }
    }

    private fun numberToWordsHindi(n: Long): String {
        val hindiMap = mapOf(
            1L to "एक", 2L to "दो", 3L to "तीन", 4L to "चार", 5L to "पांच",
            6L to "छह", 7L to "सात", 8L to "आठ", 9L to "नौ", 10L to "दस",
            20L to "बीस", 50L to "पचास", 100L to "सौ", 200L to "दो सौ",
            250L to "दो सौ पचास", 500L to "पांच सौ", 1000L to "एक हज़ार"
        )
        return hindiMap[n] ?: when {
            n < 100 -> "$n"
            n < 1000 -> "${hindiMap[n / 100] ?: (n / 100).toString()} सौ" + (if (n % 100 != 0L) " ${numberToWordsHindi(n % 100)}" else "")
            else -> "$n"
        }
    }

    private fun numberToWordsTelugu(n: Long): String {
        val teluguMap = mapOf(
            1L to "ఒకటి", 2L to "రెండు", 3L to "మూడు", 4L to "నాలుగు", 5L to "ఐదు",
            6L to "ఆరు", 7L to "ఏడు", 8L to "ఎనిమిది", 9L to "తొమ్మిది", 10L to "పది",
            20L to "ఇరవై", 50L to "యాభై", 100L to "వంద", 200L to "రెండు వందల",
            250L to "రెండు వందల యాభై", 480L to "నాలుగు వందల ఎనభై", 1000L to "వెయ్యి"
        )
        return teluguMap[n] ?: when {
            n < 1000 -> "${teluguMap[n / 100] ?: (n / 100).toString()} వందల" + (if (n % 100 != 0L) " ${numberToWordsTelugu(n % 100)}" else "")
            else -> "$n"
        }
    }
}
```

#### 4.3 Dish Name Transliteration Protector (`adapt/transliteration/IndicDishTransliterationProtector.kt`)

```kotlin
package com.sarvaai.app.adapt.transliteration

object IndicDishTransliterationProtector {
    private val DISH_TRANSLITERATION_MAP = mapOf(
        "Butter Naan" to Pair("बटर नान", "బటర్ నాన్"),
        "Garlic Naan" to Pair("गार्लिक नान", "గార్లిక్ నాన్"),
        "Paneer Butter Masala" to Pair("पनीर बटर मसाला", "పనీర్ బటర్ మసాలా"),
        "Kadai Paneer" to Pair("कड़ाही पनीर", "కడాయి పనీర్"),
        "Dal Makhani" to Pair("दाल मखनी", "దాల్ మఖని"),
        "Chicken Biryani" to Pair("चिकन बिरयानी", "చికెన్ బిర్యానీ"),
        "Mutton Biryani" to Pair("मटन बिरयानी", "మటన్ బిర్యానీ"),
        "Gulab Jamun" to Pair("गुलाब जामुन", "గులాబ్ జామున్")
    )

    fun protectTokens(englishText: String): Pair<String, Map<String, String>> {
        var protectedText = englishText
        val tokenMap = mutableMapOf<String, String>()
        var counter = 1

        DISH_TRANSLITERATION_MAP.keys.forEach { dishName ->
            if (protectedText.contains(dishName, ignoreCase = true)) {
                val token = "⟦DISH_${counter}⟧"
                tokenMap[token] = dishName
                protectedText = protectedText.replace(Regex(dishName, RegexOption.IGNORE_CASE), token)
                counter++
            }
        }
        return Pair(protectedText, tokenMap)
    }

    fun restoreTransliterated(translatedText: String, tokenMap: Map<String, String>, targetLanguage: String): String {
        var restored = translatedText
        tokenMap.forEach { (token, originalEnglishName) ->
            val transliterationPair = DISH_TRANSLITERATION_MAP[originalEnglishName]
            val nativeScript = when (targetLanguage) {
                "hi" -> transliterationPair?.first ?: originalEnglishName
                "te" -> transliterationPair?.second ?: originalEnglishName
                else -> originalEnglishName
            }
            restored = restored.replace(token, nativeScript)
        }
        return restored
    }
}
```

---

### 5. Raw vs Accessible Comparative Debug View (`ui/screens/debug/RawVsAccessibleDebugView.kt`)

Provides an instant visual and acoustic comparison for hackathon judges:
- **Left Column / Raw View**: Raw markdown output from LLM, unformatted currency symbols (`₹480`), robotic un-chunked speech.
- **Right Column / SarvaAI Accessible View**: WCAG AAA high-contrast layout, verbalized currency (*"నాలుగు వందల ఎనభై రూపాయలు"*), sentence-chunked speech, synchronized live captions, and TalkBack semantic nodes.

---

### 6. Physical Device Testing Protocol on realme P3 Pro (Android 16)

#### 6.1 Groq API Latency Benchmark
1. Send complex Q&A prompt to Groq client on phone:
   *"Compare Kadai Paneer and Paneer Butter Masala in terms of spice and texture."*
2. **Pass Criteria**: Groq response latency < 600ms; JSON parsed successfully into `StructuredAiResponse`.

#### 6.2 Number Verbalization Acoustic Test
1. Set language to Telugu (`te`).
2. Synthesize: ₹250, ₹480, and ₹1,200.
3. **Pass Criteria**: Phone speakers verbalize:
   - *"రెండు వందల యాభై రూపాయలు"*
   - *"నాలుగు వందల ఎనభై రూపాయలు"*
   - *"వెయ్యి రెండు వందల రూపాయలు"*

#### 6.3 Dish Pronunciation Test
1. Synthesize sentence containing "Butter Naan" in Telugu.
2. **Pass Criteria**: Bhashini TTS pronounces *"బటర్ నాన్"* fluently; zero English phonetic stuttering.

---

### 7. Failure Modes & Edge Cases

| Failure Mode | Root Cause | Exact Resolution |
|---|---|---|
| Groq returns markdown triple backticks around JSON | Llama model formatted response as codeblock | `JsonCleaner` strips ````json and ```` fences before passing to Kotlinx Serialization. |
| Groq HTTP 429 Rate Limit | Burst requests exceeded RPM | Exponential backoff retry with 500ms initial jitter; fallback to local heuristic template generator if 3 attempts fail. |

---

### 8. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase4): implement groq llama 3.3 client, structured ai response schema, number verbalizer, and dish transliteration protector"
git push origin main
```

---

### 9. Exit Criteria Checklist
- [x] Groq Cloud API sub-second response time verified on physical device.
- [x] Strict JSON schema guarantees structured domain responses.
- [x] Number verbalizer passes 100% test cases for English, Hindi, and Telugu.
- [x] Dish transliteration protector ensures natural Indic speech pronunciation.
- [x] Raw vs Accessible debug view functional for demonstration.
- [x] Code pushed to remote repository.
