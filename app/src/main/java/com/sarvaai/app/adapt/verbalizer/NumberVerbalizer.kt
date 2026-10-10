package com.sarvaai.app.adapt.verbalizer

object NumberVerbalizer {

    fun verbalizePrice(amountRupees: Long, language: String): String {
        return when (language.lowercase()) {
            "hi" -> "${numberToWordsHindi(amountRupees)} रुपये"
            "te" -> "${numberToWordsTelugu(amountRupees)} రూపాయలు"
            else -> "${numberToWordsEnglish(amountRupees)} rupees"
        }
    }

    fun verbalizeQuantity(quantity: Int, language: String): String {
        return when (language.lowercase()) {
            "hi" -> "${numberToWordsHindi(quantity.toLong())} प्लेट"
            "te" -> "${numberToWordsTelugu(quantity.toLong())} ప్లేట్లు"
            else -> "${numberToWordsEnglish(quantity.toLong())} portions of"
        }
    }

    fun numberToWords(n: Long, language: String): String {
        return when (language.lowercase()) {
            "hi" -> numberToWordsHindi(n)
            "te" -> numberToWordsTelugu(n)
            else -> numberToWordsEnglish(n)
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
            0L to "शून्य", 1L to "एक", 2L to "दो", 3L to "तीन", 4L to "चार", 5L to "पांच",
            6L to "छह", 7L to "सात", 8L to "आठ", 9L to "नौ", 10L to "दस",
            11L to "ग्यारह", 12L to "बारह", 15L to "पंद्रह", 20L to "बीस",
            30L to "तीस", 40L to "चालीस", 50L to "पचास", 60L to "साठ",
            70L to "सत्तर", 80L to "अस्सी", 90L to "नब्बे", 100L to "सौ",
            200L to "दो सौ", 250L to "दो सौ पचास", 300L to "तीन सौ",
            500L to "पांच सौ", 1000L to "एक हज़ार"
        )
        return hindiMap[n] ?: when {
            n < 100 -> "$n"
            n < 1000 -> "${hindiMap[n / 100] ?: (n / 100).toString()} सौ" + (if (n % 100 != 0L) " ${numberToWordsHindi(n % 100)}" else "")
            else -> "$n"
        }
    }

    private fun numberToWordsTelugu(n: Long): String {
        val teluguMap = mapOf(
            0L to "సున్నా", 1L to "ఒకటి", 2L to "రెండు", 3L to "మూడు", 4L to "నాలుగు", 5L to "ఐదు",
            6L to "ఆరు", 7L to "ఏడు", 8L to "ఎనిమిది", 9L to "తొమ్మిది", 10L to "పది",
            11L to "పదకొండు", 12L to "పన్నెండు", 15L to "పదిహేను", 20L to "ఇరవై",
            30L to "ముప్పై", 40L to "నలభై", 50L to "యాభై", 60L to "అరవై",
            70L to "డెబ్బై", 80L to "ఎనభై", 90L to "తొంభై", 100L to "వంద",
            200L to "రెండు వందల", 250L to "రెండు వందల యాభై", 300L to "మూడు వందల",
            480L to "నాలుగు వందల ఎనభై", 500L to "ఐదు వందల", 1000L to "వెయ్యి"
        )
        return teluguMap[n] ?: when {
            n < 1000 -> "${teluguMap[n / 100] ?: (n / 100).toString()} వందల" + (if (n % 100 != 0L) " ${numberToWordsTelugu(n % 100)}" else "")
            else -> "$n"
        }
    }
}
