package com.sarvaai.app.menu.narrator

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity

object LayoutNarrator {

    fun generateLayoutSummary(
        restaurantName: String,
        sections: List<MenuSectionEntity>,
        itemsBySection: Map<String, List<MenuItemEntity>>,
        language: String
    ): String {
        val totalSections = sections.size
        val totalItems = itemsBySection.values.sumOf { it.size }
        val vegItems = itemsBySection.values.flatten().count { it.isVegetarian }

        return when (language.lowercase()) {
            "hi" -> {
                val sectionNames = sections.joinToString(", ") { it.titleNative ?: it.titleEnglish }
                "$restaurantName के मेनू में $totalSections भाग और कुल $totalItems व्यंजन हैं, जिनमें $vegItems शाकाहारी हैं। " +
                        "भाग हैं: $sectionNames। आप किसी भी भाग को सुनने के लिए 'अगला' बोल सकते हैं।"
            }
            "te" -> {
                val sectionNames = sections.joinToString(", ") { it.titleNative ?: it.titleEnglish }
                "$restaurantName మెనూలో $totalSections విభాగాలు మరియు మొత్తం $totalItems వంటకాలు ఉన్నాయి. " +
                        "ఇందులో $vegItems శాఖాహార వంటకాలు ఉన్నాయి. " +
                        "విభాగాలు: $sectionNames. వినడానికి 'తర్వాత' అని చెప్పండి."
            }
            else -> {
                val sectionNames = sections.joinToString(", ") { it.titleEnglish }
                "This menu for $restaurantName has $totalSections sections with $totalItems items, including $vegItems vegetarian options. " +
                        "The sections are: $sectionNames. You can say 'next' to browse dishes or say a section name."
            }
        }
    }

    fun generateSectionSummary(
        section: MenuSectionEntity,
        items: List<MenuItemEntity>,
        language: String
    ): String {
        val count = items.size
        val minPrice = items.minOfOrNull { it.priceRupees } ?: 0L
        val maxPrice = items.maxOfOrNull { it.priceRupees } ?: 0L
        val title = section.titleNative ?: section.titleEnglish

        return when (language.lowercase()) {
            "hi" -> {
                val minPriceWords = NumberVerbalizer.verbalizePrice(minPrice, "hi")
                val maxPriceWords = NumberVerbalizer.verbalizePrice(maxPrice, "hi")
                "$title में $count व्यंजन हैं, जिनकी कीमत $minPriceWords से $maxPriceWords तक है।"
            }
            "te" -> {
                val minPriceWords = NumberVerbalizer.verbalizePrice(minPrice, "te")
                val maxPriceWords = NumberVerbalizer.verbalizePrice(maxPrice, "te")
                "$title లో $count వంటకాలు ఉన్నాయి, ధరలు $minPriceWords నుండి $maxPriceWords వరకు ఉన్నాయి."
            }
            else -> {
                val minPriceWords = NumberVerbalizer.verbalizePrice(minPrice, "en")
                val maxPriceWords = NumberVerbalizer.verbalizePrice(maxPrice, "en")
                "$title has $count items, ranging from $minPriceWords to $maxPriceWords."
            }
        }
    }

    fun generateItemSpokenDescription(
        item: MenuItemEntity,
        language: String
    ): String {
        val priceWords = NumberVerbalizer.verbalizePrice(item.priceRupees, language)
        val name = when (language.lowercase()) {
            "hi" -> item.nameHindi ?: item.nameEnglish
            "te" -> item.nameTelugu ?: item.nameEnglish
            else -> item.nameEnglish
        }

        val vegTag = if (item.isVegetarian) {
            when (language.lowercase()) {
                "hi" -> "शाकाहारी"
                "te" -> "శాఖాహారం"
                else -> "Vegetarian"
            }
        } else {
            when (language.lowercase()) {
                "hi" -> "मांसाहारी"
                "te" -> "మాంసాహారం"
                else -> "Non-vegetarian"
            }
        }

        val desc = item.description ?: ""

        return when (language.lowercase()) {
            "hi" -> "$name, $vegTag, कीमत $priceWords. $desc"
            "te" -> "$name, $vegTag, ధర $priceWords. $desc"
            else -> "$name, $vegTag, priced at $priceWords. $desc"
        }
    }
}
