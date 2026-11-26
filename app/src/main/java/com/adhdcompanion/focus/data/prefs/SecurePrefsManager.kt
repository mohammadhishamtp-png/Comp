package com.adhdcompanion.focus.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Gemini API key and small user settings locally, encrypted at
 * rest with a hardware-backed master key (with safe fallback if Keystore is
 * unavailable on an emulator or after restore).
 */
class SecurePrefsManager(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.applicationContext.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    var geminiApiKey: String?
        get() = prefs.getString(KEY_GEMINI_API_KEY, null)?.trim()?.ifBlank { null }
        set(value) {
            prefs.edit()
                .putString(KEY_GEMINI_API_KEY, value?.trim()?.ifBlank { null })
                .remove(KEY_RESOLVED_TEXT_MODEL)
                .apply()
        }

    var groqApiKey: String?
        get() = prefs.getString(KEY_GROQ_API_KEY, null)?.trim()?.ifBlank { null }
        set(value) {
            prefs.edit()
                .putString(KEY_GROQ_API_KEY, value?.trim()?.ifBlank { null })
                .apply()
        }

    var useFlashLiteFallbackModel: Boolean
        get() = prefs.getBoolean(KEY_USE_FALLBACK_MODEL, false)
        set(value) {
            prefs.edit()
                .putBoolean(KEY_USE_FALLBACK_MODEL, value)
                .remove(KEY_RESOLVED_TEXT_MODEL)
                .apply()
        }

    var resolvedTextModel: String?
        get() = prefs.getString(KEY_RESOLVED_TEXT_MODEL, null)?.trim()?.ifBlank { null }
        set(value) = prefs.edit().putString(KEY_RESOLVED_TEXT_MODEL, value).apply()

    var userDisplayName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    /** 24h local time, e.g. "07:30", for the daily wake/transition briefing alarm. */
    var morningBriefingTime: String?
        get() = prefs.getString(KEY_MORNING_TIME, "07:30")
        set(value) = prefs.edit().putString(KEY_MORNING_TIME, value).apply()

    var morningAlarmEnabled: Boolean
        get() {
            if (!prefs.contains(KEY_MORNING_ALARM_ENABLED)) {
                return !prefs.getString(KEY_MORNING_TIME, null).isNullOrBlank()
            }
            return prefs.getBoolean(KEY_MORNING_ALARM_ENABLED, false)
        }
        set(value) = prefs.edit().putBoolean(KEY_MORNING_ALARM_ENABLED, value).apply()

    var voicePreference: String
        get() = prefs.getString(KEY_VOICE_PREF, VOICE_GEMINI_WITH_FALLBACK) ?: VOICE_GEMINI_WITH_FALLBACK
        set(value) = prefs.edit().putString(KEY_VOICE_PREF, value).apply()

    fun hasApiKey(): Boolean = !geminiApiKey.isNullOrBlank()

    fun hasGroqApiKey(): Boolean = !groqApiKey.isNullOrBlank()

    fun hasAnyCloudKey(): Boolean = hasGroqApiKey() || hasApiKey()

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_USE_FALLBACK_MODEL = "use_fallback_model"
        private const val KEY_RESOLVED_TEXT_MODEL = "resolved_text_model"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_MORNING_TIME = "morning_briefing_time"
        private const val KEY_MORNING_ALARM_ENABLED = "morning_alarm_enabled"
        private const val KEY_VOICE_PREF = "voice_preference"

        const val VOICE_GEMINI_WITH_FALLBACK = "gemini_with_fallback"
        const val VOICE_ANDROID_ONLY = "android_only"
    }
}
