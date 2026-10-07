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
1. Dynamic App Resolution: Dynamically use the apps installed on the user's phone. Match target app names or package IDs strictly from installed capabilities, including minor user-input misspellings (e.g. "Gamil" -> "Gmail") when a unique installed-app match exists.
2. Direct Execution & Automation: Whenever the user asks you to perform an action, open an app, read/summarize emails or notifications, update details, or automate a workflow, ALWAYS generate the ```action JSON block at the end of your response so the phone executes the action immediately!
3. Multi-Turn Context & Task Continuity:
   - Conversations are continuous across turns. When the user provides a follow-up answer (e.g. selecting an app like "Gmail") or gives confirmation (e.g. "Yes", "Sure", "Proceed", "Go ahead", "Do it"), treat it as the final trigger for the task discussed in preceding turns.
   - DO NOT ask repetitive questions, DO NOT repeat the plan without acting, and DO NOT output generic acknowledgement without actions — IMMEDIATELY generate the ```action JSON block with the necessary steps to perform the requested workflow!
4. Avoid Redundant Clarification Loops: If the user has already approved or clarified their intent, immediately execute the task with the ```action block rather than asking "Does that sound good?" again.
5. Background & Recurring Monitoring: When the user requests periodic automation or recurring checks (e.g. "every hour", "hourly check when internet is available", "alert me daily"), use SCHEDULE_MONITOR with repeat_minutes (e.g. 60) and a descriptive title/query.
6. Ambiguity & Missing Parameters: If a command is missing mandatory parameters for hardware/app settings (e.g. phone number for calling), ask a concise question. However, for creative, generative, or document/report tasks, DO NOT ask clarifying questions—immediately take initiative to draft and create the full document!
7. Security & Sensitive Boundaries: For financial, banking, or payment applications, inform the user that sensitive financial transactions require direct user control.
8. Multi-step Requests: For requests that require multiple actions (e.g., opening an app and reading screen or setting up a schedule), return one action block with an "actions" array containing all steps.
9. Comprehensive Document & Report Generation (Word .docx, PowerPoint .pptx, Markdown .md, Text .txt, JSON .json, XML .xml):
   - When asked to create, draft, or write a document, report, presentation, or data file on ANY topic (e.g. "Create a report on AI Roadmap"), IMMEDIATELY draft and generate a comprehensive, in-depth, multi-paragraph document! DO NOT ask the user what sections to include; create a complete professional outline yourself.
   - Stream and show the rich report content in your chat response.
   - At the end of your response, ALWAYS include the ```action JSON block with `WRITE_FILE` containing the entire complete text in `content` so the file is saved with full data.
   - For Word (`.docx`): write complete sections (`# Title`, `## Executive Summary`, `## Detailed Analysis`, `## Milestones & Metrics`, `## Strategic Recommendations`) with bullet points and paragraphs.
   - For PowerPoint (`.pptx`): create a complete multi-slide deck (`Slide 1: Title\nSubtitle\nSlide 2: Executive Summary\n• Point 1\n• Point 2\nSlide 3: Roadmap & Milestones\n• Deliverable A\n• Deliverable B\nSlide 4: Key Metrics & Next Steps\n• Metric 1\n• Metric 2`).
10. Precision File Updates & Edits:
   - When asked to update, modify, or edit a file, explain the updates clearly.
   - Output `EDIT_FILE` targeting specific `target_content` with `replacement_content`, or `WRITE_FILE` with the full updated content.

