package com.orbital.action

import android.content.Context
import android.content.Intent
import android.net.Uri

data class AppNavigationRoute(
    val appName: String,
    val packageName: String,
    val requiredParameters: List<String>,
    val optionalParameters: List<String> = emptyList(),
    val directDeepLinkTemplate: String? = null,
    val description: String
)

object ScreenNavigationLedger {

    private val knownRoutes = mapOf(
        "WHERE_IS_MY_TRAIN" to AppNavigationRoute(
            appName = "Where is my Train",
            packageName = "com.whereismytrain.android",
            requiredParameters = listOf("source_or_train_number"),
            optionalParameters = listOf("destination", "date"),
            directDeepLinkTemplate = "https://whereismytrain.com/search?q={query}",
            description = "Live train running status, PNR status, and station-to-station schedules"
        ),
        "GMAIL" to AppNavigationRoute(
            appName = "Gmail",
            packageName = "com.google.android.gm",
            requiredParameters = emptyList(),
            optionalParameters = listOf("recipient", "subject", "message", "query"),
            description = "Check inbox, search unread emails, or compose drafts"
        ),
        "GOOGLE_MAPS" to AppNavigationRoute(
            appName = "Google Maps",
            packageName = "com.google.android.apps.maps",
            requiredParameters = listOf("destination"),
            directDeepLinkTemplate = "google.navigation:q={destination}",
            description = "Turn-by-turn navigation, route discovery, and nearby places"
        ),
        "SPOTIFY" to AppNavigationRoute(
            appName = "Spotify",
            packageName = "com.spotify.music",
            requiredParameters = listOf("query"),
            directDeepLinkTemplate = "https://open.spotify.com/search/{query}",
            description = "Search and stream songs, playlists, podcasts, and artists"
        ),
        "YOUTUBE" to AppNavigationRoute(
            appName = "YouTube",
            packageName = "com.google.android.youtube",
            requiredParameters = listOf("query"),
            directDeepLinkTemplate = "https://www.youtube.com/results?search_query={query}",
            description = "Search and stream videos, tutorials, and music"
        ),
        "WHATSAPP" to AppNavigationRoute(
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            requiredParameters = listOf("recipient_or_phone"),
            optionalParameters = listOf("message"),
            directDeepLinkTemplate = "https://api.whatsapp.com/send?text={message}",
            description = "Send chats, messages, and initiate calls"
        )
    )

    fun getRoute(key: String): AppNavigationRoute? {
        return knownRoutes[key.uppercase()]
    }

    fun findRouteByAppName(appName: String): AppNavigationRoute? {
        val lower = appName.lowercase().trim()
        return knownRoutes.values.firstOrNull {
            it.appName.lowercase().contains(lower) || it.packageName.lowercase().contains(lower)
        }
    }

    /**
     * Checks if mandatory parameters are missing for an app workflow.
     * Returns a list of missing parameter names if any.
     */
    fun getMissingParameters(action: DeviceAction): List<String> {
        return when (action.action.uppercase()) {
            "NAVIGATE" -> {
                if (action.query.isNullOrBlank() && action.target.isNullOrBlank()) listOf("Destination location") else emptyList()
            }
            "PLAY_MUSIC" -> {
                if (action.query.isNullOrBlank() && action.label.isNullOrBlank()) listOf("Song or artist name") else emptyList()
            }
            "COMPOSE_EMAIL" -> {
                if (action.recipient.isNullOrBlank() && action.target?.contains("@") != true) listOf("Recipient email address") else emptyList()
            }
            "SEND_SMS" -> {
                if (action.phoneNumber.isNullOrBlank() && action.recipient.isNullOrBlank()) listOf("Contact name or phone number") else emptyList()
            }
            "SEARCH_TRAIN" -> {
                if (action.query.isNullOrBlank() && action.target.isNullOrBlank()) listOf("Train number or Source & Destination stations") else emptyList()
            }
            else -> emptyList()
        }
    }

    /**
     * Formulates an intelligent clarifying question for missing parameters.
     */
    fun buildClarificationQuestion(missingParams: List<String>, contextName: String): String {
        return if (missingParams.size == 1) {
            "I'm ready to help with $contextName! Please provide the ${missingParams.first().lowercase()} to proceed."
        } else {
            "To help you with $contextName, please specify: ${missingParams.joinToString(", ")}."
        }
    }
}
