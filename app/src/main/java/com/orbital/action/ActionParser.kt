package com.orbital.action

import org.json.JSONObject

data class ParsedResponse(
    val userDisplayText: String,
    val actions: List<DeviceAction> = emptyList()
) {
    val action: DeviceAction? get() = actions.firstOrNull()
}

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
6. SEND_SMS / WHATSAPP: Send messages or open WhatsApp for a contact / phone number.
7. SEARCH_WEB: Search Google / the web for any query.
8. OPEN_URL: Open any web link or URL in the browser.
9. SET_TIMER: Set timers or alarms (specify seconds).
10. OPEN_SETTING: Open device settings (e.g. "wifi", "bluetooth", "display", "battery", "sound", "apps", "settings").
11. DEVICE_STATUS: Check battery percentage, charging state, and device hardware info.
12. MAKE_CALL: Open phone dialer with a phone number.
13. CREATE_CALENDAR_EVENT: Open Calendar with title, start_time_ms, and notes.
14. SET_ALARM: Open Clock with hour, minutes, and label.
15. FLASHLIGHT: Turn the flashlight on or off (enabled: true/false).
16. SET_SOUND_MODE: Set target to silent, vibrate, or normal.
17. CONNECTIVITY_STATUS: Check Wi-Fi and Bluetooth status.
18. SCHEDULE_REMINDER: Schedule a reminder with label and repeat_minutes, or hour and minutes for a daily reminder.

For multi-step requests, return one action block with an "actions" array. To run an action only when battery is low, add "if_battery_below" to that action.

CRITICAL EXECUTION RULE:
Whenever the user asks you to perform an action (e.g. open an app, send a WhatsApp message, compose an email, set a timer, play music, open settings), you MUST ALWAYS generate the ```action JSON block at the very end of your response so the phone performs the action immediately!

Action Schema Examples:
- Open WhatsApp or Message Contact:
  ```action
  {"action": "SEND_SMS", "target": "WhatsApp", "recipient": "Arvind", "message": "Hi Arvind"}
  ```
- Open App:
  ```action
  {"action": "OPEN_APP", "target": "WhatsApp"}
  ```
- Set Timer:
  ```action
  {"action": "SET_TIMER", "seconds": 900, "label": "Focus Timer"}
  ```
- Open Gmail / Check or Summarize Emails:
  ```action
  {"action": "OPEN_APP", "target": "Gmail"}
  ```
- Compose Email:
  ```action
  {"action": "COMPOSE_EMAIL", "target": "Gmail", "recipient": "name@example.com", "subject": "Update", "message": "Hello,\n\nHere is the update.\n\nBest regards"}
  ```
- Play Song / YouTube:
  ```action
  {"action": "PLAY_MUSIC", "query": "lo-fi beats"}
  ```
- GPS Navigation:
  ```action
  {"action": "NAVIGATE", "query": "Nearest Coffee Shop"}
  ```
- Search in App or Browser (e.g. Chrome, YouTube, Play Store):
  ```action
  {"action": "SEARCH_APP", "target": "Chrome", "query": "Flutter"}
  ```
- Web Search:
  ```action
  {"action": "SEARCH_WEB", "query": "top tech breakthroughs"}
  ```
- Device Status:
  ```action
  {"action": "DEVICE_STATUS"}
  ```
- Open Setting:
  ```action
  {"action": "OPEN_SETTING", "target": "wifi"}
  ```
- Multi-step task:
  ```action
  {"actions": [{"action": "SET_TIMER", "seconds": 900, "label": "Focus"}, {"action": "PLAY_MUSIC", "target": "Spotify", "query": "lo-fi"}]}
  ```

Always be fast, helpful, and execute the requested action!
""".trimIndent()
    }

    fun parse(rawResponse: String): ParsedResponse {
        // Match action block - capture JSON with action field inside code fences
        val actionBlockRegex = Regex("(?s)```(?:action|json)?\\s*(.*?)\\s*```")
        val match = actionBlockRegex.find(rawResponse)

        if (match != null) {
            val jsonStr = match.groupValues[1]
            val actions = parseActionsJson(jsonStr)
            val cleanText = rawResponse.replace(match.value, "")
                .replace(Regex("(?s)```(?:action|json)?[\\s\\S]*"), "")
                .trim()
            return ParsedResponse(
                userDisplayText = cleanText.ifBlank { "Executing ${actions.firstOrNull()?.action ?: "task"}..." },
                actions = actions
            )
        }

        // Fallback: Check if response contains inline JSON with action field
        val inlineJsonRegex = Regex("(\\{[^{}]*\"action\"\\s*:\\s*\"[A-Z_]+\"[^{}]*\\})", RegexOption.IGNORE_CASE)
        val inlineMatch = inlineJsonRegex.find(rawResponse)
        if (inlineMatch != null) {
            val jsonStr = inlineMatch.groupValues[1]
            val actions = parseActionsJson(jsonStr)
            if (actions.isNotEmpty()) {
                val cleanText = rawResponse.replace(inlineMatch.value, "").trim()
                return ParsedResponse(
                    userDisplayText = cleanText.ifBlank { "Executing ${actions.first().action}..." },
                    actions = actions
                )
            }
        }

        // Clean any leftover action fence if present
        val cleanedRaw = rawResponse.replace(Regex("(?s)```(?:action|json)?[\\s\\S]*"), "").trim()

        return ParsedResponse(
            userDisplayText = cleanedRaw.ifBlank { rawResponse.trim() },
            actions = emptyList()
        )
    }

    private fun parseActionsJson(jsonStr: String): List<DeviceAction> {
        return try {
            val json = JSONObject(jsonStr)
            val steps = json.optJSONArray("actions")
            if (steps != null) {
                return buildList {
                    for (index in 0 until steps.length()) {
                        steps.optJSONObject(index)?.let(::parseActionJson)?.let(::add)
                    }
                }
            }
            listOfNotNull(parseActionJson(json))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseActionJson(json: JSONObject): DeviceAction? {
        val actionType = json.optString("action").uppercase()
        if (actionType.isBlank()) return null

        return DeviceAction(
            action = actionType,
            target = json.optString("target").takeIf { it.isNotBlank() } ?: json.optString("appName").takeIf { it.isNotBlank() },
            query = json.optString("query").takeIf { it.isNotBlank() } ?: json.optString("search").takeIf { it.isNotBlank() },
            url = json.optString("url").takeIf { it.isNotBlank() } ?: json.optString("link").takeIf { it.isNotBlank() },
            seconds = json.optInt("seconds", -1).takeIf { it > 0 },
            label = json.optString("label").takeIf { it.isNotBlank() },
            phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() } ?: json.optString("phone").takeIf { it.isNotBlank() },
            recipient = json.optString("recipient").takeIf { it.isNotBlank() } ?: json.optString("to").takeIf { it.isNotBlank() },
            subject = json.optString("subject").takeIf { it.isNotBlank() },
            message = json.optString("message").takeIf { it.isNotBlank() } ?: json.optString("body").takeIf { it.isNotBlank() },
            title = json.optString("title").takeIf { it.isNotBlank() },
            startTimeMillis = json.optLong("start_time_ms", -1).takeIf { it > 0 },
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            hour = json.optInt("hour", -1).takeIf { it in 0..23 },
            minutes = json.optInt("minutes", -1).takeIf { it in 0..59 },
            enabled = json.takeIf { it.has("enabled") }?.optBoolean("enabled"),
            ifBatteryBelow = json.optInt("if_battery_below", -1).takeIf { it in 1..100 },
            repeatMinutes = json.optLong("repeat_minutes", -1).takeIf { it >= 15 }
        )
    }
}
