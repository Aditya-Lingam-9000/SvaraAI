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
        if (n == 0L) return "शून्य"
        val units = arrayOf(
            "", "एक", "दो", "तीन", "चार", "पांच", "छह", "सात", "आठ", "नौ", "दस",
            "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस"
        )
        val tens = arrayOf("", "", "बीस", "तीस", "चालीस", "पचास", "साठ", "सत्तर", "अस्सी", "नब्बे")

        return when {
            n < 20 -> units[n.toInt()]
            n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
            n < 1000 -> units[(n / 100).toInt()] + " सौ" + (if (n % 100 != 0L) " " + numberToWordsHindi(n % 100) else "")
            n < 100000 -> numberToWordsHindi(n / 1000) + " हज़ार" + (if (n % 1000 != 0L) " " + numberToWordsHindi(n % 1000) else "")
            else -> "$n"
        }
    }

    private fun numberToWordsTelugu(n: Long): String {
        if (n == 0L) return "సున్నా"
        val units = arrayOf(
            "", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది", "పది",
            "పదకొండు", "పన్నెండు", "పదమూడు", "పద్నాలుగు", "పదిహేను", "పదహారు", "పదిహేడు", "పద్దెనిమిది", "పందొమ్మిది"
        )
        val tens = arrayOf("", "", "ఇరవై", "ముప్పై", "నలభై", "యాభై", "అరవై", "డెబ్బై", "ఎనభై", "తొంభై")

        return when {
            n < 20 -> units[n.toInt()]
            n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
            n < 1000 -> {
                val hundredPrefix = if (n / 100 == 1L) "వంద" else "${units[(n / 100).toInt()]} వందల"
                hundredPrefix + (if (n % 100 != 0L) " " + numberToWordsTelugu(n % 100) else "")
            }
            n < 100000 -> {
                val thousandPrefix = if (n / 1000 == 1L) "వెయ్యి" else "${numberToWordsTelugu(n / 1000)} వేల"
                thousandPrefix + (if (n % 1000 != 0L) " " + numberToWordsTelugu(n % 1000) else "")
            }
            else -> "$n"
        }
    }
}
