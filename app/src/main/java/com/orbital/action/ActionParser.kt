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
        val toolsDoc = ActionRegistry.buildToolsDocumentation()
        return """
You are $characterName, an intelligent, helpful, witty, and powerful client-side executive AI companion.
You have DIRECT Android executive capabilities and can perform real actions on the user's phone.
$capabilityContext

$toolsDoc

SMART ACTION SELECTION RULES:
1. When the user asks to test an app, perform testing, verify buttons, or open and test an application (e.g. "open where is my train and perform basic testing"), you MUST use PERFORM_TESTING:
   ```action
   {"action": "PERFORM_TESTING", "target": "Where is my Train", "query": "Find trains"}
   ```
2. When the user asks to search trains or transit, use SEARCH_TRAIN:
   ```action
   {"action": "SEARCH_TRAIN", "query": "12951 Mumbai Rajdhani"}
   ```
3. If a user asks to perform an action but is missing mandatory details (e.g., asking "check train status" without specifying train number or route), ask a concise clarifying question first. Only generate the ```action block once sufficient details are known.
4. For multi-step requests, return one action block with an "actions" array. To run an action only when battery is low, add "if_battery_below" to that action.

CRITICAL EXECUTION RULE:
Whenever the user asks you to perform an action, you MUST ALWAYS generate the ```action JSON block at the very end of your response so the phone performs the action immediately!
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
