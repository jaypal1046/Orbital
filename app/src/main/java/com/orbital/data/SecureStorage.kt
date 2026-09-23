package com.orbital.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    companion object {
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

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun saveProviderApiKey(provider: String, apiKey: String) {
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

    fun getProviderApiKey(provider: String): String? {
        val key = sharedPreferences.getString("api_key_${provider.lowercase()}", null)
        if (!key.isNullOrBlank()) return key.trim()

        if (provider.equals("gemini", ignoreCase = true) || provider.equals("google", ignoreCase = true) || provider.contains("gemini", ignoreCase = true)) {
            val gKey = sharedPreferences.getString("api_key_gemini", null)
                ?: sharedPreferences.getString("api_key_google", null)
            if (!gKey.isNullOrBlank()) return gKey.trim()
        }

        return null
    }

    fun saveProviderEnabled(provider: String, enabled: Boolean) {
        sharedPreferences.edit().putBoolean("enabled_${provider.lowercase()}", enabled).apply()
    }

    fun isProviderEnabled(provider: String): Boolean {
        return sharedPreferences.getBoolean("enabled_${provider.lowercase()}", true)
    }

    fun saveProviderSelectedModel(provider: String, model: String) {
        sharedPreferences.edit().putString("model_${provider.lowercase()}", model).apply()
    }

    fun getProviderSelectedModel(provider: String): String? {
        return sharedPreferences.getString("model_${provider.lowercase()}", null)
    }

    fun saveProviderModelScope(provider: String, models: Set<String>?) {
        if (models == null) {
            sharedPreferences.edit().remove("scope_${provider.lowercase()}").apply()
        } else {
            sharedPreferences.edit().putStringSet("scope_${provider.lowercase()}", models).apply()
        }
    }

    fun getProviderModelScope(provider: String): Set<String>? {
        return sharedPreferences.getStringSet("scope_${provider.lowercase()}", null)
    }

    fun saveProvider(provider: String) {
        sharedPreferences.edit().putString(KEY_PROVIDER, provider).apply()
    }

    fun getProvider(): String? {
        return sharedPreferences.getString(KEY_PROVIDER, null)
    }

    fun saveCharacter(character: String) {
        sharedPreferences.edit().putString(KEY_CHARACTER, character).apply()
    }

    fun getCharacter(): String? {
        return sharedPreferences.getString(KEY_CHARACTER, null)
    }

    fun saveSafetyAction(action: String) {
        sharedPreferences.edit().putString(KEY_SAFETY_ACTION, action).apply()
    }

    fun getSafetyAction(): String? {
        return sharedPreferences.getString(KEY_SAFETY_ACTION, null)
    }

    fun saveSelectedCharacter(characterId: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_CHARACTER, characterId).apply()
    }

    fun getSelectedCharacter(): String? {
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
