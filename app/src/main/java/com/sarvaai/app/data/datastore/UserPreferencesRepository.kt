package com.sarvaai.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val selectedLanguage: String = "en", // "en", "hi", "te"
    val staffLanguage: String = "hi",     // "hi", "te", "en"
    val isHighContrast: Boolean = false,
    val isCaptionsEnabled: Boolean = true,
    val speechSpeed: Float = 1.0f,       // 0.8f (slow), 1.0f, 1.25f, 1.5f
    val readingLevel: Int = 1,           // 1 = Normal, 2 = Simple, 3 = Very Simple
    val isTalkBackOptimized: Boolean = false,
    val isStepByStepEnabled: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val STAFF_LANGUAGE = stringPreferencesKey("staff_language")
        val IS_HIGH_CONTRAST = booleanPreferencesKey("is_high_contrast")
        val IS_CAPTIONS_ENABLED = booleanPreferencesKey("is_captions_enabled")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val READING_LEVEL = intPreferencesKey("reading_level")
        val IS_TALKBACK_OPTIMIZED = booleanPreferencesKey("is_talkback_optimized")
        val IS_STEP_BY_STEP = booleanPreferencesKey("is_step_by_step")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                selectedLanguage = preferences[PreferencesKeys.SELECTED_LANGUAGE] ?: "en",
                staffLanguage = preferences[PreferencesKeys.STAFF_LANGUAGE] ?: "hi",
                isHighContrast = preferences[PreferencesKeys.IS_HIGH_CONTRAST] ?: false,
                isCaptionsEnabled = preferences[PreferencesKeys.IS_CAPTIONS_ENABLED] ?: true,
                speechSpeed = preferences[PreferencesKeys.SPEECH_SPEED] ?: 1.0f,
                readingLevel = preferences[PreferencesKeys.READING_LEVEL] ?: 1,
                isTalkBackOptimized = preferences[PreferencesKeys.IS_TALKBACK_OPTIMIZED] ?: false,
                isStepByStepEnabled = preferences[PreferencesKeys.IS_STEP_BY_STEP] ?: false
            )
        }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_LANGUAGE] = language
        }
    }

    suspend fun setStaffLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STAFF_LANGUAGE] = language
        }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_HIGH_CONTRAST] = enabled
        }
    }

    suspend fun setCaptionsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CAPTIONS_ENABLED] = enabled
        }
    }

    suspend fun setSpeechSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPEECH_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun setReadingLevel(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.READING_LEVEL] = level.coerceIn(1, 3)
        }
    }

    suspend fun setStepByStep(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_STEP_BY_STEP] = enabled
        }
    }
}
