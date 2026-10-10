package com.sarvaai.app.adapt.adapter

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.llm.models.StructuredTable

object TableNarrator {

    fun generateComparisonTable(dishes: List<MenuItemEntity>): StructuredTable {
        val columns = listOf("Dish Name", "Diet & Spice", "Price (INR)")
        val rows = dishes.map { dish ->
            val dietStr = buildString {
                append(if (dish.isVegetarian) "Pure Veg" else "Non-Veg")
                append(", ")
                append(when (dish.spiceLevel) {
                    0 -> "Mild"
                    1 -> "Medium Spice"
                    2 -> "Spicy"
                    else -> "Very Spicy"
                })
            }
            listOf(dish.nameEnglish, dietStr, "₹${dish.priceRupees}")
        }
        return StructuredTable(columns = columns, rows = rows)
    }

    fun narrateComparison(table: StructuredTable, language: String): String {
        val rows = table.rows
        if (rows.isEmpty()) return ""

        return when (language.lowercase()) {
            "hi" -> {
                val sb = StringBuilder("व्यंजनों की तुलना: ")
                rows.forEachIndexed { idx, row ->
                    val dish = row.getOrNull(0) ?: "व्यंजन"
                    val dietAndSpice = row.getOrNull(1) ?: "मध्यम"
                    val price = row.getOrNull(2) ?: "0"
                    val numOnly = price.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                    val priceWords = NumberVerbalizer.verbalizePrice(numOnly, "hi")
                    sb.append("${idx + 1}. $dish ($dietAndSpice), जिसकी कीमत $priceWords है। ")
                }
                sb.toString().trim()
            }

            "te" -> {
                val sb = StringBuilder("వంటకాల పోలిక: ")
                rows.forEachIndexed { idx, row ->
                    val dish = row.getOrNull(0) ?: "వంటకం"
                    val dietAndSpice = row.getOrNull(1) ?: "మధ్యస్థం"
                    val price = row.getOrNull(2) ?: "0"
                    val numOnly = price.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                    val priceWords = NumberVerbalizer.verbalizePrice(numOnly, "te")
                    sb.append("${idx + 1}. $dish ($dietAndSpice), దీని ధర $priceWords. ")
                }
                sb.toString().trim()
            }

            else -> {
                val sb = StringBuilder("Dish Comparison: ")
                rows.forEachIndexed { idx, row ->
                    val dish = row.getOrNull(0) ?: "Dish"
                    val dietAndSpice = row.getOrNull(1) ?: "Mild"
                    val price = row.getOrNull(2) ?: "0"
                    val numOnly = price.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                    val priceWords = NumberVerbalizer.verbalizePrice(numOnly, "en")
                    sb.append("${idx + 1}. $dish is $dietAndSpice, priced at $priceWords. ")
                }
                sb.toString().trim()
            }
        }
    }
}
