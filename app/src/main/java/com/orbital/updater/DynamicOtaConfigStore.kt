package com.orbital.updater

import android.content.Context
import android.util.Log
import com.orbital.skills.MobileSkill
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OtaSkillDto(
    val id: String,
    val name: String,
    val categoryName: String = "AUTOMATION",
    val description: String,
    val detailedInstructions: String = "",
    val examplePrompts: List<String> = emptyList(),
    val icon: String = "⚡",
    val isEnabled: Boolean = true
) {
    fun toMobileSkill(): MobileSkill {
        val cat = runCatching {
            com.orbital.skills.SkillCategory.valueOf(categoryName.uppercase())
        }.getOrDefault(com.orbital.skills.SkillCategory.AUTOMATION)
        return MobileSkill(
            id = id,
            name = name,
            category = cat,
            description = description,
            detailedInstructions = detailedInstructions.ifBlank { description },
            examplePrompts = examplePrompts,
            icon = icon,
            isCustom = true,
            isEnabled = isEnabled
        )
    }
}

@Serializable
data class OtaHotPatchConfig(
    val patchVersion: Int = 0,
    val description: String = "",
    val customSystemPrompt: String? = null,
    val defaultProvider: String? = null,
    val dynamicRules: List<String> = emptyList(),
    val dynamicSkills: List<OtaSkillDto> = emptyList(),
    val modelRoutingOverrides: Map<String, String> = emptyMap(),
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val disabledActions: List<String> = emptyList(),
    val packageWorkarounds: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface HotPatchSyncResult {
    data class Applied(val patchVersion: Int, val description: String, val durationMs: Long) : HotPatchSyncResult
    data class AlreadyUpToDate(val currentVersion: Int) : HotPatchSyncResult
    data class Failed(val reason: String) : HotPatchSyncResult
}

@Singleton
class DynamicOtaConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {

    companion object {
        private const val TAG = "DynamicOtaConfigStore"
        private const val PREFS_NAME = "orbital_ota_config"
        private const val KEY_PATCH_VERSION = "ota_patch_version"
        private const val KEY_CONFIG_JSON = "ota_config_json"
        private const val KEY_CONFIG_HASH = "ota_config_hash"
        private const val KEY_LAST_FAILED_PATCH = "ota_last_failed_patch"
        private const val KEY_LAST_FAILED_TIME = "ota_last_failed_time"
        private const val FAILED_RETRY_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes cooldown after failure
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeConfigFlow = MutableStateFlow(loadInitialConfig())
    val activeConfigFlow: StateFlow<OtaHotPatchConfig> = _activeConfigFlow.asStateFlow()

    private fun loadInitialConfig(): OtaHotPatchConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return OtaHotPatchConfig()
        return runCatching {
            json.decodeFromString(OtaHotPatchConfig.serializer(), raw)
        }.getOrDefault(OtaHotPatchConfig())
    }

    fun getCurrentPatchVersion(): Int {
        return prefs.getInt(KEY_PATCH_VERSION, 0)
    }

    fun getActiveConfig(): OtaHotPatchConfig {
        return _activeConfigFlow.value
    }

    fun isPatchAlreadyApplied(patchVersion: Int): Boolean {
        return getCurrentPatchVersion() >= patchVersion
    }

    /**
     * Instantly synchronizes hot-patches from GitHub in ~1 second.
     */
    suspend fun syncInstantHotPatch(
        manifestUrl: String = "https://raw.githubusercontent.com/jaypal1046/Orbital/main/version.json",
        force: Boolean = false
    ): HotPatchSyncResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(manifestUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext HotPatchSyncResult.Failed("HTTP ${response.code}: Failed to fetch manifest")
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext HotPatchSyncResult.Failed("Empty manifest body")
                }

                val manifest = runCatching {
                    json.decodeFromString(VersionManifest.serializer(), body)
                }.getOrNull() ?: return@withContext HotPatchSyncResult.Failed("Manifest JSON parse error")

                val otaPatch = manifest.otaPatch
                    ?: return@withContext HotPatchSyncResult.AlreadyUpToDate(getCurrentPatchVersion())

                if (!force && otaPatch.patchVersion <= getCurrentPatchVersion()) {
                    return@withContext HotPatchSyncResult.AlreadyUpToDate(getCurrentPatchVersion())
                }

                val applied = applyOtaPatch(otaPatch, force = force)
                if (applied) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "Applied OTA hot-patch v${otaPatch.patchVersion} in ${duration}ms")
                    HotPatchSyncResult.Applied(
                        patchVersion = otaPatch.patchVersion,
                        description = otaPatch.patchDescription,
                        durationMs = duration
                    )
                } else {
                    HotPatchSyncResult.Failed("Failed to fetch or apply hot-patch config from ${otaPatch.configUrl}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Instant hot-patch sync failed: ${e.message}")
            HotPatchSyncResult.Failed(e.message ?: "Network error")
        }
    }

    /**
     * Applies a specific OTA hot patch manifest by downloading and parsing its config payload.
     */
    suspend fun applyOtaPatch(manifest: OtaPatchManifest, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentPatchVersion()
        if (!force && manifest.patchVersion <= currentVersion) {
            return@withContext true
        }

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

                val config = json.decodeFromString(OtaHotPatchConfig.serializer(), body)
                val bodyHash = body.hashCode().toString()

                prefs.edit()
                    .putInt(KEY_PATCH_VERSION, manifest.patchVersion)
                    .putString(KEY_CONFIG_JSON, body)
                    .putString(KEY_CONFIG_HASH, bodyHash)
                    .remove(KEY_LAST_FAILED_PATCH)
                    .remove(KEY_LAST_FAILED_TIME)
                    .apply()

                _activeConfigFlow.value = config
                Log.i(TAG, "Instant hot-patch v${manifest.patchVersion} activated successfully")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply OTA patch config", e)
            recordFailure(manifest.patchVersion)
            false
        }
    }

    /**
     * Direct programmatic injection for testing or emergency runtime fallback.
     */
    fun injectDirectPatch(config: OtaHotPatchConfig) {
        val raw = json.encodeToString(config)
        prefs.edit()
            .putInt(KEY_PATCH_VERSION, config.patchVersion)
            .putString(KEY_CONFIG_JSON, raw)
            .apply()
        _activeConfigFlow.value = config
    }

    private fun recordFailure(patchVersion: Int) {
        prefs.edit()
            .putInt(KEY_LAST_FAILED_PATCH, patchVersion)
            .putLong(KEY_LAST_FAILED_TIME, System.currentTimeMillis())
            .apply()
    }

    fun clearPatch() {
        prefs.edit().clear().apply()
        _activeConfigFlow.value = OtaHotPatchConfig()
    }
}
