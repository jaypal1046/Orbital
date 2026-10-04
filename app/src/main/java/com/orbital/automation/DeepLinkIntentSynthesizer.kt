package com.orbital.automation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class IntentAccelerationResult(
    val isAccelerated: Boolean,
    val intentAction: String? = null,
    val intentDataUri: String? = null,
    val targetPackage: String? = null,
    val explanation: String,
    val stepSavingsEstimate: Int = 0
)

class DeepLinkIntentSynthesizer {

    private val settingsIntentTaxonomy = mapOf(
        listOf("wifi", "wi-fi", "wireless network", "hotspot") to "android.settings.WIFI_SETTINGS",
        listOf("bluetooth", "pair device", "wireless audio", "bluetooth device") to "android.settings.BLUETOOTH_SETTINGS",
        listOf("battery", "battery saver", "power saving", "battery percentage", "battery health") to "android.settings.BATTERY_SAVER_SETTINGS",
        listOf("display", "brightness", "dark mode", "screen timeout", "font size") to "android.settings.DISPLAY_SETTINGS",
        listOf("storage", "internal storage", "free up space", "storage capacity") to "android.settings.INTERNAL_STORAGE_SETTINGS",
        listOf("sound", "volume", "ringtone", "do not disturb", "vibration", "media volume") to "android.settings.SOUND_SETTINGS",
        listOf("location", "gps", "location access", "location permissions") to "android.settings.LOCATION_SOURCE_SETTINGS",
        listOf("apps", "installed applications", "app list", "manage apps", "app permissions") to "android.settings.APPLICATION_SETTINGS",
        listOf("accessibility", "screen reader", "talkback", "a11y settings") to "android.settings.ACCESSIBILITY_SETTINGS",
        listOf("date", "time", "timezone", "clock settings", "date and time") to "android.settings.DATE_SETTINGS",
        listOf("nfc", "contactless payment") to "android.settings.NFC_SETTINGS",
        listOf("developer options", "usb debugging", "developer settings") to "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
    )

    /**
     * Synthesizes an intent shortcut from user instructions, skipping tedious manual UI navigation.
     */
    fun synthesizeIntent(instruction: String): IntentAccelerationResult {
        val lower = instruction.trim().lowercase()

        // 1. Direct URL navigation
        val urlRegex = Regex("""(?:https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)
        val urlMatch = urlRegex.find(instruction)
        if (urlMatch != null) {
            var url = urlMatch.value
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            return IntentAccelerationResult(
                isAccelerated = true,
                intentAction = "android.intent.action.VIEW",
                intentDataUri = url,
                explanation = "Direct web browser navigation to $url (1-hop acceleration)",
                stepSavingsEstimate = 4
            )
        }

        // 2. Geolocation / Map Search Queries
        if (lower.startsWith("directions to ") || lower.startsWith("navigate to ") || lower.startsWith("map of ") || lower.contains("open in maps")) {
            val destination = lower
                .replace("directions to ", "")
                .replace("navigate to ", "")
                .replace("map of ", "")
                .replace("open in maps", "")
                .trim()
            if (destination.isNotEmpty()) {
                val encoded = URLEncoder.encode(destination, StandardCharsets.UTF_8.toString())
                return IntentAccelerationResult(
                    isAccelerated = true,
                    intentAction = "android.intent.action.VIEW",
                    intentDataUri = "geo:0,0?q=$encoded",
                    explanation = "Direct map navigation to '$destination' (1-hop acceleration)",
                    stepSavingsEstimate = 5
                )
            }
        }

        // 3. System Settings Shortcuts
        if (lower.contains("setting") || lower.contains("turn on") || lower.contains("turn off") || lower.contains("open") || lower.contains("configure")) {
            for ((keywords, action) in settingsIntentTaxonomy) {
                if (keywords.any { lower.contains(it) }) {
                    val settingName = keywords.first().replaceFirstChar { it.uppercase() }
                    return IntentAccelerationResult(
                        isAccelerated = true,
                        intentAction = action,
                        explanation = "Direct shortcut to $settingName Settings (skips App Drawer & Settings root)",
                        stepSavingsEstimate = 3
                    )
                }
            }
        }

        // 4. Dialing phone numbers
        val phoneRegex = Regex("""(?:call|dial|phone)\s+([\+0-9\-\s\(\)]{6,})""")
        val phoneMatch = phoneRegex.find(lower)
        if (phoneMatch != null) {
            val rawNumber = phoneMatch.groupValues[1].replace(Regex("""[\s\-\(\)]"""), "")
            return IntentAccelerationResult(
                isAccelerated = true,
                intentAction = "android.intent.action.DIAL",
                intentDataUri = "tel:$rawNumber",
                explanation = "Direct dialer launch for number $rawNumber",
                stepSavingsEstimate = 4
            )
        }

        // 5. Composing Email
        if (lower.startsWith("email to ") || lower.startsWith("send email to ") || lower.contains("compose email")) {
            val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
            val emailMatch = emailRegex.find(instruction)
            val recipient = emailMatch?.value ?: ""
            val uri = if (recipient.isNotEmpty()) "mailto:$recipient" else "mailto:"
            return IntentAccelerationResult(
                isAccelerated = true,
                intentAction = "android.intent.action.SENDTO",
                intentDataUri = uri,
                explanation = "Direct email client composition (skips opening mail app and searching compose button)",
                stepSavingsEstimate = 4
            )
        }

        return IntentAccelerationResult(
            isAccelerated = false,
            explanation = "No standard 1-hop intent shortcut detected; executing via standard ReAct UI loop.",
            stepSavingsEstimate = 0
        )
    }
}
