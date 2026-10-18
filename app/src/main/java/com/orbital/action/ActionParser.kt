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
8. Multi-step Requests: Return an "actions" array only when each next step is valid for the expected observed state. When later steps depend on an unknown screen, execute one step and use the observed state on the next turn.
9. Comprehensive Document & Report Generation (Word .docx, PowerPoint .pptx, Markdown .md, Text .txt, JSON .json, XML .xml):
   - When asked to create, draft, or write a document, report, presentation, or data file on ANY topic (e.g. "Create a report on AI Roadmap"), IMMEDIATELY draft and generate a comprehensive, in-depth, multi-paragraph document! DO NOT ask the user what sections to include; create a complete professional outline yourself.
   - Stream and show the rich report content in your chat response.
   - At the end of your response, ALWAYS include the ```action JSON block with `WRITE_FILE` containing the entire complete text in `content` so the file is saved with full data.
   - For Word (`.docx`): write complete sections (`# Title`, `## Executive Summary`, `## Detailed Analysis`, `## Milestones & Metrics`, `## Strategic Recommendations`) with bullet points and paragraphs.
   - For PowerPoint (`.pptx`): create a complete multi-slide deck (`Slide 1: Title\nSubtitle\nSlide 2: Executive Summary\n• Point 1\n• Point 2\nSlide 3: Roadmap & Milestones\n• Deliverable A\n• Deliverable B\nSlide 4: Key Metrics & Next Steps\n• Metric 1\n• Metric 2`).
10. Precision File Updates & Edits:
   - When asked to update, modify, or edit a file, explain the updates clearly.
   - Output `EDIT_FILE` targeting specific `target_content` with `replacement_content`, or `WRITE_FILE` with the full updated content.
11. App Testing Continuity:
   - Treat follow-up requests in this chat as continuation of the current test unless the user changes the goal.
   - Use previous and current screen states, their changes, and action outcomes to decide the next step. Never claim a step passed without an observed result.
   - If the screen does not match the expected flow, inspect the actual UI before choosing another action. Report the blocked step and observed state when progress stops.
   - In multi-action app tests, include `expected_ui_text` on each action when a visible result is known. If the result is unknown, issue one action, inspect the new screen, then plan the next action.
12. Forms:
   - Read the live screen before filling a form. Use one `INPUT_TEXT` action per editable field, with `target` copied from its observed label, hint, or resource ID and `query` taken from the user's supplied data.
   - If a required value or field identity is missing, ask for that specific information. Do not guess values or type into an unrelated field. Fill fields first; submit only when requested.
   - When chaining field entries, set `expected_ui_text` only if the entered value should remain visible. Otherwise enter one field, observe its result, then continue.
