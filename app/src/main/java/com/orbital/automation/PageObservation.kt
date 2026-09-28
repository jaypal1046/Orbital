package com.orbital.automation

import android.graphics.Rect
import kotlinx.serialization.Serializable

@Serializable
enum class PageType {
    FORM,           // Input fields, station/date search form
    LIST_VIEW,      // Search results, list of trains, flight list, song list
    DETAILS_VIEW,   // Single item view, live train status, timetable
    CHECKOUT,       // Passenger details, seat layout, payment screen
    DIALOG,         // System permission, alert dialog, confirmation
    ERROR,          // No results found, network error, login required
    UNKNOWN
}

@Serializable
data class InputFieldState(
    val id: String? = null,
    val hint: String? = null,
    val currentValue: String = "",
    val isEditable: Boolean = true,
    val isFocused: Boolean = false
)

@Serializable
data class PageObservation(
    val appPackage: String,
    val pageIdentity: String,          // e.g. "WhereIsMyTrain.HomeScreen", "GoogleMaps.RouteView"
    val pageType: PageType = PageType.UNKNOWN,
    val visibleInputs: List<InputFieldState> = emptyList(),
    val visibleButtons: List<String> = emptyList(),  // Available action buttons e.g. ["Find trains", "PNR", "Tickets"]
    val extractedEntities: Map<String, String> = emptyMap(), // Key data e.g. {"from": "Mumbai LTT", "to": "Bhadohi"}
    val recommendedNextActions: List<String> = emptyList(), // Suggestions for next steps
    val requiresUserClarification: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toContextSummary(): String {
        val sb = StringBuilder()
        sb.append("📍 Current Page: $pageIdentity ($pageType) in $appPackage\n")
        if (visibleInputs.isNotEmpty()) {
            sb.append("• Inputs: ${visibleInputs.joinToString(", ") { "${it.hint ?: "Field"}=${it.currentValue.ifBlank { "[Empty]" }}" }}\n")
        }
        if (visibleButtons.isNotEmpty()) {
            sb.append("• Action Buttons: ${visibleButtons.joinToString(", ")}\n")
        }
        if (extractedEntities.isNotEmpty()) {
            sb.append("• Extracted Data: ${extractedEntities.entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n")
        }
        if (recommendedNextActions.isNotEmpty()) {
            sb.append("• Possible Next Actions: ${recommendedNextActions.joinToString(", ")}\n")
        }
        return sb.toString().trim()
    }
}
