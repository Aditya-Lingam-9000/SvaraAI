package com.sarvaai.app

import com.sarvaai.app.bhashini.ocr.BhashiniOcrClient
import com.sarvaai.app.menu.spatial.LocalSpatialOcr
import org.junit.Assert.*
import org.junit.Test

class MenuVisionOcrTest {

    @Test
    fun testBhashiniOcrEndpointsMappedCorrectly() {
        val endpoints = BhashiniOcrClient.LANGUAGE_OCR_ENDPOINTS
        assertTrue(endpoints.containsKey("en"))
        assertTrue(endpoints.containsKey("hi"))
        assertTrue(endpoints.containsKey("te"))
        assertTrue(endpoints.containsKey("ta"))
        assertTrue(endpoints.containsKey("kn"))
        assertTrue(endpoints.containsKey("mr"))
        assertTrue(endpoints.containsKey("gu"))
        assertTrue(endpoints.containsKey("bn"))
        assertTrue(endpoints.containsKey("pa"))
        assertTrue(endpoints.containsKey("or"))
        assertTrue(endpoints.containsKey("ml"))

        // Check English OCR endpoint
        assertEquals("https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cbf473b6e4905f682970", endpoints["en"])
        // Check Hindi OCR endpoint
        assertEquals("https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc3573b6e4905f682974", endpoints["hi"])
        // Check Telugu OCR endpoint
        assertEquals("https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ce2573b6e4905f682991", endpoints["te"])
    }

    @Test
    fun testDeterministicFallbackParserExtractsCategoriesAndDishes() {
        val sampleOcrText = """
        SARVA RESTAURANT
        BREAKFAST SPECIALS
        Plain Dosa ₹80
        Masala Dosa ₹120
        Idli Sambar ₹70
        
        MAIN COURSE CURRIES
        Paneer Butter Masala ₹240
        Dal Tadka ₹160
        Butter Naan ₹60
        
        BEVERAGES
        Filter Coffee ₹40
        Masala Tea ₹30
        """.trimIndent()

        val parsed = LocalSpatialOcr.parseDeterministicFallback(sampleOcrText)
        assertEquals(3, parsed.sections.size)

        // Section 1: BREAKFAST SPECIALS
        val breakfast = parsed.sections[0]
        assertTrue(breakfast.sectionName.contains("BREAKFAST", ignoreCase = true))
        assertEquals(3, breakfast.items.size)
        assertEquals("Plain Dosa", breakfast.items[0].name)
        assertEquals(80L, breakfast.items[0].price)
        assertEquals("Masala Dosa", breakfast.items[1].name)
        assertEquals(120L, breakfast.items[1].price)

        // Section 2: MAIN COURSE CURRIES
        val mainCourse = parsed.sections[1]
        assertTrue(mainCourse.sectionName.contains("MAIN", ignoreCase = true))
        assertEquals(3, mainCourse.items.size)
        assertEquals("Paneer Butter Masala", mainCourse.items[0].name)
        assertEquals(240L, mainCourse.items[0].price)

        // Section 3: BEVERAGES
        val beverages = parsed.sections[2]
        assertTrue(beverages.sectionName.contains("BEVERAGES", ignoreCase = true))
        assertEquals(2, beverages.items.size)
        assertEquals("Filter Coffee", beverages.items[0].name)
        assertEquals(40L, beverages.items[0].price)
    }

    @Test
    fun testNonVegAndDietaryClassification() {
        val menuText = """
        STARTERS
        Paneer Tikka ₹220
        Chicken 65 ₹280
        Egg Biryani ₹200
        Aloo Samosa ₹40
        """.trimIndent()

        val parsed = LocalSpatialOcr.parseDeterministicFallback(menuText)
        val items = parsed.sections.flatMap { it.items }

        val paneer = items.first { it.name == "Paneer Tikka" }
        assertTrue(paneer.isVegetarian)

        val chicken = items.first { it.name == "Chicken 65" }
        assertFalse(chicken.isVegetarian)

        val egg = items.first { it.name == "Egg Biryani" }
        assertFalse(egg.isVegetarian)

        val samosa = items.first { it.name == "Aloo Samosa" }
        assertTrue(samosa.isVegetarian)
        assertFalse(samosa.isJain) // Contains aloo / potato
    }
}
