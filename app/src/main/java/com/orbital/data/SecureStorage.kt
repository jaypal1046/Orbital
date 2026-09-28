package com.orbital.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

open class SecureStorage(context: Context) {

    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFERENCES_NAME = "orbital_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_CHARACTER = "character"
        private const val KEY_SAFETY_ACTION = "safety_action"
        private const val KEY_SELECTED_CHARACTER = "selected_character"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    open fun saveApiKey(apiKey: String) {
        val clean = apiKey.trim()
        sharedPreferences.edit().putString(KEY_API_KEY, clean).apply()
        if (clean.isNotBlank()) {
            sharedPreferences.edit().putString("api_key_gemini", clean).apply()
            sharedPreferences.edit().putString("api_key_google", clean).apply()
        }
    }

    open fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    open fun saveProviderApiKey(provider: String, apiKey: String) {
        val clean = apiKey.trim()
        sharedPreferences.edit().putString("api_key_${provider.lowercase()}", clean).apply()
        if (provider.equals("gemini", ignoreCase = true) || provider.equals("google", ignoreCase = true)) {
            sharedPreferences.edit().putString("api_key_gemini", clean).apply()
            sharedPreferences.edit().putString("api_key_google", clean).apply()
        }
        if (clean.isNotBlank()) {
            sharedPreferences.edit().putString(KEY_API_KEY, clean).apply()
        }
    }

    open fun getProviderApiKey(provider: String): String? {
        val key = sharedPreferences.getString("api_key_${provider.lowercase()}", null)
        if (!key.isNullOrBlank()) return key.trim()

        if (provider.equals("gemini", ignoreCase = true) || provider.equals("google", ignoreCase = true) || provider.contains("gemini", ignoreCase = true)) {
            val gKey = sharedPreferences.getString("api_key_gemini", null)
                ?: sharedPreferences.getString("api_key_google", null)
                ?: sharedPreferences.getString(KEY_API_KEY, null)
            if (!gKey.isNullOrBlank()) return gKey.trim()
        }

        return null
    }

    open fun saveProviderEnabled(provider: String, enabled: Boolean) {
        sharedPreferences.edit().putBoolean("enabled_${provider.lowercase()}", enabled).apply()
    }

    open fun isProviderEnabled(provider: String): Boolean {
        return sharedPreferences.getBoolean("enabled_${provider.lowercase()}", true)
    }

    open fun saveProviderSelectedModel(provider: String, model: String) {
        sharedPreferences.edit().putString("model_${provider.lowercase()}", model).apply()
    }

    open fun getProviderSelectedModel(provider: String): String? {
        return sharedPreferences.getString("model_${provider.lowercase()}", null)
    }

    open fun saveProviderModelScope(provider: String, models: Set<String>?) {
        if (models == null) {
            sharedPreferences.edit().remove("scope_${provider.lowercase()}").apply()
        } else {
            sharedPreferences.edit().putStringSet("scope_${provider.lowercase()}", models).apply()
        }
    }

    open fun getProviderModelScope(provider: String): Set<String>? {
        return sharedPreferences.getStringSet("scope_${provider.lowercase()}", null)
    }

    open fun saveProvider(provider: String) {
        sharedPreferences.edit().putString(KEY_PROVIDER, provider).apply()
    }

    open fun getProvider(): String? {
        return sharedPreferences.getString(KEY_PROVIDER, null)
    }

    open fun saveCharacter(character: String) {
        sharedPreferences.edit().putString(KEY_CHARACTER, character).apply()
    }

    open fun getCharacter(): String? {
        return sharedPreferences.getString(KEY_CHARACTER, null)
    }

    open fun saveSafetyAction(action: String) {
        sharedPreferences.edit().putString(KEY_SAFETY_ACTION, action).apply()
    }

    open fun getSafetyAction(): String? {
        return sharedPreferences.getString(KEY_SAFETY_ACTION, null)
    }

    open fun saveSelectedCharacter(characterId: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_CHARACTER, characterId).apply()
    }

    open fun getSelectedCharacter(): String? {
        return sharedPreferences.getString(KEY_SELECTED_CHARACTER, null)
    }

    fun isSetupComplete(): Boolean {
        return sharedPreferences.getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun markSetupComplete() {
        sharedPreferences.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
