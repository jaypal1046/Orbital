package com.orbital.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

data class DeepLinkRoute(
    val id: String,
    val appName: String,
    val packageName: String,
    val uriTemplates: List<String>,
    val fallbackWebTemplate: String,
    val requiredParams: List<String> = emptyList(),
    val category: AppCategory
)

object DeepLinkLedger {

    private const val TAG = "DeepLinkLedger"

    val routes: List<DeepLinkRoute> = listOf(
        // Trains & Transit
        DeepLinkRoute(
            id = "WHERE_IS_MY_TRAIN",
            appName = "Where is My Train",
            packageName = "com.whereismytrain.android",
            uriTemplates = listOf(
                "whereismytrain://search?from={from}&to={to}&train={train}",
                "whereismytrain://spot?train={query}",
                "https://whereismytrain.com/search?q={query}"
            ),
            fallbackWebTemplate = "https://www.google.com/search?q=where+is+my+train+live+status+{query}",
            requiredParams = listOf("query"),
            category = AppCategory.NAVIGATION
        ),
        DeepLinkRoute(
            id = "IRCTC",
            appName = "IRCTC Rail Connect",
            packageName = "cris.org.in.prs.ima",
            uriTemplates = listOf(
                "irctc://train_search?from={from}&to={to}&date={date}",
                "https://www.irctc.co.in/nget/train-search?from={from}&to={to}"
            ),
            fallbackWebTemplate = "https://www.irctc.co.in/nget/train-search?from={from}&to={to}",
            requiredParams = listOf("from", "to"),
            category = AppCategory.NAVIGATION
        ),
        // Navigation & Maps
        DeepLinkRoute(
            id = "GOOGLE_MAPS",
            appName = "Google Maps",
            packageName = "com.google.android.apps.maps",
            uriTemplates = listOf(
                "google.navigation:q={destination}&mode=d",
                "geo:0,0?q={destination}",
                "https://www.google.com/maps/dir/?api=1&destination={destination}"
            ),
            fallbackWebTemplate = "https://www.google.com/maps/search/?api=1&query={destination}",
            requiredParams = listOf("destination"),
            category = AppCategory.NAVIGATION
        ),
        // Music & Streaming
        DeepLinkRoute(
            id = "SPOTIFY",
            appName = "Spotify",
            packageName = "com.spotify.music",
            uriTemplates = listOf(
                "spotify:search:{query}",
                "https://open.spotify.com/search/{query}"
            ),
            fallbackWebTemplate = "https://open.spotify.com/search/{query}",
            requiredParams = listOf("query"),
            category = AppCategory.MUSIC
        ),
        DeepLinkRoute(
            id = "YOUTUBE",
            appName = "YouTube",
            packageName = "com.google.android.youtube",
            uriTemplates = listOf(
                "vnd.youtube://results?search_query={query}",
                "https://www.youtube.com/results?search_query={query}"
            ),
            fallbackWebTemplate = "https://www.youtube.com/results?search_query={query}",
            requiredParams = listOf("query"),
            category = AppCategory.MEDIA
        ),
        // Movie & Event Tickets
        DeepLinkRoute(
            id = "BOOKMYSHOW",
            appName = "BookMyShow",
            packageName = "com.bt.bms",
            uriTemplates = listOf(
                "bookmyshow://explore/movies?q={query}",
                "https://in.bookmyshow.com/explore/movies?q={query}"
            ),
            fallbackWebTemplate = "https://in.bookmyshow.com/explore/movies?q={query}",
            requiredParams = listOf("query"),
            category = AppCategory.GENERAL
        ),
        // Messaging
        DeepLinkRoute(
            id = "WHATSAPP",
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            uriTemplates = listOf(
                "https://api.whatsapp.com/send?phone={phone}&text={message}",
                "whatsapp://send?text={message}"
            ),
            fallbackWebTemplate = "https://api.whatsapp.com/send?text={message}",
            requiredParams = listOf("message"),
            category = AppCategory.MESSAGING
        )
    )

    fun findRoute(idOrPackage: String): DeepLinkRoute? {
        val clean = idOrPackage.lowercase().trim()
        return routes.firstOrNull {
            it.id.lowercase() == clean ||
            it.appName.lowercase().contains(clean) ||
            it.packageName.lowercase() == clean
        }
    }

    /**
     * Resolves and formats a deep-link Intent with pre-filled parameters.
     */
    fun buildDeepLinkIntent(
        context: Context,
        routeIdOrPackage: String,
        params: Map<String, String>
    ): Intent? {
        val route = findRoute(routeIdOrPackage) ?: return null
        val pm = context.packageManager

        for (template in route.uriTemplates) {
            val formattedUri = formatTemplate(template, params)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUri)).apply {
                if (isPackageInstalled(context, route.packageName)) {
                    setPackage(route.packageName)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(pm) != null) {
                return intent
            }
        }

        // Web Fallback
        val webFallbackUri = formatTemplate(route.fallbackWebTemplate, params)
        return Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUri)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun formatTemplate(template: String, params: Map<String, String>): String {
        var result = template
        params.forEach { (key, value) ->
            val encodedValue = Uri.encode(value.trim())
            result = result.replace("{$key}", encodedValue)
        }
        // Handle generic query replacement
        val defaultQuery = params["query"] ?: params["destination"] ?: params["message"] ?: ""
        result = result.replace("{query}", Uri.encode(defaultQuery))
        return result
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
