package com.example.data.util

import android.content.Context
import com.example.BuildConfig

object VoiceAssistantPreferences {
    private const val PREFS_NAME = "voice_assistant_prefs"
    private const val KEY_API_KEY = "voice_api_key"
    private const val KEY_MODEL = "voice_model"
    private const val KEY_VOICE_NAME = "voice_name"

    fun saveVoiceApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getVoiceApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_API_KEY, "") ?: ""
        if (saved.isNotBlank()) return saved
        
        // Fallback to text key or BuildConfig if they match, but typically separate
        val textApiKey = GeminiService.getSavedApiKey(context)
        return textApiKey.ifBlank {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey == "MY_GEMINI_API_KEY") "" else buildKey
        }
    }

    fun saveVoiceModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODEL, model).apply()
    }

    fun getVoiceModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODEL, "gemini-2.0-flash-exp") ?: "gemini-2.0-flash-exp"
    }

    fun saveVoiceName(context: Context, voiceName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VOICE_NAME, voiceName).apply()
    }

    fun getVoiceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VOICE_NAME, "Puck") ?: "Puck" // AOEDE, CHARON, FENRIR, KORE, PUCK etc.
    }
}
