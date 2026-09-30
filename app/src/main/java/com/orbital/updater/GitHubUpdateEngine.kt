package com.orbital.updater

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubUpdateEngine(
    private val context: Context,
    private val distributionDetector: DistributionDetector = DistributionDetector(context),
    private val otaConfigStore: DynamicOtaConfigStore = DynamicOtaConfigStore(context),
    private val manifestUrl: String = "https://raw.githubusercontent.com/jaypal1046/Orbital/main/version.json",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val TAG = "GitHubUpdateEngine"
        private const val PREFS_NAME = "orbital_update_engine"
        private const val KEY_LAST_CHECK_TIME = "last_update_check_time"
        const val DEFAULT_CHECK_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes minimum between auto checks
    }

    private val enginePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Checks GitHub for updates and resolves the exact UpdateType based on the active distribution track.
     * @param force If true, ignores the check cooldown timer (e.g. for manual button click).
     * @param customManifestUrl Optional override for testing or enterprise mirrors.
     */
    suspend fun checkForUpdates(
        force: Boolean = false,
        customManifestUrl: String? = null,
        cooldownMs: Long = DEFAULT_CHECK_COOLDOWN_MS
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val targetUrl = customManifestUrl ?: manifestUrl
        val channel = distributionDetector.detectChannel()

        val (currentVersionCode, currentVersionName) = getLocalVersionInfo()
        val currentPatchVersion = otaConfigStore.getCurrentPatchVersion()

        val now = System.currentTimeMillis()
        val lastCheck = enginePrefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        if (!force && (now - lastCheck) < cooldownMs) {
            // Throttled: return UP_TO_DATE without redundant network call
            return@withContext createUpToDateResult(
                channel, currentVersionCode, currentVersionName, currentPatchVersion
            )
        }

        try {
            val request = Request.Builder().url(targetUrl).build()
            httpClient.newCall(request).execute().use { response ->
                enginePrefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val manifest = runCatching {
                            json.decodeFromString(VersionManifest.serializer(), body)
                        }.getOrNull()

                        if (manifest != null) {
                            return@withContext processManifest(
                                manifest, channel, currentVersionCode, currentVersionName, currentPatchVersion
                            )
                        }
                    }
                }
            }

            // Fallback: Check GitHub Releases API if version.json is not present on branch
            val releasesApiUrl = "https://api.github.com/repos/jaypal1046/Orbital/releases/latest"
            val releaseReq = Request.Builder()
                .url(releasesApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            httpClient.newCall(releaseReq).execute().use { relResponse ->
                if (!relResponse.isSuccessful) {
                    return@withContext createUpToDateResult(
                        channel, currentVersionCode, currentVersionName, currentPatchVersion
                    )
                }

                val relBody = relResponse.body?.string().orEmpty()
                if (relBody.isBlank()) {
                    return@withContext createUpToDateResult(
                        channel, currentVersionCode, currentVersionName, currentPatchVersion
                    )
                }

                val releaseManifest = parseGitHubRelease(relBody, currentVersionCode)
                if (releaseManifest != null) {
                    return@withContext processManifest(
                        releaseManifest, channel, currentVersionCode, currentVersionName, currentPatchVersion
                    )
                }
            }

            createUpToDateResult(channel, currentVersionCode, currentVersionName, currentPatchVersion)
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            createUpToDateResult(channel, currentVersionCode, currentVersionName, currentPatchVersion)
        }
    }

    private fun processManifest(
        manifest: VersionManifest,
        channel: DistributionChannel,
        currentVersionCode: Int,
        currentVersionName: String,
        currentPatchVersion: Int
    ): UpdateCheckResult {
        // 1. Check for Major Binary APK Updates
        if (manifest.latestVersionCode > currentVersionCode) {
            val isForce = currentVersionCode < manifest.minRequiredVersionCode ||
                    (channel == DistributionChannel.PLAY_STORE && manifest.playStore.forceUpdate) ||
                    (channel == DistributionChannel.GITHUB_STANDALONE && manifest.githubStandalone?.forceUpdate == true)

            val updateType = if (channel == DistributionChannel.PLAY_STORE) {
                UpdateType.PLAY_STORE_REDIRECT
            } else {
                UpdateType.GITHUB_APK_DOWNLOAD
            }

            return UpdateCheckResult(
                updateType = updateType,
                channel = channel,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                latestVersionCode = manifest.latestVersionCode,
                latestVersionName = manifest.latestVersionName,
                currentPatchVersion = currentPatchVersion,
                latestPatchVersion = manifest.otaPatch?.patchVersion ?: currentPatchVersion,
                changelog = manifest.changelog,
                playStoreUrl = manifest.playStore.url,
                standaloneApkUrl = manifest.githubStandalone?.apkUrl,
                isForceUpdate = isForce,
                otaPatchManifest = manifest.otaPatch
            )
        }

        // 2. Check for Shorebird-style Instant OTA Patch Updates
        val otaPatch = manifest.otaPatch
        if (otaPatch != null && otaPatch.patchVersion > currentPatchVersion && currentVersionCode >= otaPatch.minRequiredVersionCode) {
            return UpdateCheckResult(
                updateType = UpdateType.OTA_HOT_PATCH,
                channel = channel,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                latestVersionCode = manifest.latestVersionCode,
                latestVersionName = manifest.latestVersionName,
                currentPatchVersion = currentPatchVersion,
                latestPatchVersion = otaPatch.patchVersion,
                changelog = listOfNotNull(otaPatch.patchDescription.takeIf { it.isNotBlank() } ?: "Instant configuration & prompt update"),
                playStoreUrl = manifest.playStore.url,
                standaloneApkUrl = manifest.githubStandalone?.apkUrl,
                isForceUpdate = false,
                otaPatchManifest = otaPatch
            )
        }

        // 3. Already Up to date
        return createUpToDateResult(channel, currentVersionCode, currentVersionName, currentPatchVersion, manifest)
    }

    private fun parseGitHubRelease(jsonBody: String, currentVersionCode: Int): VersionManifest? {
        return runCatching {
            val element = json.parseToJsonElement(jsonBody) as? kotlinx.serialization.json.JsonObject ?: return null
            val tagName = element["tag_name"]?.toString()?.trim('"').orEmpty() // e.g. "v1.0.4"
            val rawName = tagName.removePrefix("v")
            val releaseNotes = element["body"]?.toString()?.trim('"')?.replace("\\n", "\n").orEmpty()
            val changelogList = releaseNotes.lines().filter { it.isNotBlank() }

            val assets = element["assets"] as? kotlinx.serialization.json.JsonArray
            var apkUrl: String? = null
            var apkSize = 0L

            assets?.forEach { asset ->
                val assetObj = asset as? kotlinx.serialization.json.JsonObject
                val name = assetObj?.get("name")?.toString()?.trim('"').orEmpty()
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = assetObj?.get("browser_download_url")?.toString()?.trim('"')
                    apkSize = assetObj?.get("size")?.toString()?.toLongOrNull() ?: 0L
                }
            }

            // Derive version code from tag if possible (e.g. 1.0.4 -> 1004 or incremental)
            val parts = rawName.split(".").mapNotNull { it.toIntOrNull() }
            val derivedCode = if (parts.size >= 3) {
                parts[0] * 10000 + parts[1] * 100 + parts[2]
            } else {
                currentVersionCode + 1
            }

            VersionManifest(
                latestVersionCode = derivedCode,
                latestVersionName = rawName.ifBlank { "Latest" },
                changelog = changelogList,
                githubStandalone = apkUrl?.let {
                    GithubStandaloneManifest(
                        apkUrl = it,
                        apkSizeBytes = apkSize
                    )
                }
            )
        }.getOrNull()
    }


    /**
     * Applies an OTA patch in the background. Skips download if patch is already applied.
     */
    suspend fun applyOtaPatch(manifest: OtaPatchManifest, force: Boolean = false): Boolean {
        return otaConfigStore.applyOtaPatch(manifest, force)
    }


    @Suppress("DEPRECATION")
    private fun getLocalVersionInfo(): Pair<Int, String> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
            Pair(code, pInfo.versionName ?: "1.0.0")
        } catch (_: Exception) {
            Pair(1, "1.0.0")
        }
    }

    private fun createUpToDateResult(
        channel: DistributionChannel,
        currentCode: Int,
        currentName: String,
        patchVersion: Int,
        manifest: VersionManifest? = null
    ): UpdateCheckResult {
        return UpdateCheckResult(
            updateType = UpdateType.UP_TO_DATE,
            channel = channel,
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            latestVersionCode = manifest?.latestVersionCode ?: currentCode,
            latestVersionName = manifest?.latestVersionName ?: currentName,
            currentPatchVersion = patchVersion,
            latestPatchVersion = manifest?.otaPatch?.patchVersion ?: patchVersion,
            changelog = emptyList(),
            playStoreUrl = null,
            standaloneApkUrl = null,
            isForceUpdate = false,
            otaPatchManifest = null
        )
    }
}
