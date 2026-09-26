package com.orbital.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/**
 * Shared interface for rendering mascot sprites, heroes, and glowing aura rings
 * across both Jetpack Compose in-app views and Android Service overlay bubbles.
 */
interface CharacterRenderer {
    @DrawableRes
    fun getSprite(characterId: String, state: MascotState): Int

    @DrawableRes
    fun getHeroSprite(characterId: String): Int

    fun getAuraGlowColors(characterId: String): List<Color>
}

/**
 * Default state-machine renderer mapping character states to actual drawable resources.
 */
object DefaultCharacterRenderer : CharacterRenderer {

    override fun getSprite(characterId: String, state: MascotState): Int {
        val character = Character.find(characterId)
        return when (character.fallbackTheme) {
            Character.CharacterTheme.LUMY -> state.spriteLumy
            Character.CharacterTheme.AETHER -> state.spriteAether
        }
    }

    override fun getHeroSprite(characterId: String): Int {
        val character = Character.find(characterId)
        return character.heroDrawableRes
    }

    override fun getAuraGlowColors(characterId: String): List<Color> {
        val character = Character.find(characterId)
        return when (character.fallbackTheme) {
            Character.CharacterTheme.LUMY -> listOf(
                Color(0xFFFFB74D).copy(alpha = 0.35f),
                Color(0xFFF43F5E).copy(alpha = 0.15f),
                Color.Transparent
            )
            Character.CharacterTheme.AETHER -> listOf(
                Color(0xFF8B5CF6).copy(alpha = 0.35f),
                Color(0xFF3B82F6).copy(alpha = 0.15f),
                Color.Transparent
            )
        }
    }
}
