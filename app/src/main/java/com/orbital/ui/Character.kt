package com.orbital.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.orbital.R

/**
 * Type-safe definition of AI Companion characters with sprite asset verification,
 * metadata, and theme fallbacks.
 */
sealed class Character(
    val id: String,
    val name: String,
    val emoji: String,
    val title: String,
    val description: String,
    val hasSprites: Boolean,
    val gradientColors: List<Color>,
    @DrawableRes val heroDrawableRes: Int,
    val fallbackTheme: CharacterTheme = CharacterTheme.AETHER
) {
    enum class CharacterTheme {
        AETHER, LUMY
    }

    object Aether : Character(
        id = "aether",
        name = "Aether",
        emoji = "🌌",
        title = "Cosmic Ethereal Companion",
        description = "Light, floating celestial spirit with nebula rings. Clean, witty, and premium.",
        hasSprites = true,
        gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6)),
        heroDrawableRes = R.drawable.aether_hero,
        fallbackTheme = CharacterTheme.AETHER
    )

    object Lumy : Character(
        id = "lumy",
        name = "Lumy",
        emoji = "✨",
        title = "Gentle Light Spirit",
        description = "Soft, glowing light spirit with expressive anime warmth and cheerful energy.",
        hasSprites = true,
        gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFEC4899)),
        heroDrawableRes = R.drawable.lumy_hero,
        fallbackTheme = CharacterTheme.LUMY
    )

    object Nexus : Character(
        id = "nexus",
        name = "Nexus",
        emoji = "🔮",
        title = "Cybernetic Holographic Core",
        description = "Modern AI orb with pulsing data rings and geometric cyber laser abilities.",
        hasSprites = false,
        gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF7C3AED)),
        heroDrawableRes = R.drawable.aether_hero,
        fallbackTheme = CharacterTheme.AETHER
    )

    object Spark : Character(
        id = "spark",
        name = "Spark",
        emoji = "⚡",
        title = "Energetic Lightning Wisp",
        description = "Playful plasma firefly with lightning antennae and high-voltage execution speed.",
        hasSprites = false,
        gradientColors = listOf(Color(0xFFFBBF24), Color(0xFF10B981)),
        heroDrawableRes = R.drawable.lumy_hero,
        fallbackTheme = CharacterTheme.LUMY
    )

    object Volo : Character(
        id = "volo",
        name = "Volo",
        emoji = "🕊️",
        title = "Swift Sky Messenger",
        description = "Aerodynamic winged tech mascot with graceful flight and supersonic task routing.",
        hasSprites = false,
        gradientColors = listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
        heroDrawableRes = R.drawable.aether_hero,
        fallbackTheme = CharacterTheme.AETHER
    )

    object Pico : Character(
        id = "pico",
        name = "Pico",
        emoji = "🤖",
        title = "Chibi Robotic Pet",
        description = "Minimal, adorable cyber pet with an expressive glowing visor and helper gears.",
        hasSprites = false,
        gradientColors = listOf(Color(0xFF6366F1), Color(0xFFA855F7)),
        heroDrawableRes = R.drawable.aether_hero,
        fallbackTheme = CharacterTheme.AETHER
    )

    object Guardian : Character(
        id = "guardian",
        name = "Guardian",
        emoji = "🛡️",
        title = "Cyber Shield Sentinel",
        description = "Protective AI defender with blue forcefields, battery watchdog, and safety shields.",
        hasSprites = false,
        gradientColors = listOf(Color(0xFF2563EB), Color(0xFF0EA5E9)),
        heroDrawableRes = R.drawable.aether_hero,
        fallbackTheme = CharacterTheme.AETHER
    )

    object Echo : Character(
        id = "echo",
        name = "Echo",
        emoji = "🔊",
        title = "Resonant Soundwave Pulsar",
        description = "Audio-reactive companion with harmonic frequency rings and voice mastery.",
        hasSprites = false,
        gradientColors = listOf(Color(0xFFD946EF), Color(0xFF8B5CF6)),
        heroDrawableRes = R.drawable.aether_hero,
        fallbackTheme = CharacterTheme.AETHER
    )

    companion object {
        val all: List<Character> = listOf(Aether, Lumy, Nexus, Spark, Volo, Pico, Guardian, Echo)
        val withFullAssets: List<Character> = all.filter { it.hasSprites }

        fun find(id: String): Character {
            return all.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Aether
        }
    }
}
