package com.orbital.skills

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MobileSkillRegistry @Inject constructor() {

    private val defaultSkills: List<MobileSkill> = listOf(
        MobileSkill(
            id = "device-automation",
            name = "Android Device Automator",
            category = SkillCategory.AUTOMATION,
            icon = "🤖",
            description = "Hands-free screen navigation, taps, text entry, and multi-step app workflows.",
            detailedInstructions = """
### Skill: Android Device Automator
- When asked to operate the phone, inspect visible UI components via Accessibility.
- Chain related actions in sequence using the `actions` array.
- Avoid hardcoded coordinates; use semantic view titles and accessibility labels.
- Execute confirmations gracefully before triggering destructive steps.
""".trimIndent(),
            examplePrompts = listOf(
                "Open an installed app and search for a topic",
                "Open a system setting and inspect its current value",
                "Set a focus timer"
            )
        ),
        MobileSkill(
            id = "screen-vision",
            name = "Live Vision & Screen Reader",
            category = SkillCategory.VISION,
            icon = "👁️",
            description = "Reads, parses, and summarizes everything visible on your current active phone screen.",
            detailedInstructions = """
### Skill: Live Vision & Screen Reader
- Inspect live UI hierarchies and extract actionable text, tables, and buttons.
- Summarize long articles or messages currently open on screen.
- Identify actionable items like tracking numbers, order totals, or incoming emails.
""".trimIndent(),
            examplePrompts = listOf(
                "Inspect my screen and summarize what's open",
                "Read the article on my screen and give key takeaways",
                "Find the confirm button on this screen"
            )
        ),
        MobileSkill(
            id = "system-controller",
            name = "Hardware & System Controller",
            category = SkillCategory.SYSTEM,
            icon = "⚡",
            description = "Toggles flashlight, Wi-Fi, Bluetooth, volume levels, alarms, and battery health checks.",
            detailedInstructions = """
### Skill: Hardware & System Controller
- Instantly trigger hardware toggles (FLASHLIGHT, WIFI, BLUETOOTH, VOLUME, ALARM).
- Check battery percentage, charging state, and system storage capacity.
- Advise power-saving actions when battery is below 20%.
""".trimIndent(),
            examplePrompts = listOf(
                "Turn on flashlight",
                "Check device battery & storage health",
                "Set an alarm for 7:30 AM tomorrow"
            )
        ),
        MobileSkill(
            id = "smart-messaging",
            name = "Communication & Messaging",
            category = SkillCategory.COMMUNICATION,
            icon = "💬",
            description = "Drafts and sends messages or emails with dynamic contact resolution.",
            detailedInstructions = """
### Skill: Communication & Messaging
- Resolve recipient names dynamically without hardcoded phone numbers.
- Ask for confirmation if sending messages to real recipients.
- Compose clear, concise message bodies tailored to the companion mascot tone.
""".trimIndent(),
            examplePrompts = listOf(
                "Draft a message for a contact",
                "Compose an SMS for a contact",
                "Draft an email"
            )
        ),
        MobileSkill(
            id = "web-research",
            name = "Real-Time Web Researcher",
            category = SkillCategory.RESEARCH,
            icon = "🌐",
            description = "Executes real-time searches, extracts verified news, and answers complex questions.",
            detailedInstructions = """
### Skill: Real-Time Web Researcher
- Provide up-to-date facts, tech releases, and live data.
- Structure information with clean markdown bullet points and clear takeaways.
- Include source context and avoid speculative hallucinations.
""".trimIndent(),
            examplePrompts = listOf(
                "What are the top AI breakthroughs this week?",
                "Search latest price and specs of Snapdragon 8 Elite",
                "Summarize recent space exploration news"
            )
        )
    )

    private val _skills = MutableStateFlow(defaultSkills)
    val skills: StateFlow<List<MobileSkill>> = _skills.asStateFlow()

    fun getActiveSkillsPrompt(): String {
        val active = _skills.value.filter { it.isEnabled }
        if (active.isEmpty()) return ""

        val sb = StringBuilder("\n\n[Active Mobile Skills]:\n")
        active.forEach { skill ->
            sb.append("• Skill: ${skill.name} (${skill.icon})\n")
            sb.append("  ${skill.description}\n")
        }
        return sb.toString()
    }

    fun toggleSkill(skillId: String) {
        _skills.value = _skills.value.map {
            if (it.id == skillId) it.copy(isEnabled = !it.isEnabled) else it
        }
    }
}
