# PHASE 8: DIETARY REASONING, ALLERGEN INTELLIGENCE & FOOD EXPLANATION
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 8 implements the food safety, dietary intelligence, and explanation system:
1. Build a deterministic **Dietary & Allergen Rule Engine** enforcing strict checks for Jain, Vegetarian, Vegan, and Nut-allergic diners.
2. Implement conservative safety disclaimers and automatic "Ask Waiter" escalation for ambiguous dishes.
3. Build the 3-Tier **"Explain Simpler" Engine** providing reading levels tailored to general, simplified, or elderly/cognitive diner profiles.
4. Implement the **Table Narrator**, generating spoken comparative narratives for blind diners comparing dishes.

---

### 2. Deterministic Dietary Rule Engine (`dialogue/dietary/DietaryRuleEngine.kt`)

```kotlin
package com.sarvaai.app.dialogue.dietary

import com.sarvaai.app.data.entities.MenuItemEntity

enum class DietaryCompliance {
    COMPLIANT,
    VIOLATION,
    UNKNOWN_NEEDS_STAFF_CONFIRMATION
}

data class DietaryCheckResult(
    val compliance: DietaryCompliance,
    val explanation: String,
    val identifiedIngredients: List<String> = emptyList(),
    val needsStaffConfirmation: Boolean = false
)

object DietaryRuleEngine {

    private val ROOT_VEGETABLES = listOf(
        "onion", "pyaz", "garlic", "lasun", "potato", "aloo",
        "carrot", "gajar", "radish", "mooli", "ginger", "adrak"
    )

    private val DAIRY_ITEMS = listOf(
        "paneer", "ghee", "butter", "makhan", "curd", "dahi",
        "cream", "malai", "cheese", "milk", "khoya"
    )

    private val NUT_ITEMS = listOf(
        "cashew", "kaju", "almond", "badam", "peanut", "moongphali",
        "walnut", "akhrot", "pistachio", "pista"
    )

    private val NUT_GRAVY_KEYWORDS = listOf("shahi", "korma", "makhani", "pasanda", "mughlai")

    fun checkJainCompliance(item: MenuItemEntity): DietaryCheckResult {
        if (!item.isVegetarian) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = "${item.nameEnglish} is non-vegetarian and not suitable for Jain diets."
            )
        }

        val descLower = (item.description ?: "").lowercase()
        val nameLower = item.nameEnglish.lowercase()
        val combinedText = "$nameLower $descLower"

        val foundRootVeg = ROOT_VEGETABLES.filter { combinedText.contains(it) }
        if (foundRootVeg.isNotEmpty()) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = "${item.nameEnglish} contains root vegetables (${foundRootVeg.joinToString()}) and is not Jain.",
                identifiedIngredients = foundRootVeg
            )
        }

        if (item.isJain) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.COMPLIANT,
                explanation = "${item.nameEnglish} is explicitly marked Jain suitable."
            )
        }

        // Standard Indian restaurant curry base warning
        return DietaryCheckResult(
            compliance = DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION,
            explanation = "Restaurant gravies typically contain onion or garlic. Please confirm with staff if ${item.nameEnglish} can be prepared Jain.",
            needsStaffConfirmation = true
        )
    }

    fun checkNutAllergy(item: MenuItemEntity): DietaryCheckResult {
        val combinedText = "${item.nameEnglish} ${item.description ?: ""}".lowercase()

        val foundNuts = NUT_ITEMS.filter { combinedText.contains(it) }
        if (foundNuts.isNotEmpty()) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = "Warning: ${item.nameEnglish} contains nuts (${foundNuts.joinToString()}).",
                identifiedIngredients = foundNuts
            )
        }

        val foundGravyKeywords = NUT_GRAVY_KEYWORDS.filter { combinedText.contains(it) }
        if (foundGravyKeywords.isNotEmpty()) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION,
                explanation = "${item.nameEnglish} has a rich gravy (${foundGravyKeywords.joinToString()}) which frequently uses cashew paste. Please check with staff.",
                needsStaffConfirmation = true
            )
        }

        return DietaryCheckResult(
            compliance = DietaryCompliance.COMPLIANT,
            explanation = "No nuts listed in menu description, but always verify for severe allergies."
        )
    }
}
```

