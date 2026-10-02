package com.orbital.action

import android.content.Context
import android.content.Intent
import android.net.Uri

data class AppNavigationRoute(
    val actionType: String,
    val requiredParameters: List<String>,
    val optionalParameters: List<String> = emptyList(),
    val directDeepLinkTemplate: String? = null,
    val description: String
)

/**
 * Dynamic action contract ledger for parameter completeness and intent validation.
 * Complies with the Zero Hardcoding Rule: all package routing and resolution is purely dynamic.
 */
object ScreenNavigationLedger {

    /**
     * Checks if mandatory parameters are missing for an app workflow action.
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
            "OPEN_URL" -> {
                if (action.url.isNullOrBlank() && action.target.isNullOrBlank()) listOf("Target URL") else emptyList()
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
