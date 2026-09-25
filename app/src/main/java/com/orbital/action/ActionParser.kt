package com.orbital.action

import org.json.JSONObject

data class ParsedResponse(
    val userDisplayText: String,
    val action: DeviceAction? = null
)

object ActionParser {

    fun buildSystemPrompt(characterName: String, capabilityContext: String = ""): String {
        return """
You are $characterName, an intelligent, helpful, witty, and powerful client-side executive AI companion.
You have DIRECT Android executive capabilities and can perform real actions on the user's phone.
$capabilityContext
Your Available Phone Tools & Capabilities:
1. OPEN_APP: Open any installed app on this device (e.g. Chrome, YouTube, WhatsApp, Settings, Spotify, Gmail, Camera, Calculator, Maps, etc.)
2. SEARCH_APP: Search inside specific apps (e.g. YouTube for videos, Spotify for songs, Maps for places, Play Store for apps).
3. NAVIGATE: Open GPS directions and navigation to an address or place in Google Maps.
4. PLAY_MUSIC: Search and play music/songs or artists in Spotify / YouTube.
5. COMPOSE_EMAIL: Open email app with recipient, subject, and body pre-filled.
6. SEND_SMS / WHATSAPP: Send direct messages or WhatsApp texts to a contact / phone number.
7. SEARCH_WEB: Search Google / the web for any query.
8. OPEN_URL: Open any web link or URL in the browser.
9. SET_TIMER: Set timers or alarms (specify seconds).
10. OPEN_SETTING: Open device settings (e.g. "wifi", "bluetooth", "display", "battery", "sound", "apps", "settings").
11. DEVICE_STATUS: Check battery percentage, charging state, and device hardware info.
12. MAKE_CALL: Open phone dialer with a phone number.

How to Execute Actions:
When the user asks you to perform a task, reply with a natural, friendly confirmation, and append an action block at the very end:

```action
{"action": "OPEN_APP", "target": "Chrome"}
```

Action Schema Examples:
- Open App:
  ```action
  {"action": "OPEN_APP", "target": "WhatsApp"}
  ```

- Search YouTube:
  ```action
  {"action": "SEARCH_APP", "target": "YouTube", "query": "Cyberpunk music mix"}
  ```

- Play Song / Music:
  ```action
  {"action": "PLAY_MUSIC", "query": "Starboy by The Weeknd"}
  ```

- GPS Navigation:
  ```action
  {"action": "NAVIGATE", "query": "Nearest Coffee Shop"}
  ```

- Web Search:
  ```action
  {"action": "SEARCH_WEB", "query": "latest space telescope discoveries"}
  ```

- Set Timer:
  ```action
  {"action": "SET_TIMER", "seconds": 300, "label": "Tea Timer"}
  ```

- Check Device Battery:
  ```action
  {"action": "DEVICE_STATUS"}
  ```

- Open Setting:
  ```action
  {"action": "OPEN_SETTING", "target": "wifi"}
  ```

Always be fast, accurate, and select the best matching action for the user's request!
""".trimIndent()
    }

    fun parse(rawResponse: String): ParsedResponse {
        val actionBlockRegex = Regex("```(?:action|json)?\\s*(\\{[\\s\\S]*?\"action\"[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)
        val match = actionBlockRegex.find(rawResponse)

        if (match != null) {
            val jsonStr = match.groupValues[1]
            val action = parseActionJson(jsonStr)
            val cleanText = rawResponse.replace(match.value, "").trim()
            return ParsedResponse(
                userDisplayText = cleanText.ifBlank { "Executing ${action?.action ?: "task"}..." },
                action = action
            )
        }

        // Fallback: Check if response contains inline JSON with action field
        val inlineJsonRegex = Regex("(\\{[^{}]*\"action\"\\s*:\\s*\"[A-Z_]+\"[^{}]*\\})", RegexOption.IGNORE_CASE)
        val inlineMatch = inlineJsonRegex.find(rawResponse)
        if (inlineMatch != null) {
            val jsonStr = inlineMatch.groupValues[1]
            val action = parseActionJson(jsonStr)
            if (action != null) {
                val cleanText = rawResponse.replace(inlineMatch.value, "").trim()
                return ParsedResponse(
                    userDisplayText = cleanText.ifBlank { "Executing ${action.action}..." },
                    action = action
                )
            }
        }

        // Fallback intent detection for direct command phrases if model forgot action block
        val fallbackAction = detectDirectCommand(rawResponse)

        return ParsedResponse(
            userDisplayText = rawResponse.trim(),
            action = fallbackAction
        )
    }

    private fun parseActionJson(jsonStr: String): DeviceAction? {
        return try {
            val json = JSONObject(jsonStr)
            val actionType = json.optString("action").uppercase()
            if (actionType.isBlank()) return null

            DeviceAction(
                action = actionType,
                target = json.optString("target").takeIf { it.isNotBlank() } ?: json.optString("appName").takeIf { it.isNotBlank() },
                query = json.optString("query").takeIf { it.isNotBlank() } ?: json.optString("search").takeIf { it.isNotBlank() },
                url = json.optString("url").takeIf { it.isNotBlank() } ?: json.optString("link").takeIf { it.isNotBlank() },
                seconds = json.optInt("seconds", -1).takeIf { it > 0 },
                label = json.optString("label").takeIf { it.isNotBlank() },
                phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() } ?: json.optString("phone").takeIf { it.isNotBlank() },
                recipient = json.optString("recipient").takeIf { it.isNotBlank() } ?: json.optString("to").takeIf { it.isNotBlank() },
                subject = json.optString("subject").takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() } ?: json.optString("body").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun detectDirectCommand(text: String): DeviceAction? {
        val lower = text.lowercase()
        return when {
            lower.startsWith("opening ") && lower.contains("gmail") -> DeviceAction("OPEN_APP", target = "Gmail")
            lower.startsWith("opening ") && lower.contains("youtube") -> DeviceAction("OPEN_APP", target = "YouTube")
            lower.startsWith("opening ") && lower.contains("whatsapp") -> DeviceAction("OPEN_APP", target = "WhatsApp")
            lower.startsWith("opening ") && lower.contains("chrome") -> DeviceAction("OPEN_APP", target = "Chrome")
            lower.startsWith("opening ") && lower.contains("settings") -> DeviceAction("OPEN_SETTING", target = "settings")
            lower.startsWith("opening ") && lower.contains("camera") -> DeviceAction("OPEN_APP", target = "Camera")
            else -> null
        }
    }
}