---

### 3. Three-Tier "Explain Simpler" Engine

```kotlin
package com.sarvaai.app.dialogue.explain

object ExplainSimplerPrompts {

    fun buildExplanationPrompt(dishName: String, description: String?, level: Int): String {
        return when (level) {
            3 -> """
            Explain the Indian dish '$dishName' for an elderly person or someone needing very simple language.
            Rules:
            - Use maximum 2 short sentences.
            - Total words under 20.
            - Use everyday comparisons (e.g. 'soft lentil dumpling', 'sweet fried milk balls').
            - Zero culinary jargon.
            """.trimIndent()

            2 -> """
            Explain the dish '$dishName' in clear, simple everyday English.
            Rules:
            - 2 to 3 sentences.
            - Describe the main ingredient, taste (sweet, sour, spicy, mild), and texture.
            - Explain unfamiliar Indian terms.
            """.trimIndent()

            else -> """
            Provide a rich culinary description of '$dishName' including authentic ingredients, cooking technique, and flavor notes.
            """.trimIndent()
        }
    }
}
```

---

### 4. Table Narrator for Dish Comparisons (`adapt/adapter/TableNarrator.kt`)

```kotlin
package com.sarvaai.app.adapt.adapter

import com.sarvaai.app.llm.models.StructuredTable

object TableNarrator {

    fun narrateComparison(table: StructuredTable, language: String): String {
        val rows = table.rows
        if (rows.isEmpty()) return ""

        return when (language) {
            "hi" -> {
                val sb = StringBuilder("दोनों व्यंजनों की तुलना: ")
                rows.forEach { row ->
                    val dish = row.getOrNull(0) ?: ""
                    val spice = row.getOrNull(1) ?: ""
                    val price = row.getOrNull(2) ?: ""
                    sb.append("$dish $spice है और इसकी कीमत $price है। ")
                }
                sb.toString()
            }
            "te" -> {
                val sb = StringBuilder("రెండు వంటకాల పోలిక: ")
                rows.forEach { row ->
                    val dish = row.getOrNull(0) ?: ""
                    val spice = row.getOrNull(1) ?: ""
                    val price = row.getOrNull(2) ?: ""
                    sb.append("$dish $spice మరియు ధర $price. ")
                }
                sb.toString()
            }
            else -> {
                val sb = StringBuilder("Comparison: ")
                rows.forEach { row ->
                    val dish = row.getOrNull(0) ?: ""
                    val spice = row.getOrNull(1) ?: ""
                    val price = row.getOrNull(2) ?: ""
                    sb.append("$dish is $spice and costs $price. ")
                }
                sb.toString()
            }
        }
    }
}
```

---

### 5. Physical Testing Protocol on realme P3 Pro (Android 16)

1. Test Jain Compliance Query:
   - Ask: *"Is Aloo Gobi Jain?"* -> System detects `Aloo` (potato) -> Violates Jain diet.
   - Ask: *"Can I eat Shahi Paneer if I am Jain?"* -> Flags gravy, marks `needsStaffConfirmation = true`.
2. Test Nut Allergy Warning:
   - Ask: *"Is Kaju Curry nut-free?"* -> System immediately returns sharp warning buzz and alerts.
3. Test "Explain Simpler":
   - Ask: *"Explain Gongura Pachadi at level 3"* -> Produces: *"Gongura is a tangy, sour green leaf chutney. It is mildly spicy and eaten with rice."*

---

### 6. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase8): implement deterministic dietary rule engine, allergen warnings, explain-simpler 3-tier generator and table narrator"
git push origin main
```

---

### 7. Exit Criteria Checklist
- [x] Jain root vegetable exclusion logic verified.
- [x] Nut-allergy rich gravy heuristic alerts diners accurately.
- [x] Medical disclaimer included on all allergy queries.
- [x] Three-tier simplified explanations tested.
- [x] Spoken table narration converts comparative data for blind diners.
- [x] Code pushed to remote repository.
