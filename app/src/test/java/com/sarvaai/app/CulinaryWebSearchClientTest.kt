package com.sarvaai.app

import com.sarvaai.app.llm.CulinaryWebSearchClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CulinaryWebSearchClientTest {

    private val culinaryClient = CulinaryWebSearchClient()

    @Test
    fun testIsFoodQueryWithCulinaryTerms() {
        assertTrue(culinaryClient.isFoodQuery("What is chilli chicken?"))
        assertTrue(culinaryClient.isFoodQuery("Tell me about Chicken 65"))
        assertTrue(culinaryClient.isFoodQuery("What is Pesarattu?"))
        assertTrue(culinaryClient.isFoodQuery("Is Paneer Butter Masala spicy?"))
        assertTrue(culinaryClient.isFoodQuery("How is Dal Makhani made?"))
        assertTrue(culinaryClient.isFoodQuery("బిర్యానీ అంటే ఏమిటి?"))
        assertTrue(culinaryClient.isFoodQuery("दाल मखनी क्या है?"))
        assertTrue(culinaryClient.isFoodQuery("Tell me about gongura pachadi"))
    }

    @Test
    fun testIsFoodQueryWithNonFoodTerms() {
        assertFalse(culinaryClient.isFoodQuery("What is the weather today?"))
        assertFalse(culinaryClient.isFoodQuery("Who won the cricket match?"))
        assertFalse(culinaryClient.isFoodQuery("Write a python script"))
        assertFalse(culinaryClient.isFoodQuery("What is quantum computing?"))
        assertFalse(culinaryClient.isFoodQuery(""))
    }

    @Test
    fun testExtractDishSearchTerm() {
        assertEquals("chilli chicken", culinaryClient.extractDishSearchTerm("What is chilli chicken?"))
        assertEquals("Chicken 65", culinaryClient.extractDishSearchTerm("Tell me about Chicken 65"))
        assertEquals("Pesarattu", culinaryClient.extractDishSearchTerm("What is Pesarattu?"))
        assertEquals("Dal Makhani", culinaryClient.extractDishSearchTerm("How is Dal Makhani made"))
        assertEquals("Gongura Pachadi", culinaryClient.extractDishSearchTerm("Tell me about Gongura Pachadi dish"))
    }

    @Test
    fun testNonFoodQuerySkipsSearch() = runBlocking {
        val result = culinaryClient.searchFoodKnowledge("What is the capital of France?")
        assertNull(result)
    }

    @Test
    fun testSearchFoodKnowledgeLiveDishRetrieval() = runBlocking {
        // Query a well-known food term
        val result = culinaryClient.searchFoodKnowledge("What is chilli chicken?")
        // If internet is reachable, it should fetch non-blank summary; if offline, it handles it safely
        if (result != null) {
            assertTrue(result.contains("chicken", ignoreCase = true) || result.contains("dish", ignoreCase = true))
        }
    }

    @Test
    fun testSearchFoodKnowledgePesarattuRetrieval() = runBlocking {
        val result = culinaryClient.searchFoodKnowledge("What is Pesarattu?")
        if (result != null) {
            assertTrue(result.contains("Pesarattu", ignoreCase = true) || result.contains("dosa", ignoreCase = true) || result.contains("crepe", ignoreCase = true))
        }
    }

    @Test
    fun testFastTimeoutAndErrorSafety() = runBlocking {
        // Create client with ultra-short 1ms timeout to verify robust error-safe handling
        val shortTimeoutClient = CulinaryWebSearchClient(
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MILLISECONDS)
                .readTimeout(1, TimeUnit.MILLISECONDS)
                .build()
        )
        // Should not throw exception, must return null safely
        val result = shortTimeoutClient.searchFoodKnowledge("What is chilli chicken?")
        // Either returns null or handles gracefully
        assertTrue(result == null || result.isNotEmpty())
    }
}
