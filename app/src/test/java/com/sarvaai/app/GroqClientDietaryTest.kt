package com.sarvaai.app

import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.llm.GroqClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqClientDietaryTest {

    private val groqClient = GroqClient(context = null)

    private val sampleSections = listOf(
        MenuSectionEntity(sectionId = "s1", menuId = "m1", titleEnglish = "Starters", displayOrder = 1),
        MenuSectionEntity(sectionId = "s2", menuId = "m1", titleEnglish = "Main Course", displayOrder = 2),
        MenuSectionEntity(sectionId = "s3", menuId = "m1", titleEnglish = "South Indian Tiffins", displayOrder = 3)
    )

    private val sampleMenuItems = listOf(
        MenuItemEntity(
            itemId = "item_1",
            sectionId = "s2",
            nameEnglish = "Paneer Butter Masala",
            pricePaise = 28000L,
            isVegetarian = true,
            isJain = false,
            isVegan = false,
            isNutFree = false,
            spiceLevel = 1,
            description = "Cottage cheese cubes in a rich tomato, butter, and cashew nut gravy"
        ),
        MenuItemEntity(
            itemId = "item_2",
            sectionId = "s3",
            nameEnglish = "Masala Dosa",
            pricePaise = 12000L,
            isVegetarian = true,
            isJain = false,
            isVegan = false,
            isNutFree = true,
            spiceLevel = 1,
            description = "Crispy fermented rice crepe stuffed with spiced potato masala"
        ),
        MenuItemEntity(
            itemId = "item_3",
            sectionId = "s3",
            nameEnglish = "Jain Plain Dosa",
            pricePaise = 10000L,
            isVegetarian = true,
            isJain = true,
            isVegan = true,
            isNutFree = true,
            spiceLevel = 0,
            description = "Crispy rice crepe made without onion, garlic, or root vegetables"
        ),
        MenuItemEntity(
            itemId = "item_4",
            sectionId = "s2",
            nameEnglish = "Dal Makhani",
            pricePaise = 22000L,
            isVegetarian = true,
            isJain = false,
            isVegan = false,
            isNutFree = true,
            spiceLevel = 1,
            description = "Slow cooked black lentils simmered overnight with butter and fresh cream"
        ),
        MenuItemEntity(
            itemId = "item_5",
            sectionId = "s1",
            nameEnglish = "Vegan Hara Bhara Kebab",
            pricePaise = 18000L,
            isVegetarian = true,
            isJain = false,
            isVegan = true,
            isNutFree = true,
            spiceLevel = 1,
            description = "Spinach and green pea patties shallow fried with aromatic spices"
        )
    )

    @Test
    fun testPaneerButterMasalaNutsAndVegQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Is the paneer butter masala vegetarian or does it contain nuts / cashews?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Paneer Butter Masala"))
        assertTrue(response.spoken.contains("cashew") || response.spoken.contains("kaju"))
        assertTrue(response.needsStaffConfirmation)
        val actionTypes = response.actions.map { it.actionType }
        assertTrue(actionTypes.contains("ANSWER_DIETARY") || actionTypes.contains("HIGHLIGHT_ITEM"))
    }

    @Test
    fun testMasalaDosaJainQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Is masala dosa Jain friendly? Does it have onion or garlic?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Masala Dosa"))
        assertTrue(response.spoken.contains("onion") || response.spoken.contains("garlic") || response.spoken.contains("potato"))
        assertTrue(response.needsStaffConfirmation)
        val actionTypes = response.actions.map { it.actionType }
        assertTrue(actionTypes.contains("HIGHLIGHT_ITEM") || actionTypes.contains("ASK_WAITER"))
    }

    @Test
    fun testWhichDishesAreJainQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Which dishes here are Jain?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Jain Plain Dosa"))
        assertNotNull(response.table)
        val filterAction = response.actions.firstOrNull { it.actionType == "FILTER_JAIN" }
        assertNotNull(filterAction)
        assertEquals("jain", filterAction?.payload)
    }

    @Test
    fun testWhichDishesAreNutFreeQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Which dishes here are nut-free?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertNotNull(response.table)
        val filterAction = response.actions.firstOrNull { it.actionType == "FILTER_NUT_FREE" }
        assertNotNull(filterAction)
        assertEquals("nut_free", filterAction?.payload)
    }

    @Test
    fun testVeganStartersQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What starters are vegan?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Vegan Hara Bhara Kebab") || response.spoken.contains("Jain Plain Dosa"))
        val filterAction = response.actions.firstOrNull { it.actionType == "FILTER_VEG" }
        assertNotNull(filterAction)
        assertEquals("vegan", filterAction?.payload)
    }

    @Test
    fun testDalMakhaniDairyQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Does dal makhani contain dairy or butter?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Dal Makhani"))
        assertTrue(response.spoken.contains("dairy") || response.spoken.contains("butter"))
        val ansAction = response.actions.firstOrNull { it.actionType == "ANSWER_DIETARY" }
        assertNotNull(ansAction)
    }

    @Test
    fun testAddToCartIntentWithinDietaryQa() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Add Paneer Butter Masala to cart",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("ORDER_ITEMS", response.intent)
        val addAction = response.actions.firstOrNull { it.actionType == "ADD_TO_CART" }
        assertNotNull(addAction)
        assertEquals("Paneer Butter Masala:1:", addAction?.payload)
    }

    @Test
    fun testComprehensiveAskDietaryQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Ask: is Paneer Butter Masala vegetarian, Jain or nut-free?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertNotNull(response.table)
        val actionTypes = response.actions.map { it.actionType }
        assertTrue(actionTypes.contains("ANSWER_DIETARY"))
        assertTrue(actionTypes.contains("HIGHLIGHT_ITEM"))
    }

    @Test
    fun testMultilingualTeluguDietaryQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "పనీర్ బటర్ మసాలా లో కాజు లేదా నట్స్ ఉన్నాయా?",
            language = "te",
            restaurantName = "స్వాగత్ గ్రాండ్",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("జీడిపప్పు") || response.spoken.contains("కాజు") || response.spoken.contains("Paneer Butter Masala"))
        assertTrue(response.needsStaffConfirmation)
    }

    @Test
    fun testMultilingualHindiDietaryQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "क्या दाल मखनी में मक्खन या डेयरी है?",
            language = "hi",
            restaurantName = "स्वाद रेस्टोरेंट",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("मक्खन") || response.spoken.contains("Dal Makhani") || response.spoken.contains("डेयरी"))
    }

    @Test
    fun testCulinaryExplanationChilliChicken() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is chilli chicken?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Indo-Chinese") || response.spoken.contains("crispy fried chicken"))
        assertTrue(response.spoken.contains("green chilies") || response.spoken.contains("soy sauce"))
        assertNotNull(response.table)
    }

    @Test
    fun testCulinaryExplanationChicken65() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is chicken 65?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Chennai") || response.spoken.contains("South Indian"))
        assertTrue(response.spoken.contains("curry leaves") || response.spoken.contains("fiery"))
    }

    @Test
    fun testCulinaryExplanationBiryaniPreparation() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "How is biryani prepared?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("basmati") || response.spoken.contains("slow-cooked") || response.spoken.contains("dum"))
    }

    @Test
    fun testCulinaryExplanationRasam() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is rasam?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("tamarind") || response.spoken.contains("black pepper") || response.spoken.contains("soup"))
    }

    @Test
    fun testCulinaryExplanationFishCurrySpiciness() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Is fish curry spicy?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.contains("Fish Curry") || response.spoken.contains("coastal"))
        assertTrue(response.spoken.contains("2/3") || response.spoken.contains("spicy") || response.spoken.contains("tamarind"))
    }

    @Test
    fun testCulinaryExplanationMultilingualTelugu() {
        // Telugu: Chilli Chicken & Rasam
        val resTe1 = groqClient.generateLocalDietaryResponse(
            userQuery = "చిల్లీ చికెన్ అంటే ఏమిటి?",
            language = "te",
            restaurantName = "స్వాగత్",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals("DIETARY_QUERY", resTe1.intent)
        assertTrue(resTe1.spoken.contains("ఇండో-చైనీస్") || resTe1.spoken.contains("చికెన్"))

        val resTe2 = groqClient.generateLocalDietaryResponse(
            userQuery = "రసం అంటే ఏమిటి?",
            language = "te",
            restaurantName = "స్వాగత్",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals("DIETARY_QUERY", resTe2.intent)
        assertTrue(resTe2.spoken.contains("చింతపండు") || resTe2.spoken.contains("మిరియాలు") || resTe2.spoken.contains("రసం"))
    }

    @Test
    fun testCulinaryExplanationMultilingualHindi() {
        // Hindi: Chilli Chicken & Biryani
        val resHi1 = groqClient.generateLocalDietaryResponse(
            userQuery = "चिल्ली चिकन क्या है?",
            language = "hi",
            restaurantName = "स्वाद",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals("DIETARY_QUERY", resHi1.intent)
        assertTrue(resHi1.spoken.contains("इंडो-चाइनीज") || resHi1.spoken.contains("चिकन"))

        val resHi2 = groqClient.generateLocalDietaryResponse(
            userQuery = "बिरयानी कैसे बनती है?",
            language = "hi",
            restaurantName = "स्वाद",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals("DIETARY_QUERY", resHi2.intent)
        assertTrue(resHi2.spoken.contains("बासमती") || resHi2.spoken.contains("दम") || resHi2.spoken.contains("बिरयानी"))
    }

    @Test
    fun testStrictGuardrailsForNonFoodQueries() {
        // English non-food coding query
        val resCodeEn = groqClient.generateLocalDietaryResponse(
            userQuery = "Write a python function to sort an array",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals(GroqClient.REDIRECTION_EN, resCodeEn.spoken)

        // English non-food math query
        val resMathEn = groqClient.generateLocalDietaryResponse(
            userQuery = "What is the square root of 144?",
            language = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals(GroqClient.REDIRECTION_EN, resMathEn.spoken)

        // Hindi politics non-food query
        val resPolHi = groqClient.generateLocalDietaryResponse(
            userQuery = "प्रधानमंत्री कौन है?",
            language = "hi",
            restaurantName = "स्वाद",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals(GroqClient.REDIRECTION_HI, resPolHi.spoken)

        // Telugu election non-food query
        val resElecTe = groqClient.generateLocalDietaryResponse(
            userQuery = "రాష్ట్రంలో ఎన్నికలు ఎప్పుడు?",
            language = "te",
            restaurantName = "స్వాగత్",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )
        assertEquals(GroqClient.REDIRECTION_TE, resElecTe.spoken)

        // Multilingual response method guardrail
        val resMulti = groqClient.generateLocalMultilingualResponse("Write javascript code for login", "en")
        assertEquals(GroqClient.REDIRECTION_EN, resMulti.spoken)
    }

    @Test
    fun testProcessMenuDietaryQaCoroutineExecution() = runBlocking {
        val result = groqClient.processMenuDietaryQa(
            userQuery = "Is the paneer butter masala vegetarian or does it contain nuts / cashews?",
            userLanguage = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertTrue(result is Result.Success)
        val structured = (result as Result.Success).data
        assertEquals("DIETARY_QUERY", structured.intent)
        assertTrue(structured.spoken.isNotBlank())
        assertTrue(structured.actions.isNotEmpty())
    }

    @Test
    fun testProcessMenuDietaryQaDishQueryChilliChicken() = runBlocking {
        val result = groqClient.processMenuDietaryQa(
            userQuery = "What is chilli chicken?",
            userLanguage = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertTrue(result is Result.Success)
        val structured = (result as Result.Success).data
        assertEquals("DIETARY_QUERY", structured.intent)
        assertTrue(structured.spoken.isNotBlank())
        assertTrue(structured.headline.isNotBlank())
    }

    @Test
    fun testProcessMenuDietaryQaDishQueryPesarattu() = runBlocking {
        val result = groqClient.processMenuDietaryQa(
            userQuery = "What is Pesarattu?",
            userLanguage = "en",
            restaurantName = "Saravana Bhavan",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertTrue(result is Result.Success)
        val structured = (result as Result.Success).data
        assertEquals("DIETARY_QUERY", structured.intent)
        assertTrue(structured.spoken.isNotBlank())
    }
}

