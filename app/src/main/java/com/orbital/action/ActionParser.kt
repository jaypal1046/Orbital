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
1. Dynamic App Resolution: Dynamically use the apps installed on the user's phone. Match target app names or package IDs strictly from installed capabilities, including minor user-input misspellings when a unique installed-app match exists.
2. Direct Execution & Automation: Whenever the user asks you to perform an action, open an app, update/test details, read the screen, or automate a workflow, ALWAYS generate the ```action JSON block at the end of your response so the phone executes the action immediately!
3. Background & Recurring Monitoring: When the user requests periodic automation or recurring checks (e.g. "every hour", "hourly check when internet is available", "alert me daily"), use SCHEDULE_MONITOR with repeat_minutes (e.g. 60) and a descriptive title/query.
4. Ambiguity & Multiple Matches: If a user asks for a general task (e.g. "search train tickets", "play songs", "send a message") and multiple matching apps are installed on their phone without a clear preference, ask a quick, helpful clarifying question.
5. Clarify Missing Information: If mandatory parameters are missing to fulfill a task, ask a concise clarifying question first before generating an action.
6. Security & Sensitive Boundaries: For financial, banking, or payment applications, inform the user that sensitive financial transactions require direct user control.
7. Multi-step Requests: For requests that require multiple actions (e.g., opening an app and setting up a schedule or inspecting screen), return one action block with an "actions" array containing all steps. To run an action only when battery is low, add "if_battery_below" to that action.

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
            if (actions.isNotEmpty()) {
                val cleanText = rawResponse.replace(match.value, "")
                    .replace(Regex("(?s)```(?:action|json)?[\\s\\S]*"), "")
                    .trim()
                return ParsedResponse(
                    userDisplayText = cleanText.ifBlank { "Executing ${actions.firstOrNull()?.action ?: "task"}..." },
                    actions = actions
                )
            }
        }

        // Fallback: Check if response contains inline JSON or JSON array
        val inlineJsonRegex = Regex("(\\{(?:[^{}]*|\\{[^{}]*\\})*\"(?:action|actions)\"(?:[^{}]*|\\{[^{}]*\\})*\\})|(\\[\\s*\\{[\\s\\S]*?\\}\\s*\\])", RegexOption.IGNORE_CASE)
        val inlineMatch = inlineJsonRegex.find(rawResponse)
        if (inlineMatch != null) {
            val jsonStr = inlineMatch.value
            val actions = parseActionsJson(jsonStr)
            if (actions.isNotEmpty()) {
                val cleanText = rawResponse.replace(inlineMatch.value, "").trim()
                return ParsedResponse(
                    userDisplayText = cleanText.ifBlank { "Executing ${actions.first().action}..." },
                    actions = actions
                )
            }
        }

        // Natural Language Intent Heuristic Fallback (Offline / Zero-LLM resilience)
        val naturalAction = parseNaturalIntent(rawResponse)
        if (naturalAction != null) {
            return ParsedResponse(
                userDisplayText = "Executing ${naturalAction.action.replace('_', ' ').lowercase()}...",
                actions = listOf(naturalAction)
            )
        }

        // Clean any leftover action fence if present
        val cleanedRaw = rawResponse.replace(Regex("(?s)```(?:action|json)?[\\s\\S]*"), "").trim()

        return ParsedResponse(
            userDisplayText = cleanedRaw.ifBlank { rawResponse.trim() },
            actions = emptyList()
        )
    }

    fun parseNaturalIntent(text: String): DeviceAction? {
        val clean = text.lowercase().trim()
        
        // Battery & Device Status Intents
        if (clean.matches(Regex(".*\\b(batt(e|er)?y|battery\\s*status|device\\s*status|storage\\s*status|battery\\s*level|battery\\s*health|storage\\s*health|system\\s*health|phone\\s*status)\\b.*"))) {
            return DeviceAction(action = "DEVICE_STATUS")
        }

        // Connectivity Status Intents
        if (clean.matches(Regex(".*\\b(wifi\\s*status|bluetooth\\s*status|connectivity\\s*status|internet\\s*status|network\\s*status)\\b.*"))) {
            return DeviceAction(action = "CONNECTIVITY_STATUS")
        }

        // Flashlight Intents
        if (clean.contains("flashlight") || clean.contains("torch")) {
            val turnOff = clean.contains("off") || clean.contains("disable") || clean.contains("stop")
            return DeviceAction(action = "FLASHLIGHT", enabled = !turnOff)
        }

        // Sound mode intents
        if (clean.contains("silent mode") || clean.contains("mute")) {
            return DeviceAction(action = "SET_SOUND_MODE", target = "silent")
        }
        if (clean.contains("vibrate mode") || clean.contains("vibration")) {
            return DeviceAction(action = "SET_SOUND_MODE", target = "vibrate")
        }

        // Timer intents (e.g. "set a timer for 10 minutes", "timer 5 mins")
        val timerMatch = Regex("(?:set|start)?\\s*(?:a\\s*)?timer\\s*(?:for)?\\s*(\\d+)\\s*(min(?:ute)?s?|sec(?:ond)?s?|hours?)", RegexOption.IGNORE_CASE).find(clean)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 1
            val unit = timerMatch.groupValues[2].lowercase()
            val seconds = when {
                unit.startsWith("sec") -> amount
                unit.startsWith("hour") -> amount * 3600
                else -> amount * 60
            }
            return DeviceAction(action = "SET_TIMER", seconds = seconds, label = "Focus Timer")
        }

        return null
    }

    private fun parseActionsJson(jsonStr: String): List<DeviceAction> {
        val cleanJson = jsonStr.trim()
        return try {
            val json = JSONObject(cleanJson)
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
            try {
                val array = org.json.JSONArray(cleanJson)
                buildList {
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let(::parseActionJson)?.let(::add)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun parseActionJson(json: JSONObject): DeviceAction? {
        val actionType = json.optString("action").takeIf { it.isNotBlank() }
            ?: json.optString("type").takeIf { it.isNotBlank() }
            ?: return null

        val cleanAction = actionType.uppercase().trim()
        if (cleanAction.isBlank()) return null

        val repeat = json.optLong("repeat_minutes", -1).takeIf { it >= 15 }
            ?: json.optLong("repeatMinutes", -1).takeIf { it >= 15 }
            ?: json.optLong("interval_minutes", -1).takeIf { it >= 15 }
            ?: json.optLong("intervalMinutes", -1).takeIf { it >= 15 }
            ?: json.optLong("interval", -1).takeIf { it >= 15 }

        return DeviceAction(
            action = cleanAction,
            target = json.optString("target").takeIf { it.isNotBlank() }
                ?: json.optString("appName").takeIf { it.isNotBlank() }
                ?: json.optString("app").takeIf { it.isNotBlank() }
                ?: json.optString("name").takeIf { it.isNotBlank() }
                ?: json.optString("package").takeIf { it.isNotBlank() },
            query = json.optString("query").takeIf { it.isNotBlank() }
                ?: json.optString("search").takeIf { it.isNotBlank() }
                ?: json.optString("text").takeIf { it.isNotBlank() }
                ?: json.optString("q").takeIf { it.isNotBlank() },
            url = json.optString("url").takeIf { it.isNotBlank() }
                ?: json.optString("link").takeIf { it.isNotBlank() },
            seconds = json.optInt("seconds", -1).takeIf { it > 0 }
                ?: json.optInt("duration", -1).takeIf { it > 0 }
                ?: json.optInt("time", -1).takeIf { it > 0 },
            label = json.optString("label").takeIf { it.isNotBlank() }
                ?: json.optString("tag").takeIf { it.isNotBlank() },
            phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() }
                ?: json.optString("phone").takeIf { it.isNotBlank() },
            recipient = json.optString("recipient").takeIf { it.isNotBlank() }
                ?: json.optString("to").takeIf { it.isNotBlank() },
            subject = json.optString("subject").takeIf { it.isNotBlank() },
            message = json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("body").takeIf { it.isNotBlank() },
            title = json.optString("title").takeIf { it.isNotBlank() }
                ?: json.optString("label").takeIf { it.isNotBlank() },
            startTimeMillis = json.optLong("start_time_ms", -1).takeIf { it > 0 }
                ?: json.optLong("startTimeMillis", -1).takeIf { it > 0 },
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            hour = json.optInt("hour", -1).takeIf { it in 0..23 },
            minutes = json.optInt("minutes", -1).takeIf { it in 0..59 },
            enabled = json.takeIf { it.has("enabled") }?.optBoolean("enabled"),
            ifBatteryBelow = json.optInt("if_battery_below", -1).takeIf { it in 1..100 }
                ?: json.optInt("ifBatteryBelow", -1).takeIf { it in 1..100 },
            repeatMinutes = repeat
        )
    }
}
