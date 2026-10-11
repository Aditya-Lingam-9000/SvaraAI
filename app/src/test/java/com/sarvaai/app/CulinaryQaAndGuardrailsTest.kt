package com.sarvaai.app

import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.llm.GroqClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CulinaryQaAndGuardrailsTest {

    private val groqClient = GroqClient(context = null)

    private val sampleSections = listOf(
        MenuSectionEntity(sectionId = "sec_starters", menuId = "m1", titleEnglish = "Starters", displayOrder = 1),
        MenuSectionEntity(sectionId = "sec_mains", menuId = "m1", titleEnglish = "Main Course", displayOrder = 2),
        MenuSectionEntity(sectionId = "sec_south", menuId = "m1", titleEnglish = "South Indian Tiffins", displayOrder = 3)
    )

    private val sampleMenuItems = listOf(
        MenuItemEntity(
            itemId = "item_chilli_chicken",
            sectionId = "sec_starters",
            nameEnglish = "Chilli Chicken",
            nameTelugu = "చిల్లీ చికెన్",
            nameHindi = "चिली चिकन",
            pricePaise = 26000L,
            isVegetarian = false,
            isJain = false,
            isVegan = false,
            isNutFree = true,
            spiceLevel = 2,
            description = "Crispy fried chicken tossed with green chilies, capsicum, soy sauce, and garlic"
        ),
        MenuItemEntity(
            itemId = "item_butter_chicken",
            sectionId = "sec_mains",
            nameEnglish = "Butter Chicken",
            nameTelugu = "బటర్ చికెన్",
            nameHindi = "बटर चिकन",
            pricePaise = 32000L,
            isVegetarian = false,
            isJain = false,
            isVegan = false,
            isNutFree = false,
            spiceLevel = 1,
            description = "Tandoori roasted chicken in a rich tomato, butter, fresh cream, and cashew nut gravy"
        ),
        MenuItemEntity(
            itemId = "item_paneer_butter_masala",
            sectionId = "sec_mains",
            nameEnglish = "Paneer Butter Masala",
            nameTelugu = "పనీర్ బటర్ మసాలా",
            nameHindi = "पनीर बटर मसाला",
            pricePaise = 28000L,
            isVegetarian = true,
            isJain = false,
            isVegan = false,
            isNutFree = false,
            spiceLevel = 1,
            description = "Cottage cheese cubes simmered in a creamy tomato, butter, and cashew gravy"
        ),
        MenuItemEntity(
            itemId = "item_biryani",
            sectionId = "sec_mains",
            nameEnglish = "Hyderabadi Dum Biryani",
            nameTelugu = "హైదరాబాదీ దమ్ బిర్యానీ",
            nameHindi = "हैदराबादी दम बिरयानी",
            pricePaise = 30000L,
            isVegetarian = false,
            isJain = false,
            isVegan = false,
            isNutFree = true,
            spiceLevel = 2,
            description = "Fragrant basmati rice layered with spiced meat, saffron, mint, and slow-cooked in dum style"
        ),
        MenuItemEntity(
            itemId = "item_rasam",
            sectionId = "sec_south",
            nameEnglish = "Traditional Rasam",
            nameTelugu = "సాంప్రదాయ రసం",
            nameHindi = "पारंपरिक रसम",
            pricePaise = 8000L,
            isVegetarian = true,
            isJain = true,
            isVegan = true,
            isNutFree = true,
            spiceLevel = 2,
            description = "Tangy South Indian peppery soup made with tamarind, tomatoes, cumin, and curry leaves"
        ),
        MenuItemEntity(
            itemId = "item_jain_dosa",
            sectionId = "sec_south",
            nameEnglish = "Jain Plain Dosa",
            nameTelugu = "జైన్ సాదా దోశ",
            nameHindi = "जैन सादा डोसा",
            pricePaise = 11000L,
            isVegetarian = true,
            isJain = true,
            isVegan = true,
            isNutFree = true,
            spiceLevel = 0,
            description = "Crispy rice crepe prepared without onion, garlic, or root vegetables"
        )
    )

    // =========================================================================
    // 1. CHILLI CHICKEN CULINARY QA (English, Telugu, Hindi)
    // =========================================================================

    @Test
    fun testWhatIsChilliChickenInEnglish() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is chilli chicken?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertNotNull(response.spoken)
        assertTrue("Spoken must not be blank", response.spoken.isNotBlank())
        assertNotNull(response.caption)

        val spokenLower = response.spoken.lowercase()
        assertTrue("Must describe non-vegetarian classification", spokenLower.contains("non-veg") || spokenLower.contains("non-vegetarian") || spokenLower.contains("chicken"))
        assertTrue("Must mention Indo-Chinese origin or style", spokenLower.contains("indo-chinese") || spokenLower.contains("chinese"))
        assertTrue("Must mention chicken or crispy fried chicken", spokenLower.contains("chicken"))
        assertTrue("Must describe spicy or chili or savory flavor", spokenLower.contains("spic") || spokenLower.contains("chili") || spokenLower.contains("sauce") || spokenLower.contains("garlic"))

        // Verify dietary actions contain ANSWER_DIETARY
        val actions = response.actions
        assertTrue("Must return UI actions", actions.isNotEmpty())
        val actionTypes = actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))

        // Table verification
        assertNotNull(response.table)
        val table = response.table
        assertTrue("Table must have rows", table != null && table.rows.isNotEmpty())
    }

    @Test
    fun testWhatIsChilliChickenInTelugu() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "చిల్లీ చికెన్ అంటే ఏమిటి?",
            language = "te",
            restaurantName = "స్వాగత్ గ్రాండ్",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertNotNull(response.spoken)
        assertTrue(response.spoken.isNotBlank())

        val spoken = response.spoken
        assertTrue("Spoken must contain Telugu culinary terms", spoken.contains("చిల్లీ చికెన్") || spoken.contains("చికెన్"))
        assertTrue("Spoken must indicate non-veg or style", spoken.contains("మాంసాహార") || spoken.contains("ఇండో-చైనీస్") || spoken.contains("స్పైసీ") || spoken.contains("క్రిస్పీ"))

        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))
    }

    @Test
    fun testWhatIsChilliChickenInHindi() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "चिली चिकन क्या है?",
            language = "hi",
            restaurantName = "स्वाद रेस्टोरेंट",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        assertNotNull(response.spoken)
        assertTrue(response.spoken.isNotBlank())

        val spoken = response.spoken
        assertTrue("Spoken must contain Hindi culinary terms", spoken.contains("चिकन") || spoken.contains("चिल्ली") || spoken.contains("चिली"))
        assertTrue("Spoken must indicate non-veg or Indo-Chinese style", spoken.contains("मांसाहारी") || spoken.contains("इंडो-चाइनीज") || spoken.contains("तीखा"))

        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))
    }

    // =========================================================================
    // 2. BUTTER CHICKEN, BIRYANI, PANEER BUTTER MASALA, RASAM
    // =========================================================================

    @Test
    fun testWhatIsButterChicken() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is butter chicken?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val spokenLower = response.spoken.lowercase()
        assertTrue("Must mention Butter Chicken or Murgh Makhani", spokenLower.contains("butter chicken") || spokenLower.contains("murgh makhani") || spokenLower.contains("chicken"))
        assertTrue("Must mention rich creamy tomato/butter/cashew gravy", spokenLower.contains("butter") || spokenLower.contains("cream") || spokenLower.contains("tomato") || spokenLower.contains("cashew"))
        assertTrue("Must mention North Indian or tandoori", spokenLower.contains("north indian") || spokenLower.contains("tandoori") || spokenLower.contains("punjabi"))

        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))
    }

    @Test
    fun testTellMeAboutBiryani() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Tell me about biryani",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val spokenLower = response.spoken.lowercase()
        assertTrue("Must mention Biryani", spokenLower.contains("biryani"))
        assertTrue("Must mention basmati rice", spokenLower.contains("rice") || spokenLower.contains("basmati"))
        assertTrue("Must mention spices or dum method", spokenLower.contains("dum") || spokenLower.contains("spice") || spokenLower.contains("saffron") || spokenLower.contains("aromatic"))

        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))
    }

    @Test
    fun testWhatIsPaneerButterMasala() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is paneer butter masala?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val spokenLower = response.spoken.lowercase()
        assertTrue("Must mention Paneer Butter Masala", spokenLower.contains("paneer butter masala") || spokenLower.contains("paneer"))
        assertTrue("Must mention vegetarian", spokenLower.contains("vegetarian") || spokenLower.contains("cottage cheese"))
        assertTrue("Must mention butter/cream/cashew gravy", spokenLower.contains("butter") || spokenLower.contains("cream") || spokenLower.contains("cashew") || spokenLower.contains("tomato"))

        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))
    }

    @Test
    fun testWhatIsRasam() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "What is rasam?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val spokenLower = response.spoken.lowercase()
        assertTrue("Must mention Rasam", spokenLower.contains("rasam"))
        assertTrue("Must mention South Indian soup / tamarind / pepper", spokenLower.contains("south indian") || spokenLower.contains("soup") || spokenLower.contains("tamarind") || spokenLower.contains("pepper"))
        assertTrue("Must mention digestive or vegan", spokenLower.contains("digestive") || spokenLower.contains("vegan") || spokenLower.contains("tangy") || spokenLower.contains("spic"))

        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Actions must include ANSWER_DIETARY", actionTypes.contains("ANSWER_DIETARY"))
    }

    // =========================================================================
    // 3. FOOD GUARDRAILS: NON-FOOD QUERY REDIRECTION
    // =========================================================================

    @Test
    fun testFoodGuardrailsNonFoodQueriesInEnglish() {
        val nonFoodQueries = listOf(
            "Who is the president?",
            "Who is the president of India?",
            "Write python code",
            "How to write a python script?",
            "What is the weather today?",
            "What is the capital of France?",
            "Solve 2 + 2"
        )

        for (query in nonFoodQueries) {
            assertTrue("Query '$query' must be identified as non-food", GroqClient.isNonFoodQuery(query))

            // Test in generateLocalMultilingualResponse
            val responseMulti = groqClient.generateLocalMultilingualResponse(query, "en")
            val spokenLower = responseMulti.spoken.lowercase()
            assertTrue("Response for '$query' must mention restaurant or food assistant",
                spokenLower.contains("restaurant") || spokenLower.contains("dishes") || spokenLower.contains("menu") || spokenLower.contains("order"))
            assertFalse("Non-food query must NOT result in ADD_TO_CART", responseMulti.actions.any { it.actionType == "ADD_TO_CART" })

            // Test in generateLocalDietaryResponse
            val responseDietary = groqClient.generateLocalDietaryResponse(query, "en", "Sarva Feast", sampleMenuItems, sampleSections)
            val dietarySpokenLower = responseDietary.spoken.lowercase()
            assertTrue("Dietary response for '$query' must redirect to food assistant",
                dietarySpokenLower.contains("restaurant") || dietarySpokenLower.contains("dishes") || dietarySpokenLower.contains("menu") || dietarySpokenLower.contains("order"))
            assertFalse("Non-food query must NOT result in ADD_TO_CART", responseDietary.actions.any { it.actionType == "ADD_TO_CART" })
        }
    }

    @Test
    fun testFoodGuardrailsInTeluguAndHindi() {
        val teluguQuery = "ప్రెసిడెంట్ ఎవరు?"
        assertTrue("Telugu president query must be identified as non-food", GroqClient.isNonFoodQuery(teluguQuery))
        val teluguResponse = groqClient.generateLocalMultilingualResponse(teluguQuery, "te")
        assertTrue("Telugu guardrail response must mention restaurant or menu",
            teluguResponse.spoken.contains("రెస్టారెంట్") || teluguResponse.spoken.contains("ఆహారం") || teluguResponse.spoken.contains("మెనూ"))
        assertFalse("Must NOT add to cart", teluguResponse.actions.any { it.actionType == "ADD_TO_CART" })

        val hindiQuery = "पायथन कोड लिखो"
        assertTrue("Hindi coding query must be identified as non-food", GroqClient.isNonFoodQuery(hindiQuery))
        val hindiResponse = groqClient.generateLocalMultilingualResponse(hindiQuery, "hi")
        assertTrue("Hindi guardrail response must mention restaurant or menu",
            hindiResponse.spoken.contains("रेस्तरां") || hindiResponse.spoken.contains("भोजन") || hindiResponse.spoken.contains("मेनू"))
        assertFalse("Must NOT add to cart", hindiResponse.actions.any { it.actionType == "ADD_TO_CART" })
    }

    // =========================================================================
    // 4. DIETARY ACTIONS (FILTER_VEG, FILTER_JAIN, ANSWER_DIETARY)
    // =========================================================================

    @Test
    fun testDietaryActionFilterVeg() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Show vegetarian dishes only",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val filterVegAction = response.actions.firstOrNull { it.actionType == "FILTER_VEG" }
        assertNotNull("Must return FILTER_VEG action", filterVegAction)
        assertEquals("vegetarian", filterVegAction?.payload)
    }

    @Test
    fun testDietaryActionFilterJain() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Which dishes here are Jain friendly?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val filterJainAction = response.actions.firstOrNull { it.actionType == "FILTER_JAIN" }
        assertNotNull("Must return FILTER_JAIN action", filterJainAction)
        assertEquals("jain", filterJainAction?.payload)
    }

    @Test
    fun testDietaryActionAnswerDietary() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Does paneer butter masala contain nuts or cashews?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val answerAction = response.actions.firstOrNull { it.actionType == "ANSWER_DIETARY" }
        assertNotNull("Must return ANSWER_DIETARY action", answerAction)
        assertTrue("Spoken must alert about cashews or nuts", response.spoken.lowercase().contains("cashew") || response.spoken.lowercase().contains("kaju") || response.spoken.lowercase().contains("nut"))
    }

    @Test
    fun testIsThisVegetarianQuery() {
        val response = groqClient.generateLocalDietaryResponse(
            userQuery = "Is Chilli Chicken vegetarian?",
            language = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertEquals("DIETARY_QUERY", response.intent)
        val spokenLower = response.spoken.lowercase()
        assertTrue("Must identify Chilli Chicken as non-vegetarian", spokenLower.contains("non-veg") || spokenLower.contains("non-vegetarian") || spokenLower.contains("chicken"))
        val actionTypes = response.actions.map { it.actionType }
        assertTrue("Must include dietary or highlight action", actionTypes.contains("ANSWER_DIETARY") || actionTypes.contains("HIGHLIGHT_ITEM"))
    }

    // =========================================================================
    // 5. ASYNC COROUTINE EXECUTION OF DIETARY QA AND SPEECH INTENT
    // =========================================================================

    @Test
    fun testAsyncProcessMenuDietaryQaExecution() = runBlocking {
        val result = groqClient.processMenuDietaryQa(
            userQuery = "What is chilli chicken?",
            userLanguage = "en",
            restaurantName = "Sarva Feast",
            menuItems = sampleMenuItems,
            sections = sampleSections
        )

        assertTrue("Result must be success", result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals("DIETARY_QUERY", response.intent)
        assertTrue(response.spoken.isNotBlank())
        assertTrue(response.caption.isNotBlank())
        assertTrue(response.actions.isNotEmpty())
    }

    @Test
    fun testAsyncProcessSpeechIntentExecution() = runBlocking {
        val result = groqClient.processSpeechIntent(
            userSpeech = "What is butter chicken?",
            userLanguage = "en"
        )

        assertTrue("Result must be success", result is Result.Success)
        val response = (result as Result.Success).data
        assertTrue(response.spoken.isNotBlank())
        val spokenLower = response.spoken.lowercase()
        assertTrue(spokenLower.contains("butter") || spokenLower.contains("chicken"))
    }
}
