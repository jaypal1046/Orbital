package com.orbital.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class OtaHotPatchConfig(
    val patchVersion: Int = 0,
    val description: String = "",
    val customSystemPrompt: String? = null,
    val defaultProvider: String? = null,
    val enabledTiers: List<String> = emptyList(),
    val dynamicRules: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class DynamicOtaConfigStore(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val PREFS_NAME = "orbital_ota_config"
        private const val KEY_PATCH_VERSION = "ota_patch_version"
        private const val KEY_CONFIG_JSON = "ota_config_json"
        private const val KEY_CONFIG_HASH = "ota_config_hash"
        private const val KEY_LAST_FAILED_PATCH = "ota_last_failed_patch"
        private const val KEY_LAST_FAILED_TIME = "ota_last_failed_time"
        private const val FAILED_RETRY_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes cooldown after failure
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCurrentPatchVersion(): Int {
        return prefs.getInt(KEY_PATCH_VERSION, 0)
    }

    fun getActiveConfig(): OtaHotPatchConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return OtaHotPatchConfig()
        return runCatching {
            json.decodeFromString(OtaHotPatchConfig.serializer(), raw)
        }.getOrDefault(OtaHotPatchConfig())
    }

    fun isPatchAlreadyApplied(patchVersion: Int): Boolean {
        return getCurrentPatchVersion() >= patchVersion
    }

    suspend fun applyOtaPatch(manifest: OtaPatchManifest, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // 1. Skip if already applied to prevent continuous downloading
        val currentVersion = getCurrentPatchVersion()
        if (!force && manifest.patchVersion <= currentVersion) {
            return@withContext true
        }

        // 2. Failure rate-limiting (prevent hammering network if patch endpoint fails)
        val lastFailedPatch = prefs.getInt(KEY_LAST_FAILED_PATCH, -1)
        val lastFailedTime = prefs.getLong(KEY_LAST_FAILED_TIME, 0L)
        val now = System.currentTimeMillis()
        if (!force && lastFailedPatch == manifest.patchVersion && (now - lastFailedTime) < FAILED_RETRY_COOLDOWN_MS) {
            return@withContext false
        }

        try {
            val req = Request.Builder().url(manifest.configUrl).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    recordFailure(manifest.patchVersion)
                    return@withContext false
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    recordFailure(manifest.patchVersion)
                    return@withContext false
                }

                json.decodeFromString(OtaHotPatchConfig.serializer(), body)
                val bodyHash = body.hashCode().toString()

                prefs.edit()
                    .putInt(KEY_PATCH_VERSION, manifest.patchVersion)
                    .putString(KEY_CONFIG_JSON, body)
                    .putString(KEY_CONFIG_HASH, bodyHash)
                    .remove(KEY_LAST_FAILED_PATCH)
                    .remove(KEY_LAST_FAILED_TIME)
                    .apply()
                true
            }
        } catch (_: Exception) {
            recordFailure(manifest.patchVersion)
            false
        }
    }

    private fun recordFailure(patchVersion: Int) {
        prefs.edit()
            .putInt(KEY_LAST_FAILED_PATCH, patchVersion)
            .putLong(KEY_LAST_FAILED_TIME, System.currentTimeMillis())
            .apply()
    }

    fun clearPatch() {
        prefs.edit().clear().apply()
    }
}

