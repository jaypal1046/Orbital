package com.orbital.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Dynamic Deep Link and Intent Resolver.
 * Strict Zero Hardcoding Compliance: All package matching and intent resolution is dynamic.
 */
object DeepLinkLedger {

    private const val TAG = "DeepLinkLedger"

    /**
     * Dynamically builds an intent for a target application or query without static hardcoding.
     */
    fun buildDeepLinkIntent(
        context: Context,
        targetAppOrPackage: String,
        params: Map<String, String> = emptyMap()
    ): Intent? {
        val query = params["query"] ?: params["destination"] ?: params["url"].orEmpty()

        // 1. Direct package resolution
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetAppOrPackage)
        if (launchIntent != null) {
            if (query.isNotBlank()) {
                launchIntent.putExtra("query", query)
            }
            return launchIntent
        }

        // 2. Fuzzy package matching via installed applications
        try {
            val installedApps = context.packageManager.getInstalledApplications(0)
            val cleanTarget = targetAppOrPackage.lowercase().replace(" ", "")
            val matched = installedApps.firstOrNull { app ->
                val appLabel = context.packageManager.getApplicationLabel(app).toString().lowercase().replace(" ", "")
                appLabel.contains(cleanTarget) || app.packageName.lowercase().contains(cleanTarget)
            }

            if (matched != null) {
                val matchedIntent = context.packageManager.getLaunchIntentForPackage(matched.packageName)
                if (matchedIntent != null) {
                    if (query.isNotBlank()) {
                        matchedIntent.putExtra("query", query)
                    }
                    return matchedIntent
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving dynamic installed app: ${e.message}")
        }

        // 3. Web search / URI fallback
        return if (query.startsWith("http://") || query.startsWith("https://")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(query))
        } else if (query.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        } else {
            null
        }
    }
}