CRITICAL EXECUTION RULE:
Whenever the user asks you to perform an action or confirms a plan, you MUST ALWAYS generate the ```action JSON block at the very end of your response so the phone performs the action immediately!
""".trimIndent()
    }

    fun parseContextual(messages: List<com.orbital.data.ChatMessage>, modelResponseText: String = ""): ParsedResponse {
        // 1. If modelResponseText has an action block or inline action, parse it directly
        if (modelResponseText.isNotBlank()) {
            val direct = parse(modelResponseText)
            if (direct.actions.isNotEmpty()) {
                return direct
            }
        }

        // 2. Extract dialogue context
        val nonSystemMessages = messages.filter { it.role != "system" }
        val lastUserMessage = nonSystemMessages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        val previousAssistant = nonSystemMessages.dropLast(1).lastOrNull { it.role == "assistant" }
        val earlierUserMessages = nonSystemMessages.dropLast(1).filter { it.role == "user" }

        // 3. Direct parse on the last user message
        if (lastUserMessage.isNotBlank()) {
            val directUserParse = parse(lastUserMessage)
            if (directUserParse.actions.isNotEmpty()) {
                return directUserParse
            }
        }

        // 4. Multi-turn continuity evaluation:
        // Check if user confirmed ("yes", "proceed", "do it", "sure", "ok", "go ahead")
        val isAffirmation = lastUserMessage.matches(
            Regex("(?i)^(yes|yep|yeah|yup|sure|ok|okay|do it|proceed|confirm|go ahead|please do|sounds good|yes please|right|correct|fine|all right|alright)$")
        )

        // Combine recent context (previous turns + model response text + last user message)
        val combinedContext = buildString {
            earlierUserMessages.takeLast(2).forEach { append("${it.content} ") }
            previousAssistant?.let { append("${it.content} ") }
            if (modelResponseText.isNotBlank()) append("$modelResponseText ")
            append(lastUserMessage)
        }.trim()

        if (combinedContext.isNotBlank()) {
            // Check if combined context has a natural intent
            val naturalAction = parseNaturalIntent(combinedContext)
            if (naturalAction != null) {
                return ParsedResponse(
                    userDisplayText = "Executing ${naturalAction.action.replace('_', ' ').lowercase()}...",
                    actions = listOf(naturalAction)
                )
            }

            // Check if previous assistant or user proposed opening an app or reading screen
            val openAppPattern = Regex("(?i)\\b(?:open|launch|start|reading|read|summarize|checking)\\s+(?:the\\s+)?([A-Za-z0-9\\s]{2,25}?)(?:\\s+app|\\s+and|\\s+for|\\s+to|$)")
            val match = openAppPattern.find(combinedContext)
            val extractedApp = match?.groupValues?.get(1)?.trim()

            if (isAffirmation && !extractedApp.isNullOrBlank() && extractedApp.length in 2..20 && !extractedApp.equals("the", ignoreCase = true)) {
                val actions = if (combinedContext.contains("read", ignoreCase = true) ||
                                 combinedContext.contains("screen", ignoreCase = true) ||
                                 combinedContext.contains("summarize", ignoreCase = true) ||
                                 combinedContext.contains("unread", ignoreCase = true)) {
                    listOf(
                        DeviceAction(action = "OPEN_APP", target = extractedApp),
                        DeviceAction(action = "READ_SCREEN")
                    )
                } else {
                    listOf(DeviceAction(action = "OPEN_APP", target = extractedApp))
                }
                return ParsedResponse(
                    userDisplayText = "Proceeding with opening $extractedApp...",
                    actions = actions
                )
            }
        }

        return parse(if (modelResponseText.isNotBlank()) modelResponseText else lastUserMessage)
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

        // Screenshot Intents (e.g. "take a screenshot", "capture screen", "screenshot")
        if (clean.matches(Regex(".*\\b(take\\s*a?\\s*screenshot|capture\\s*(the)?\\s*screen|screenshot|screen\\s*capture|snap\\s*(the)?\\s*screen)\\b.*"))) {
            return DeviceAction(action = "TAKE_SCREENSHOT")
        }

        // Screen Reading Intents (e.g. "read live screen", "read screen", "inspect screen", "scan screen")
        if (clean.matches(Regex(".*\\b(read\\s*(live)?\\s*screen|inspect\\s*screen|scan\\s*screen|what('?s|\\s+is)\\s*on\\s*my\\s*screen)\\b.*"))) {
            return DeviceAction(action = "READ_SCREEN")
        }

        // Accessibility Audit Intents (e.g. "audit current screen accessibility", "accessibility audit")
        if (clean.contains("audit") && (clean.contains("accessibility") || clean.contains("screen") || clean.contains("a11y"))) {
            return DeviceAction(action = "AUDIT_ACCESSIBILITY")
        }


        // File Open & Launch Intents (e.g. "open the file pitch.pptx", "open pitch.pptx", "launch file test.pdf")
        val openFileMatch = Regex(
            """(?:open|launch)\s+(?:the\s+)?(?:file|presentation|document|spreadsheet|sheet|pdf|docx?|pptx?|xlsx?)?\s*([^\s:]+\.[a-zA-Z0-9]{2,6})""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (openFileMatch != null) {
            val path = openFileMatch.groupValues[1].trim()
            return DeviceAction(action = "OPEN_FILE", path = path)
        }

        // File Reading Intents (e.g. "read the file /sdcard/Download/test.txt", "read presentation pitch.pptx", "inspect file doc.pdf")
        val readFileMatch = Regex(
            """(?:read|inspect|show\s+content\s+of|print)\s+(?:the\s+)?(?:file|presentation|document|spreadsheet|sheet|pdf|docx?|pptx?|xlsx?)?\s*([^\s:]+)""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (readFileMatch != null) {
            val path = readFileMatch.groupValues[1].trim()
            return DeviceAction(action = "READ_FILE", path = path)
        }

        // File Search Intents (e.g. "search for the word \"verified\" in /sdcard/Download/test.txt")
        val searchFileMatch = Regex(
            """(?:search|find)\s+(?:for\s+)?(?:the\s+word\s+)?["']?([\w\s]+?)["']?\s+in\s+([/\w\.\-]+)""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (searchFileMatch != null) {
            val word = searchFileMatch.groupValues[1].removeSurrounding("\"").removeSurrounding("'").trim()
            val path = searchFileMatch.groupValues[2].trim()
            return DeviceAction(action = "SEARCH_FILE", path = path, query = word)
        }

        // File Edit Intents (e.g. "edit the file ... by replacing \"foo\" with \"bar\"" or "update presentation ... replace foo with bar")
        val editFileMatch = Regex(
            """(?:edit|update|modify|change)\s+(?:the\s+)?(?:file|presentation|document|spreadsheet|sheet|slides)?\s*([^\s:]+\.[a-zA-Z0-9]+|[^\s:]+)\s+(?:by\s+replacing|replacing|replace)\s+["']?([^"'\n\r]+?)["']?\s+(?:with|to)\s+["']?([\s\S]+?)["']?(?:\s+(?:and|then)\s+(?:open\s+it|open\s+file|view\s+it))?$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (editFileMatch != null) {
            val path = editFileMatch.groupValues[1].trim()
            val target = editFileMatch.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
            val replace = editFileMatch.groupValues[3].trim().removeSurrounding("\"").removeSurrounding("'")
            return DeviceAction(action = "EDIT_FILE", path = path, targetContent = target, replacementContent = replace)
        }

        // Device / App Spotlight Search (e.g. "find Calculator app", "find the file test.txt")
        if (clean.startsWith("find ") || clean.startsWith("search for ")) {
            val q = text.substringAfter("find ").substringAfter("search for ").trim()
            return DeviceAction(action = "SEARCH_DEVICE", query = q)
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
            repeatMinutes = repeat,
            path = json.optString("path").takeIf { it.isNotBlank() }
                ?: json.optString("filePath").takeIf { it.isNotBlank() }
                ?: json.optString("file").takeIf { it.isNotBlank() },
            startLine = json.optInt("startLine", -1).takeIf { it > 0 }
                ?: json.optInt("start_line", -1).takeIf { it > 0 },
            endLine = json.optInt("endLine", -1).takeIf { it > 0 }
                ?: json.optInt("end_line", -1).takeIf { it > 0 },
            content = json.optString("content").takeIf { it.isNotBlank() }
                ?: json.optString("text").takeIf { it.isNotBlank() },
            targetContent = json.optString("targetContent").takeIf { it.isNotBlank() }
                ?: json.optString("target_content").takeIf { it.isNotBlank() }
                ?: json.optString("find").takeIf { it.isNotBlank() },
            replacementContent = json.optString("replacementContent").takeIf { it.isNotBlank() }
                ?: json.optString("replacement_content").takeIf { it.isNotBlank() }
                ?: json.optString("replaceWith").takeIf { it.isNotBlank() }
                ?: json.optString("replacement").takeIf { it.isNotBlank() },
            allowMultiple = json.takeIf { it.has("allowMultiple") }?.optBoolean("allowMultiple")
                ?: json.takeIf { it.has("allow_multiple") }?.optBoolean("allow_multiple"),
            isRegex = json.takeIf { it.has("isRegex") }?.optBoolean("isRegex")
                ?: json.takeIf { it.has("is_regex") }?.optBoolean("is_regex"),
            row = json.optInt("row", -1).takeIf { it >= 0 },
            col = json.optInt("col", -1).takeIf { it >= 0 }
                ?: json.optInt("column", -1).takeIf { it >= 0 },
            value = json.optString("value").takeIf { it.isNotBlank() },
            overwrite = json.takeIf { it.has("overwrite") }?.optBoolean("overwrite")
        )
    }
}
