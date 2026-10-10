package com.sarvaai.app.core.config

import android.content.Context
import com.sarvaai.app.BuildConfig

object ApiKeyManager {
    private const val PREFS_NAME = "sarva_ai_config"
    private const val KEY_GROQ_API = "groq_api_key"

    fun getGroqApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY_GROQ_API, "")?.trim() ?: ""
        if (savedKey.isNotEmpty()) {
            return savedKey
        }
        return BuildConfig.GROQ_API_KEY.trim()
    }

    fun setGroqApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GROQ_API, key.trim()).apply()
    }

    fun hasValidGroqKey(context: Context): Boolean {
        val key = getGroqApiKey(context)
        return key.isNotEmpty() && !key.startsWith("gsk_your")
    }
}
