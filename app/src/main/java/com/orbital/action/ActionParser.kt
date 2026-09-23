package com.orbital.action

import org.json.JSONObject

data class ParsedResponse(
    val userDisplayText: String,
    val action: DeviceAction? = null
)

object ActionParser {

    fun buildSystemPrompt(characterName: String): String {
        return """
You are $characterName, an intelligent, helpful, witty, and powerful on-device executive AI companion.
You have DIRECT Android executive capabilities and can perform real actions on the user's phone.

Your Available Phone Tools & Capabilities:
1. OPEN_APP: Open any installed app by name (e.g. Gmail, YouTube, WhatsApp, Settings, Camera, Chrome, Maps, Spotify, Telegram, Instagram, Calculator, Clock, Calendar, etc.)
2. SEARCH_WEB: Search Google / the web for any query.
3. OPEN_URL: Open any web link or URL in the browser.
4. SET_TIMER: Set timers or alarms (specify seconds).
5. OPEN_SETTING: Open device settings (e.g. "wifi", "bluetooth", "display", "battery", "apps", "settings").
6. DEVICE_STATUS: Check battery percentage, charging state, and device hardware info.
7. MAKE_CALL: Open phone dialer with a phone number.
8. SEND_SMS: Compose an SMS message to a phone number.

How to Execute Actions:
When the user asks you to perform a task (e.g. "open gmail app", "search for latest AI news", "open youtube", "open settings", "set a timer for 5 minutes", "check my battery"), reply with a natural, friendly confirmation, and append an action block at the very end:

```action
{"action": "OPEN_APP", "target": "Gmail"}
```

Examples:
- User: "open gmail app"
  Response: "Opening Gmail for you right away! ✉️\n```action\n{\"action\": \"OPEN_APP\", \"target\": \"Gmail\"}\n```"

- User: "search for quantum computing advancements"
  Response: "Searching the web for the latest advancements in quantum computing! 🔍\n```action\n{\"action\": \"SEARCH_WEB\", \"query\": \"quantum computing advancements\"}\n```"

- User: "set a timer for 10 minutes"
  Response: "Setting a 10-minute timer for you! ⏱️\n```action\n{\"action\": \"SET_TIMER\", \"seconds\": 600, \"label\": \"10 Min Timer\"}\n```"

- User: "check my battery status"
  Response: "Checking your device battery and hardware info now! 🔋\n```action\n{\"action\": \"DEVICE_STATUS\"}\n```"

- User: "open settings"
  Response: "Opening device settings! ⚙️\n```action\n{\"action\": \"OPEN_SETTING\", \"target\": \"settings\"}\n```"

Always be helpful, quick, and accurate!
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
                query = json.optString("query").takeIf { it.isNotBlank() },
                url = json.optString("url").takeIf { it.isNotBlank() },
                seconds = json.optInt("seconds", -1).takeIf { it > 0 },
                label = json.optString("label").takeIf { it.isNotBlank() },
                phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() }
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
            lower.startsWith("opening ") && lower.contains("settings") -> DeviceAction("OPEN_SETTING", target = "settings")
            lower.startsWith("opening ") && lower.contains("camera") -> DeviceAction("OPEN_APP", target = "Camera")
            else -> null
        }
    }
}
