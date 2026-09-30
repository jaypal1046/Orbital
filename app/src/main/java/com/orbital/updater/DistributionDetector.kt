package com.orbital.updater

import android.content.Context
import android.os.Build

class DistributionDetector(private val context: Context) {

    companion object {
        private const val PLAY_STORE_INSTALLER = "com.android.vending"
        private const val PREFS_NAME = "orbital_updater_prefs"
        private const val KEY_CHANNEL_OVERRIDE = "channel_override"
    }

    /**
     * Determines the active distribution channel (either user override or installer detection).
     */
    fun detectChannel(): DistributionChannel {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val override = prefs.getString(KEY_CHANNEL_OVERRIDE, null)
        if (!override.isNullOrBlank()) {
            return runCatching { DistributionChannel.valueOf(override) }.getOrDefault(DistributionChannel.AUTO_DETECT)
                .takeIf { it != DistributionChannel.AUTO_DETECT } ?: resolveFromInstaller()
        }
        return resolveFromInstaller()
    }

    fun setChannelOverride(channel: DistributionChannel?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (channel == null || channel == DistributionChannel.AUTO_DETECT) {
            prefs.edit().remove(KEY_CHANNEL_OVERRIDE).apply()
        } else {
            prefs.edit().putString(KEY_CHANNEL_OVERRIDE, channel.name).apply()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveFromInstaller(): DistributionChannel {
        return try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                pm.getInstallerPackageName(context.packageName)
            }

            if (installer != null && installer.contains(PLAY_STORE_INSTALLER, ignoreCase = true)) {
                DistributionChannel.PLAY_STORE
            } else {
                DistributionChannel.GITHUB_STANDALONE
            }
        } catch (_: Exception) {
            DistributionChannel.GITHUB_STANDALONE
        }
    }
}
