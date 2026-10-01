package com.orbital.skills

import androidx.compose.ui.graphics.Color

enum class SkillCategory(val displayName: String, val icon: String, val accentColor: Color) {
    AUTOMATION("Automation & Actions", "🤖", Color(0xFF8B5CF6)),
    VISION("Screen & Vision AI", "👁️", Color(0xFF38BDF8)),
    SYSTEM("System & Hardware", "⚙️", Color(0xFF10B981)),
    COMMUNICATION("Communication & Messaging", "💬", Color(0xFFF59E0B)),
    RESEARCH("Search & Research", "🌐", Color(0xFFEC4899))
}

data class MobileSkill(
    val id: String,
    val name: String,
    val category: SkillCategory,
    val description: String,
    val detailedInstructions: String,
    val examplePrompts: List<String>,
    val icon: String,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true
)
