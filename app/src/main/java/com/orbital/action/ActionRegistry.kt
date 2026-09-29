package com.orbital.action

data class ActionDefinition(
    val type: String,
    val description: String,
    val requiredParams: List<String> = emptyList(),
    val optionalParams: List<String> = emptyList(),
    val exampleJson: String
)

object ActionRegistry {

    val allActions: List<ActionDefinition> = listOf(
        ActionDefinition(
            type = "OPEN_APP",
            description = "Launch any installed app on the device dynamically",
            requiredParams = listOf("target"),
            exampleJson = """{"action": "OPEN_APP", "target": "WhatsApp"}"""
        ),
        ActionDefinition(
            type = "SEARCH_APP",
            description = "Search inside any installed application or specific service",
            requiredParams = listOf("target", "query"),
            exampleJson = """{"action": "SEARCH_APP", "target": "YouTube", "query": "lo-fi beats"}"""
        ),
        ActionDefinition(
            type = "NAVIGATE",
            description = "Open turn-by-turn GPS navigation or search places on Maps",
            requiredParams = listOf("query"),
            exampleJson = """{"action": "NAVIGATE", "query": "Nearest Coffee Shop"}"""
        ),
        ActionDefinition(
            type = "PLAY_MUSIC",
            description = "Search and stream music, songs, or artists on any installed media app",
            requiredParams = listOf("query"),
            optionalParams = listOf("target"),
            exampleJson = """{"action": "PLAY_MUSIC", "query": "chill acoustic"}"""
        ),
        ActionDefinition(
            type = "COMPOSE_EMAIL",
            description = "Open the device email app with recipient, subject, and body pre-filled",
            optionalParams = listOf("recipient", "subject", "message", "target"),
            exampleJson = """{"action": "COMPOSE_EMAIL", "recipient": "contact@example.com", "subject": "Meeting", "message": "Hello,\nHere is the update."}"""
        ),
        ActionDefinition(
            type = "SEND_SMS",
            description = "Send message or open chat in WhatsApp or default messaging app",
            requiredParams = listOf("recipient"),
            optionalParams = listOf("message", "target"),
            exampleJson = """{"action": "SEND_SMS", "target": "WhatsApp", "recipient": "Arvind", "message": "Hey Arvind"}"""
        ),
        ActionDefinition(
            type = "SEARCH_WEB",
            description = "Search the web using the user's default or requested browser",
            requiredParams = listOf("query"),
            optionalParams = listOf("target"),
            exampleJson = """{"action": "SEARCH_WEB", "query": "latest space discoveries"}"""
        ),
        ActionDefinition(
            type = "OPEN_URL",
            description = "Open any web link or URL in the browser",
            requiredParams = listOf("url"),
            exampleJson = """{"action": "OPEN_URL", "url": "https://github.com"}"""
        ),
        ActionDefinition(
            type = "SET_TIMER",
            description = "Set countdown timers with duration in seconds",
            requiredParams = listOf("seconds"),
            optionalParams = listOf("label"),
            exampleJson = """{"action": "SET_TIMER", "seconds": 900, "label": "Focus Timer"}"""
        ),
        ActionDefinition(
            type = "SET_ALARM",
            description = "Set an alarm on the device clock with hour, minutes, and label",
            requiredParams = listOf("hour", "minutes"),
            optionalParams = listOf("label"),
            exampleJson = """{"action": "SET_ALARM", "hour": 7, "minutes": 30, "label": "Morning Wakeup"}"""
        ),
        ActionDefinition(
            type = "CREATE_CALENDAR_EVENT",
            description = "Open device Calendar to create an event with title, time, and notes",
            requiredParams = listOf("title"),
            optionalParams = listOf("start_time_ms", "notes"),
            exampleJson = """{"action": "CREATE_CALENDAR_EVENT", "title": "Project Review", "notes": "Sprint progress discussion"}"""
        ),
        ActionDefinition(
            type = "FLASHLIGHT",
            description = "Turn device torch / flashlight on or off",
            requiredParams = listOf("enabled"),
            exampleJson = """{"action": "FLASHLIGHT", "enabled": true}"""
        ),
        ActionDefinition(
            type = "SET_SOUND_MODE",
            description = "Set phone ringer mode to 'silent', 'vibrate', or 'normal'",
            requiredParams = listOf("target"),
            exampleJson = """{"action": "SET_SOUND_MODE", "target": "vibrate"}"""
        ),
        ActionDefinition(
            type = "DEVICE_STATUS",
            description = "Check real-time battery percentage, charging state, and storage info",
            exampleJson = """{"action": "DEVICE_STATUS"}"""
        ),
        ActionDefinition(
            type = "CONNECTIVITY_STATUS",
            description = "Check real-time Wi-Fi and Bluetooth connection statuses",
            exampleJson = """{"action": "CONNECTIVITY_STATUS"}"""
        ),
        ActionDefinition(
            type = "OPEN_SETTING",
            description = "Open device settings (wifi, bluetooth, battery, display, sound, apps, etc.)",
            requiredParams = listOf("target"),
            exampleJson = """{"action": "OPEN_SETTING", "target": "wifi"}"""
        ),
        ActionDefinition(
            type = "MAKE_CALL",
            description = "Open phone dialer with a phone number",
            requiredParams = listOf("phoneNumber"),
            exampleJson = """{"action": "MAKE_CALL", "phoneNumber": "+1234567890"}"""
        ),
        ActionDefinition(
            type = "SCHEDULE_REMINDER",
            description = "Schedule automated background notifications & reminders",
            requiredParams = listOf("label"),
            optionalParams = listOf("repeat_minutes", "hour", "minutes"),
            exampleJson = """{"action": "SCHEDULE_REMINDER", "label": "Drink water", "repeat_minutes": 120}"""
        ),
        ActionDefinition(
            type = "SEARCH_TRAIN",
            description = "Search live train running status, schedules, and transit info via installed travel apps or web",
            requiredParams = listOf("query"),
            optionalParams = listOf("target"),
            exampleJson = """{"action": "SEARCH_TRAIN", "query": "12951 Mumbai Rajdhani"}"""
        ),
        ActionDefinition(
            type = "READ_SCREEN",
            description = "Read and analyze all visible UI elements, text, buttons, and state of the active foreground app screen",
            exampleJson = """{"action": "READ_SCREEN"}"""
        ),
        ActionDefinition(
            type = "CLICK_ELEMENT",
            description = "Click or tap a specific button, tab, link, or item by its visible label on the active screen",
            requiredParams = listOf("target"),
            exampleJson = """{"action": "CLICK_ELEMENT", "target": "Find trains"}"""
        ),
        ActionDefinition(
            type = "INPUT_TEXT",
            description = "Type text into a designated input field or search bar on the active screen",
            requiredParams = listOf("query"),
            optionalParams = listOf("target"),
            exampleJson = """{"action": "INPUT_TEXT", "target": "From station", "query": "Mumbai LTT"}"""
        ),
        ActionDefinition(
            type = "SCROLL",
            description = "Scroll the active screen up or down to reveal more items or information",
            optionalParams = listOf("target"),
            exampleJson = """{"action": "SCROLL", "target": "down"}"""
        ),
        ActionDefinition(
            type = "PERFORM_TESTING",
            description = "Launch target app, explore active screen structure, interact with primary buttons, and perform automated screen verification",
            requiredParams = listOf("target"),
            optionalParams = listOf("query"),
            exampleJson = """{"action": "PERFORM_TESTING", "target": "App Name", "query": "Search query or button label"}"""
        ),
        ActionDefinition(
            type = "SCHEDULE_MONITOR",
            description = "Schedule autonomous background cron monitoring for periodic alerts or status checks via WorkManager",
            requiredParams = listOf("query"),
            optionalParams = listOf("title", "hour", "minutes", "repeat_minutes", "target"),
            exampleJson = """{"action": "SCHEDULE_MONITOR", "title": "Daily Status Alert", "query": "Status query details", "hour": 8, "minutes": 0, "target": "App Name"}"""
        ),
        ActionDefinition(
            type = "LIST_MONITORS",
            description = "List all currently active background cron monitoring jobs and scheduled alerts",
            exampleJson = """{"action": "LIST_MONITORS"}"""
        ),
        ActionDefinition(
            type = "CANCEL_MONITOR",
            description = "Cancel an active background cron monitoring task by its task ID or query keyword",
            requiredParams = listOf("target"),
            exampleJson = """{"action": "CANCEL_MONITOR", "target": "train_12951"}"""
        )
    )

    fun findDefinition(type: String): ActionDefinition? {
        val clean = type.uppercase().trim()
        return allActions.firstOrNull { it.type == clean }
    }

    fun getAction(type: String): ActionDefinition? = findDefinition(type)

    /**
     * Dynamically builds the tools documentation and action schema examples for the LLM system prompt.
     */
    fun buildToolsDocumentation(): String {
        return buildString {
            append("Available Device Tools & Actions:\n")
            allActions.forEachIndexed { index, def ->
                append("${index + 1}. ${def.type}: ${def.description}\n")
            }
            append("\nAction Schema Examples:\n")
            allActions.take(8).forEach { def ->
                append("- ${def.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}:\n")
                append("  ```action\n  ${def.exampleJson}\n  ```\n")
            }
            append("- Multi-step Chained Actions:\n")
            append("  ```action\n  {\"actions\": [{\"action\": \"SET_TIMER\", \"seconds\": 600}, {\"action\": \"PLAY_MUSIC\", \"query\": \"focus\"}]}\n  ```\n")
        }
    }
}
