package com.sarvaai.app

import com.sarvaai.app.adapt.adapter.TableNarrator
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.dialogue.dietary.DietaryCompliance
import com.sarvaai.app.dialogue.dietary.DietaryRuleEngine
import com.sarvaai.app.dialogue.explain.ExplainSimplerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase8DietaryExplainTest {

    private val alooGobi = MenuItemEntity(
        itemId = "item_aloo",
        sectionId = "sec_veg",
        nameEnglish = "Aloo Gobi",
        pricePaise = 18000L,
        isVegetarian = true,
        description = "Spiced potato and cauliflower dry curry"
    )

    private val chickenBiryani = MenuItemEntity(
        itemId = "item_chk",
        sectionId = "sec_nv",
        nameEnglish = "Chicken Biryani",
        pricePaise = 32000L,
        isVegetarian = false
    )

    private val shahiPaneer = MenuItemEntity(
        itemId = "item_shahi",
        sectionId = "sec_curry",
        nameEnglish = "Shahi Paneer",
        pricePaise = 26000L,
        isVegetarian = true,
        description = "Cottage cheese in rich cashew and cream gravy"
    )

    private val idliSambar = MenuItemEntity(
        itemId = "item_idli",
        sectionId = "sec_tiffin",
        nameEnglish = "Idli Sambar",
        pricePaise = 8000L,
        isVegetarian = true,
        isJain = true,
        isVegan = true,
        description = "Steamed rice cakes with lentil soup"
    )

    @Test
    fun testJainComplianceRules() {
        // Non-veg violates Jain
        val resNv = DietaryRuleEngine.checkJainCompliance(chickenBiryani, "en")
        assertEquals(DietaryCompliance.VIOLATION, resNv.compliance)

        // Aloo Gobi contains potato (root veg) -> violates Jain
        val resAloo = DietaryRuleEngine.checkJainCompliance(alooGobi, "en")
        assertEquals(DietaryCompliance.VIOLATION, resAloo.compliance)
        assertTrue(resAloo.identifiedIngredients.contains("potato") || resAloo.identifiedIngredients.contains("aloo"))

        // Explicitly marked Jain -> compliant
        val resIdli = DietaryRuleEngine.checkJainCompliance(idliSambar, "te")
        assertEquals(DietaryCompliance.COMPLIANT, resIdli.compliance)
        assertTrue(resIdli.explanation.contains("జైన్") || resIdli.explanation.contains("Idli"))
    }

    @Test
    fun testNutAllergyRules() {
        // Shahi Paneer contains cashew -> Violation or warning
        val resShahi = DietaryRuleEngine.checkNutAllergy(shahiPaneer, "en")
        assertEquals(DietaryCompliance.VIOLATION, resShahi.compliance)
        assertTrue(resShahi.identifiedIngredients.contains("cashew"))

        // Idli Sambar is nut free
        val resIdli = DietaryRuleEngine.checkNutAllergy(idliSambar, "hi")
        assertEquals(DietaryCompliance.COMPLIANT, resIdli.compliance)
    }

    @Test
    fun testVeganComplianceRules() {
        // Shahi Paneer contains paneer and cream -> Violation
        val resPaneer = DietaryRuleEngine.checkVeganCompliance(shahiPaneer, "en")
        assertEquals(DietaryCompliance.VIOLATION, resPaneer.compliance)
        assertTrue(resPaneer.identifiedIngredients.contains("paneer") || resPaneer.identifiedIngredients.contains("cream"))

        // Idli Sambar is 100% plant based vegan
        val resIdli = DietaryRuleEngine.checkVeganCompliance(idliSambar, "en")
        assertEquals(DietaryCompliance.COMPLIANT, resIdli.compliance)
    }

    @Test
    fun testExplainSimplerThreeTierLimits() {
        // Level 3 should be <= 20 words and ultra simple
        val expL3En = ExplainSimplerEngine.getInstantExplanation("Masala Dosa", 3, "en")
        val wordCountL3 = expL3En.split(" ").filter { it.isNotBlank() }.size
        assertTrue("Level 3 word count should be <= 20, got $wordCountL3", wordCountL3 <= 20)

        // Level 2 (Everyday)
        val expL2Te = ExplainSimplerEngine.getInstantExplanation("Masala Dosa", 2, "te")
        assertTrue(expL2Te.isNotBlank())

        // Level 1 (Culinary depth)
        val expL1Hi = ExplainSimplerEngine.getInstantExplanation("Idli", 1, "hi")
        assertTrue(expL1Hi.contains("दक्षिण भारतीय") || expL1Hi.contains("पारंपरिक") || expL1Hi.contains("नाश्ता"))
    }

    @Test
    fun testTableNarratorComparison() {
        val dishes = listOf(
            MenuItemEntity(itemId = "d1", sectionId = "s1", nameEnglish = "Masala Dosa", pricePaise = 12000L, isVegetarian = true, spiceLevel = 1),
            MenuItemEntity(itemId = "d2", sectionId = "s1", nameEnglish = "Chicken Biryani", pricePaise = 32000L, isVegetarian = false, spiceLevel = 3)
        )

        val table = TableNarrator.generateComparisonTable(dishes)
        assertEquals(2, table.rows.size)
        assertEquals("Masala Dosa", table.rows[0][0])
        assertEquals("₹120", table.rows[0][2])

        val narrationTe = TableNarrator.narrateComparison(table, "te")
        assertTrue(narrationTe.contains("Masala Dosa") && narrationTe.contains("Chicken Biryani"))

        val narrationEn = TableNarrator.narrateComparison(table, "en")
        assertTrue(narrationEn.contains("Masala Dosa") && narrationEn.contains("Chicken Biryani"))
    }
}
