package com.sarvaai.app.adapt.verbalizer

object NumberVerbalizer {

    fun verbalizePrice(amountRupees: Long, language: String): String {
        return when (language.lowercase()) {
            "hi" -> "${numberToWordsHindi(amountRupees)} रुपये"
            "te" -> "${numberToWordsTelugu(amountRupees)} రూపాయలు"
            "ta" -> "${numberToWordsTamil(amountRupees)} ரூபாய்"
            "kn" -> "${numberToWordsKannada(amountRupees)} ರೂಪಾಯಿ"
            "ml" -> "${numberToWordsMalayalam(amountRupees)} രൂപ"
            else -> "${numberToWordsEnglish(amountRupees)} rupees"
        }
    }

    fun verbalizeQuantity(quantity: Int, language: String): String {
        return when (language.lowercase()) {
            "hi" -> "${numberToWordsHindi(quantity.toLong())} प्लेट"
            "te" -> "${numberToWordsTelugu(quantity.toLong())} ప్లేట్లు"
            "ta" -> "${numberToWordsTamil(quantity.toLong())} தட்டுகள்"
            "kn" -> "${numberToWordsKannada(quantity.toLong())} ಪ್ಲೇಟ್"
            "ml" -> "${numberToWordsMalayalam(quantity.toLong())} പ്ലേറ്റ്"
            else -> "${numberToWordsEnglish(quantity.toLong())} portions of"
        }
    }

    fun numberToWords(n: Long, language: String): String {
        return when (language.lowercase()) {
            "hi" -> numberToWordsHindi(n)
            "te" -> numberToWordsTelugu(n)
            "ta" -> numberToWordsTamil(n)
            "kn" -> numberToWordsKannada(n)
            "ml" -> numberToWordsMalayalam(n)
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

    private fun numberToWordsTamil(n: Long): String {
        if (n == 0L) return "பூஜ்ஜியம்"
        val units = arrayOf(
            "", "ஒன்று", "இரண்டு", "மூன்று", "நான்கு", "ஐந்து", "ஆறு", "ஏழு", "எட்டு", "ஒன்பது", "பத்து",
            "பதினொன்று", "பன்னிரண்டு", "பதின்மூன்று", "பதினான்கு", "பதினைந்து", "பதினாறு", "பதினேழு", "பதினெட்டு", "பத்தொன்பது"
        )
        val tens = arrayOf("", "", "இருபது", "முப்பது", "நாற்பது", "ஐம்பது", "அறுபது", "எழுபது", "எண்பது", "தொண்ணூறு")

        return when {
            n < 20 -> units[n.toInt()]
            n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
            n < 1000 -> {
                val hundredPrefix = if (n / 100 == 1L) "நூறு" else "${units[(n / 100).toInt()]} நூறு"
                hundredPrefix + (if (n % 100 != 0L) " " + numberToWordsTamil(n % 100) else "")
            }
            n < 100000 -> {
                val thousandPrefix = if (n / 1000 == 1L) "ஆயிரம்" else "${numberToWordsTamil(n / 1000)} ஆயிரம்"
                thousandPrefix + (if (n % 1000 != 0L) " " + numberToWordsTamil(n % 1000) else "")
            }
            else -> "$n"
        }
    }

    private fun numberToWordsKannada(n: Long): String {
        if (n == 0L) return "ಸೊನ್ನೆ"
        val units = arrayOf(
            "", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು", "ಹತ್ತು",
            "ಹನ್ನೊಂದು", "ಹನ್ನೆರಡು", "ಹದಿಮೂರು", "ಹದಿನಾಲ್ಕು", "ಹದಿನೈದು", "ಹದಿನಾರು", "ಹದಿನೇಳು", "ಹದಿನೆಂಟು", "ಹತ್ತೊಂಬತ್ತು"
        )
        val tens = arrayOf("", "", "ಇಪ್ಪತ್ತು", "ಮೂವತ್ತು", "ನಲವತ್ತು", "ಐವತ್ತು", "ಅರವತ್ತು", "ಎಪ್ಪತ್ತು", "ಎಂಬತ್ತು", "ತೊಂಬತ್ತು")

        return when {
            n < 20 -> units[n.toInt()]
            n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
            n < 1000 -> {
                val hundredPrefix = if (n / 100 == 1L) "ನೂರು" else "${units[(n / 100).toInt()]} ನೂರು"
                hundredPrefix + (if (n % 100 != 0L) " " + numberToWordsKannada(n % 100) else "")
            }
            n < 100000 -> {
                val thousandPrefix = if (n / 1000 == 1L) "ಸಾವಿರ" else "${numberToWordsKannada(n / 1000)} ಸಾವಿರ"
                thousandPrefix + (if (n % 1000 != 0L) " " + numberToWordsKannada(n % 1000) else "")
            }
            else -> "$n"
        }
    }

    private fun numberToWordsMalayalam(n: Long): String {
        if (n == 0L) return "പൂജ്യം"
        val units = arrayOf(
            "", "ഒന്ന്", "രണ്ട്", "മൂന്ന്", "നാല്", "അഞ്ച്", "ആറ്", "ഏഴ്", "എട്ട്", "ഒൻപത്", "പത്ത്",
            "പതിനൊന്ന്", "പന്ത്രണ്ട്", "പതിമൂന്ന്", "പതിനാല്", "പതിനഞ്ച്", "പതിനാറ്", "പതിനേഴ്", "പതിനെട്ട്", "പത്തൊൻപത്"
        )
        val tens = arrayOf("", "", "ഇരുപത്", "മുപ്പത്", "നാൽപ്പത്", "അൻപത്", "അറുപത്", "എഴുപത്", "എൺപത്", "തൊണ്ണൂറ്")

        return when {
            n < 20 -> units[n.toInt()]
            n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
            n < 1000 -> {
                val hundredPrefix = if (n / 100 == 1L) "നൂറ്" else "${units[(n / 100).toInt()]} നൂറ്"
                hundredPrefix + (if (n % 100 != 0L) " " + numberToWordsMalayalam(n % 100) else "")
            }
            n < 100000 -> {
                val thousandPrefix = if (n / 1000 == 1L) "ആയിരം" else "${numberToWordsMalayalam(n / 1000)} ആയിരം"
                thousandPrefix + (if (n % 1000 != 0L) " " + numberToWordsMalayalam(n % 1000) else "")
            }
            else -> "$n"
        }
    }
}
