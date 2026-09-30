package com.orbital.updater

import kotlinx.serialization.Serializable

enum class DistributionChannel(val displayName: String) {
    PLAY_STORE("Google Play Store"),
    GITHUB_STANDALONE("GitHub / Standalone Sideload"),
    AUTO_DETECT("Auto-Detect from Installer")
}

enum class UpdateType {
    UP_TO_DATE,
    PLAY_STORE_REDIRECT,      // Full APK update via Google Play Store listing
    GITHUB_APK_DOWNLOAD,      // Full APK direct in-app download and 1-tap install
    OTA_HOT_PATCH             // Shorebird-style instant prompt/config hot-patch (no APK install)
}

@Serializable
data class PlayStoreManifest(
    val url: String = "https://play.google.com/store/apps/details?id=com.ai.orbital",
    val forceUpdate: Boolean = false
)

@Serializable
data class GithubStandaloneManifest(
    val apkUrl: String,
    val apkSizeBytes: Long = 0L,
    val sha256: String? = null,
    val forceUpdate: Boolean = false
)

@Serializable
data class OtaPatchManifest(
    val patchVersion: Int,
    val patchDescription: String = "",
    val configUrl: String,
    val minRequiredVersionCode: Int = 1
)

@Serializable
data class VersionManifest(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minRequiredVersionCode: Int = 1,
    val releaseDate: String = "",
    val changelog: List<String> = emptyList(),
    val playStore: PlayStoreManifest = PlayStoreManifest(),
    val githubStandalone: GithubStandaloneManifest? = null,
    val otaPatch: OtaPatchManifest? = null
)

sealed interface DownloadProgress {
    object Idle : DownloadProgress
    data class Downloading(val bytesRead: Long, val totalBytes: Long, val percent: Int) : DownloadProgress
    data class Completed(val apkFilePath: String) : DownloadProgress
    data class Failed(val errorMessage: String) : DownloadProgress
}

data class UpdateCheckResult(
    val updateType: UpdateType,
    val channel: DistributionChannel,
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val currentPatchVersion: Int,
    val latestPatchVersion: Int,
    val changelog: List<String>,
    val playStoreUrl: String?,
    val standaloneApkUrl: String?,
    val isForceUpdate: Boolean = false,
    val otaPatchManifest: OtaPatchManifest? = null
)
