package com.orbital.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

object CustomTabPortalHelper {

    /**
     * Opens the portal URL in a Chrome Custom Tab so that users share their
     * existing Chrome sessions, Google accounts, and GitHub logins without retyping passwords.
     */
    fun openPortalCustomTab(context: Context, portalUrl: String) {
        try {
            val primaryColor = 0xFF0F1322.toInt()
            val defaultColors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(primaryColor)
                .setNavigationBarColor(primaryColor)
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(defaultColors)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()

            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            customTabsIntent.launchUrl(context, Uri.parse(portalUrl))
        } catch (e: Exception) {
            // Fallback to standard browser intent if Custom Tabs is unavailable
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}
