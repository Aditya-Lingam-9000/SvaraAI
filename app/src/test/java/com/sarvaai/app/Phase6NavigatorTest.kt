package com.sarvaai.app

import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.menu.narrator.LayoutNarrator
import com.sarvaai.app.menu.navigation.NavigationAction
import com.sarvaai.app.menu.navigation.VoiceMenuNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6NavigatorTest {

    private val sampleSections = listOf(
        MenuSectionEntity(sectionId = "sec_tiffin", menuId = "menu_1", titleEnglish = "Tiffins", titleNative = "టిఫిన్స్", displayOrder = 1),
        MenuSectionEntity(sectionId = "sec_curry", menuId = "menu_1", titleEnglish = "Curries", titleNative = "కూరలు", displayOrder = 2)
    )

    private val sampleItems = mapOf(
        "sec_tiffin" to listOf(
            MenuItemEntity(itemId = "item_1", sectionId = "sec_tiffin", nameEnglish = "Masala Dosa", pricePaise = 8000, isVegetarian = true),
            MenuItemEntity(itemId = "item_2", sectionId = "sec_tiffin", nameEnglish = "Egg Dosa", pricePaise = 10000, isVegetarian = false)
        ),
        "sec_curry" to listOf(
            MenuItemEntity(itemId = "item_3", sectionId = "sec_curry", nameEnglish = "Paneer Butter Masala", pricePaise = 24000, isVegetarian = true)
        )
    )

    @Test
    fun testVoiceNavigatorNextAndOrderCommands() {
        val navigator = VoiceMenuNavigator(sampleSections, sampleItems)

        // Command "next" navigates to first item
        val act1 = navigator.handleVoiceCommand("next")
        assertTrue(act1 is NavigationAction.ReadItem)
        assertEquals("Masala Dosa", (act1 as NavigationAction.ReadItem).item.nameEnglish)

        // Telugu command "కావాలి" (I want this / add to order)
        val actOrder = navigator.handleVoiceCommand("నాకు ఇది కావాలి")
        assertTrue(actOrder is NavigationAction.AddCurrentToCart)
        assertEquals("Masala Dosa", (actOrder as NavigationAction.AddCurrentToCart).item.nameEnglish)

        // Command "next" moves to Egg Dosa
        val act2 = navigator.handleVoiceCommand("తర్వాత")
        assertTrue(act2 is NavigationAction.ReadItem)
        assertEquals("Egg Dosa", (act2 as NavigationAction.ReadItem).item.nameEnglish)
    }

    @Test
    fun testLayoutNarratorOverviewGeneration() {
        val summaryTe = LayoutNarrator.generateLayoutSummary("Sarva Bhavan", sampleSections, sampleItems, "te")
        assertTrue(summaryTe.contains("Sarva Bhavan"))
        assertTrue(summaryTe.contains("విభాగాలు ఉన్నాయి") || summaryTe.contains("టిఫిన్స్"))

        val summaryEn = LayoutNarrator.generateLayoutSummary("Sarva Bhavan", sampleSections, sampleItems, "en")
        assertTrue(summaryEn.contains("Sarva Bhavan"))
        assertTrue(summaryEn.contains("sections"))
    }
}
