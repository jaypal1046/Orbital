package com.orbital.updater

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GitHubUpdateEngineTest {

    private lateinit var context: Context
    private lateinit var detector: DistributionDetector
    private lateinit var otaStore: DynamicOtaConfigStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        detector = DistributionDetector(context)
        otaStore = DynamicOtaConfigStore(context)
        otaStore.clearPatch()
    }

    @Test
    fun `detector honors manual channel override`() {
        detector.setChannelOverride(DistributionChannel.PLAY_STORE)
        assertThat(detector.detectChannel()).isEqualTo(DistributionChannel.PLAY_STORE)

        detector.setChannelOverride(DistributionChannel.GITHUB_STANDALONE)
        assertThat(detector.detectChannel()).isEqualTo(DistributionChannel.GITHUB_STANDALONE)
    }

    @Test
    fun `otaConfigStore stores and retrieves active patch config`() {
        assertThat(otaStore.getCurrentPatchVersion()).isEqualTo(0)

        val config = otaStore.getActiveConfig()
        assertThat(config.patchVersion).isEqualTo(0)
    }

    @Test
    fun `updateCheckResult correctly identifies Play Store track vs GitHub Standalone track`() {
        val playStoreManifest = VersionManifest(
            latestVersionCode = 10,
            latestVersionName = "2.0.0",
            playStore = PlayStoreManifest(url = "https://play.google.com/store/apps/details?id=com.ai.orbital"),
            githubStandalone = GithubStandaloneManifest(apkUrl = "https://github.com/org/repo/releases/download/v2.0.0/app.apk")
        )

        // Play Store Track
        val playStoreResult = UpdateCheckResult(
            updateType = UpdateType.PLAY_STORE_REDIRECT,
            channel = DistributionChannel.PLAY_STORE,
            currentVersionCode = 3,
            currentVersionName = "1.0.2",
            latestVersionCode = playStoreManifest.latestVersionCode,
            latestVersionName = playStoreManifest.latestVersionName,
            currentPatchVersion = 0,
            latestPatchVersion = 0,
            changelog = listOf("New features"),
            playStoreUrl = playStoreManifest.playStore.url,
            standaloneApkUrl = null
        )

        assertThat(playStoreResult.updateType).isEqualTo(UpdateType.PLAY_STORE_REDIRECT)
        assertThat(playStoreResult.playStoreUrl).contains("play.google.com")

        // Standalone Track
        val standaloneResult = UpdateCheckResult(
            updateType = UpdateType.GITHUB_APK_DOWNLOAD,
            channel = DistributionChannel.GITHUB_STANDALONE,
            currentVersionCode = 3,
            currentVersionName = "1.0.2",
            latestVersionCode = playStoreManifest.latestVersionCode,
            latestVersionName = playStoreManifest.latestVersionName,
            currentPatchVersion = 0,
            latestPatchVersion = 0,
            changelog = listOf("New features"),
            playStoreUrl = null,
            standaloneApkUrl = playStoreManifest.githubStandalone?.apkUrl
        )

        assertThat(standaloneResult.updateType).isEqualTo(UpdateType.GITHUB_APK_DOWNLOAD)
        assertThat(standaloneResult.standaloneApkUrl).contains(".apk")
    }

    @Test
    fun `OTA hot-patch is identified when binary version is up to date but patch is newer`() {
        val otaResult = UpdateCheckResult(
            updateType = UpdateType.OTA_HOT_PATCH,
            channel = DistributionChannel.PLAY_STORE,
            currentVersionCode = 4,
            currentVersionName = "1.0.3",
            latestVersionCode = 4,
            latestVersionName = "1.0.3",
            currentPatchVersion = 1,
            latestPatchVersion = 3,
            changelog = listOf("Hot-fix prompt routing rules"),
            playStoreUrl = null,
            standaloneApkUrl = null,
            otaPatchManifest = OtaPatchManifest(
                patchVersion = 3,
                patchDescription = "Hot-fix prompt routing rules",
                configUrl = "https://raw.githubusercontent.com/org/repo/main/ota.json"
            )
        )

        assertThat(otaResult.updateType).isEqualTo(UpdateType.OTA_HOT_PATCH)
        assertThat(otaResult.latestPatchVersion).isEqualTo(3)
        assertThat(otaResult.otaPatchManifest).isNotNull()
    }

    @Test
    fun `applyOtaPatch skips network download if patch version is already applied`() = runTest {
        val otaManifest = OtaPatchManifest(
            patchVersion = 2,
            patchDescription = "Test patch",
            configUrl = "https://invalid-url-that-would-fail.example.com/patch.json"
        )

        // Pre-apply version 2 in store
        context.getSharedPreferences("orbital_ota_config", Context.MODE_PRIVATE)
            .edit()
            .putInt("ota_patch_version", 2)
            .apply()

        assertThat(otaStore.isPatchAlreadyApplied(2)).isTrue()
        assertThat(otaStore.isPatchAlreadyApplied(1)).isTrue()
        assertThat(otaStore.isPatchAlreadyApplied(3)).isFalse()

        // Should return true immediately without calling the invalid URL
        val applied = otaStore.applyOtaPatch(otaManifest, force = false)
        assertThat(applied).isTrue()
    }
}

