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
5. COMPOSE_EMAIL: Open email app (Gmail) with recipient, subject, and body pre-filled.
6. SEND_SMS / WHATSAPP: Send direct messages or WhatsApp texts to a contact / phone number.
7. SEARCH_WEB: Search Google / the web for any query.
8. OPEN_URL: Open any web link or URL in the browser.
9. SET_TIMER: Set timers or alarms (specify seconds).
10. OPEN_SETTING: Open device settings (e.g. "wifi", "bluetooth", "display", "battery", "sound", "apps", "settings").
11. DEVICE_STATUS: Check battery percentage, charging state, and device hardware info.
12. MAKE_CALL: Open phone dialer with a phone number.

Multi-Intent & Action Execution Guidelines:
- When the user asks to compose or write an email (e.g., "open gmail and write email to jaypal1046@gmail.com write about why Flutter is based..."), use COMPOSE_EMAIL with "target": "Gmail", "recipient", a well-crafted "subject", and a rich, detailed "message" body containing the requested points.
- When the user asks to play a song/artist, use PLAY_MUSIC with "query".
- When the user asks to search videos or topics in YouTube, use SEARCH_APP with "target": "YouTube" and "query".
- When the user asks to message someone on WhatsApp, use SEND_SMS with "target": "whatsapp", "recipient", and "message".

How to Output Actions:
Always reply with a brief, friendly confirmation text, followed by the action block at the very end:

```action
{"action": "COMPOSE_EMAIL", "target": "Gmail", "recipient": "jaypal1046@gmail.com", "subject": "Flutter vs React Discussion", "message": "Hi,\n\nHere are my thoughts on why Flutter is a solid foundation for modern app development and how React complements it...\n\nBest regards"}
```

Action Schema Examples:
- Open App:
  ```action
  {"action": "OPEN_APP", "target": "Gmail"}
  ```
- Compose Email:
  ```action
  {"action": "COMPOSE_EMAIL", "target": "Gmail", "recipient": "name@example.com", "subject": "Meeting Update", "message": "Here is the summary of our meeting."}
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
        // Match action block - capture JSON with action field inside code fences
        val actionBlockRegex = Regex("(?s)```(?:action|json)?\\s*(\\{.*?\"action\".*?\\})[\\s\\S]*?(?:```|$)")
        val match = actionBlockRegex.find(rawResponse)

        if (match != null) {
            val jsonStr = match.groupValues[1]
            val action = parseActionJson(jsonStr)
            val cleanText = rawResponse.replace(match.value, "")
                .replace(Regex("(?s)```(?:action|json)?[\\s\\S]*"), "")
                .trim()
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

        // Clean any leftover action fence if present
        val cleanedRaw = rawResponse.replace(Regex("(?s)```(?:action|json)?[\\s\\S]*"), "").trim()

        // Fallback intent detection for direct command phrases if model forgot action block
        val fallbackAction = detectDirectCommand(rawResponse)

        return ParsedResponse(
            userDisplayText = cleanedRaw.ifBlank { rawResponse.trim() },
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