13. App testing safety:
   - For a request to test an app, use PERFORM_TESTING with the installed app target; its tester will inspect each reached screen and handle safe navigation. Do not output a batch of speculative taps.
   - Use only passwords supplied by the user in this chat. Never invent credentials or repeat them in reports.
   - Stop and report controls that submit, delete, purchase, or change account data. Stop when a control's effect is uncertain.
   - Report only screens actually observed; do not claim an app was fully tested after a single screen inspection.

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

        // Only an unexecuted assistant proposal can be confirmed. Older requests have already run.
        val combinedContext = buildString {
            if (isAffirmation && previousAssistant?.actionLabel == null) {
                append(previousAssistant?.content.orEmpty())
                append(' ')
                append(lastUserMessage)
            }
        }.trim()

        if (combinedContext.isNotBlank()) {
            // Check if combined context has a natural intent or compound intents
            val compoundContextActions = parseCompoundIntents(combinedContext)
            if (compoundContextActions.isNotEmpty()) {
                val label = compoundContextActions.first().action.replace('_', ' ').lowercase()
                return ParsedResponse(
                    userDisplayText = "Executing $label...",
                    actions = compoundContextActions
                )
            }
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
        val compoundActions = parseCompoundIntents(rawResponse)
        if (compoundActions.isNotEmpty()) {
            val label = compoundActions.first().action.replace('_', ' ').lowercase()
            return ParsedResponse(
                userDisplayText = "Executing $label...",
                actions = compoundActions
            )
        }

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

    fun parseCompoundIntents(text: String, fallbackContextTarget: String? = null): List<DeviceAction> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        val actionVerbLookahead = """\b(?:and(?:\s+then)?|then|,)\s+(?=(?:open|launch|start|type|enter|input|fill|test|auto[- ]?test|screen[- ]?test|perform[- ]?testing|click|tap|press|scroll|search|take\s+screenshot|read\s+screen|set\s+timer|turn\s+on|turn\s+off)\b)"""
        val clauses = trimmed.split(Regex(actionVerbLookahead, RegexOption.IGNORE_CASE))
            .map { it.trim().removePrefix("and ").removePrefix("then ").trim() }
            .filter { it.isNotBlank() }

        val actions = mutableListOf<DeviceAction>()
        var activeTarget = fallbackContextTarget

        for (clause in clauses) {
            val action = parseNaturalIntent(clause, contextTarget = activeTarget)
            if (action != null) {
                if (!action.target.isNullOrBlank() && action.action in setOf("OPEN_APP", "LAUNCH_APP", "PERFORM_TESTING")) {
                    activeTarget = action.target
                }
                actions.add(action)
            }
        }

        return actions
    }

    fun parseNaturalIntent(text: String, contextTarget: String? = null): DeviceAction? {
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

        // App Testing Intents (e.g. "test that app", "test the app", "test Encyra", "test app Encyra", "auto test Encyra")
        val testAppMatch = Regex(
            """^(?:test|auto[- ]?test|screen[- ]?test|perform[- ]?testing(?:\s+on)?)\s+(?:the\s+app|that\s+app|app\s+([A-Za-z0-9\s._-]+)|([A-Za-z0-9\s._-]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (testAppMatch != null) {
            val appParam = (testAppMatch.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: testAppMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() })?.trim()
            val target = if (!appParam.isNullOrBlank() && !appParam.equals("that app", ignoreCase = true) && !appParam.equals("the app", ignoreCase = true)) {
                appParam
            } else {
                contextTarget
            }
            return DeviceAction(action = "PERFORM_TESTING", target = target)
        }

        // Text Input / Typing Intents (e.g. "type 4444 password", "enter 4444 into password field", "type 4444", "input hello in search")
        val inputMatch = Regex(
            """^(?:type|enter|input|fill(?:\s+in)?)\s+(?:the\s+)?(?:password|pin|code|text)?\s*["']?([^"'\n\r]+?)["']?(?:\s+(?:into|in|for|as)?\s+(?:the\s+)?(password|pin|code|search|field|[A-Za-z0-9._-]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (inputMatch != null) {
            val query = inputMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
            val rawTarget = inputMatch.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            if (query.isNotBlank()) {
                return DeviceAction(action = "INPUT_TEXT", query = query, target = rawTarget)
            }
        }

        // Click / Tap Element Intents (e.g. "click login", "tap submit button", "press ok")
        val clickMatch = Regex(
            """^(?:click|tap|press)\s+(?:on\s+)?(?:the\s+)?["']?([^"'\n\r]+?)["']?(?:\s+(?:button|icon|tab|link))?$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (clickMatch != null) {
            val target = clickMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
            if (target.isNotBlank() && !target.equals("the", ignoreCase = true)) {
                return DeviceAction(action = "CLICK_ELEMENT", target = target)
            }
        }

        // Email Smart Staging Intents (e.g. "reply to this email saying I accept", "reply saying will send tomorrow", "stage reply on outlook saying ...")
        val replyEmailMatch = Regex(
            """^(?:stage\s+reply|reply(?:\s+to\s+this\s+email)?|reply\s+to\s+email|answer\s+email)(?:\s+on\s+([A-Za-z0-9\s]+?))?\s+(?:saying|with|that)\s+["']?(.+?)["']?$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (replyEmailMatch != null) {
            val targetClient = replyEmailMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
            val draft = replyEmailMatch.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
            if (draft.isNotBlank()) {
                return DeviceAction(action = "STAGE_EMAIL_REPLY", message = draft, target = targetClient)
            }
        }

        // Generic reply to email (e.g. "reply to active email", "reply to this email", "stage email reply")
        if (clean.matches(Regex("""^(?:stage\s+)?(?:reply|respond|answer)(?:\s+to)?(?:\s+this|\s+the|\s+active)?(?:\s+email|\s+thread)?(?:\s+on\s+([A-Za-z0-9\s]+?))?$"""))) {
            return DeviceAction(
                action = "STAGE_EMAIL_REPLY",
                message = "Thank you for your email. I have reviewed the thread and will follow up shortly."
            )
        }

        // Send Email Confirmation Intent (e.g. "send the email", "send email now", "confirm and send")
        if (clean.matches(Regex(".*\\b(send\\s+(the\\s+)?email(\\s+now)?|send\\s+it\\s+now|confirm\\s+and\\s+send)\\b.*"))) {
            return DeviceAction(action = "DISPATCH_EMAIL_SEND")
        }

        // Scroll Intents (e.g. "scroll down", "scroll up")
        val scrollMatch = Regex("""^scroll\s+(down|up)$""", RegexOption.IGNORE_CASE).find(clean)
        if (scrollMatch != null) {
            return DeviceAction(action = "SCROLL", target = scrollMatch.groupValues[1].lowercase())
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

        // App Launch / Open Intents (e.g. "open Encyra", "launch Chrome", "open WhatsApp", "start Camera")
        val openAppMatch = Regex(
            """^(?:open|launch|start)\s+(?:the\s+)?([A-Za-z0-9\s._-]+?)(?:\s+app)?$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim())
        if (openAppMatch != null) {
            val appTarget = openAppMatch.groupValues[1].trim()
            if (appTarget.isNotBlank() && !appTarget.contains(".") && !appTarget.equals("the", ignoreCase = true) && !appTarget.equals("app", ignoreCase = true)) {
                return DeviceAction(action = "OPEN_APP", target = appTarget)
            }
        }

        // File Reading Intents (e.g. "read the file /sdcard/Download/test.txt", "read presentation pitch.pptx", "inspect file doc.pdf")
        val readFileMatch = Regex(
            """(?:read|inspect|show\s+content\s+of|print)\s+(?:the\s+)?(?:(?:file|presentation|document|spreadsheet|sheet|pdf|docx?|pptx?|xlsx?)\s+)?([^\s:]+\.[a-zA-Z0-9]{2,6}|/[^\s:]+)""",
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
            overwrite = json.takeIf { it.has("overwrite") }?.optBoolean("overwrite"),
            expectedUiText = json.optString("expected_ui_text").takeIf { it.isNotBlank() }
        )
    }
}
