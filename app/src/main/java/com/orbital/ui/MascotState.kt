package com.orbital.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.orbital.R

enum class MascotState(
    val displayName: String,
    val emoji: String,
    val spriteAether: Int,
    val spriteLumy: Int
) {
    IDLE("Idle", "🌿", R.drawable.aether_idle, R.drawable.lumy_idle),
    HOVER("Hover", "✨", R.drawable.aether_idle, R.drawable.lumy_idle),
    JUMP("Jump / Greet", "🚀", R.drawable.aether_jump, R.drawable.lumy_jump),
    THINKING("Thinking", "🧠", R.drawable.aether_thinking, R.drawable.lumy_thinking),
    WORKING("Working & Executing", "⚙️", R.drawable.aether_working, R.drawable.lumy_working),
    CELEBRATING("Celebrating / Done", "🎉", R.drawable.aether_celebrating, R.drawable.lumy_celebrating),
    HAPPY("Happy", "😊", R.drawable.aether_celebrating, R.drawable.lumy_celebrating),
    EXCITED("Excited", "⚡", R.drawable.aether_jump, R.drawable.lumy_jump),
    CURIOUS("Curious", "🧐", R.drawable.aether_thinking, R.drawable.lumy_thinking),
    WINK("Wink", "😉", R.drawable.aether_celebrating, R.drawable.lumy_celebrating),
    LOVE("Love", "💖", R.drawable.aether_celebrating, R.drawable.lumy_celebrating),
    SLEEPING("Sleeping", "💤", R.drawable.aether_sleep, R.drawable.lumy_sleep),
    TIRED("Tired", "😴", R.drawable.aether_sleep, R.drawable.lumy_sleep),
    SAD("Sad / Error", "🥺", R.drawable.aether_sad, R.drawable.lumy_sad),
    PLAYING("Playing & Timepass", "🎮", R.drawable.aether_jump, R.drawable.lumy_jump),
    WALKING("Walking around", "🚶", R.drawable.aether_idle, R.drawable.lumy_idle),
    LOOKING("Looking around", "👀", R.drawable.aether_thinking, R.drawable.lumy_thinking),
    EATING("Eating snack / Tea", "🍵", R.drawable.aether_celebrating, R.drawable.lumy_celebrating)
}

object MascotSpriteHelper {

    fun getSprite(characterId: String, state: MascotState): Int {
        return DefaultCharacterRenderer.getSprite(characterId, state)
    }

    @DrawableRes
    fun getHeroSprite(characterId: String): Int {
        return DefaultCharacterRenderer.getHeroSprite(characterId)
    }

    fun getAuraGlowColors(characterId: String): List<Color> {
        return DefaultCharacterRenderer.getAuraGlowColors(characterId)
    }
}