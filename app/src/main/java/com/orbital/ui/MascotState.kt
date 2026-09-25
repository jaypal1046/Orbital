package com.orbital.ui

import androidx.annotation.DrawableRes
import com.orbital.R

enum class MascotState(val displayName: String, val emoji: String) {
    IDLE("Idle", "🌿"),
    HOVER("Hover", "✨"),
    JUMP("Jump / Greet", "🚀"),
    THINKING("Thinking", "🧠"),
    WORKING("Working & Executing", "⚙️"),
    CELEBRATING("Celebrating / Done", "🎉"),
    HAPPY("Happy", "😊"),
    EXCITED("Excited", "⚡"),
    CURIOUS("Curious", "🧐"),
    WINK("Wink", "😉"),
    LOVE("Love", "💖"),
    SLEEPING("Sleeping", "💤"),
    TIRED("Tired", "😴"),
    SAD("Sad / Error", "🥺"),
    PLAYING("Playing & Timepass", "🎮"),
    WALKING("Walking around", "🚶"),
    LOOKING("Looking around", "👀"),
    EATING("Eating snack / Tea", "🍵")
}

object MascotSpriteHelper {

    @DrawableRes
    fun getSprite(characterId: String, state: MascotState): Int {
        val isLumy = characterId.equals("lumy", ignoreCase = true) || characterId.equals("spark", ignoreCase = true)
        
        return if (isLumy) {
            when (state) {
                MascotState.IDLE -> R.drawable.lumy_idle
                MascotState.HOVER -> R.drawable.lumy_idle
                MascotState.JUMP -> R.drawable.lumy_jump
                MascotState.THINKING -> R.drawable.lumy_thinking
                MascotState.WORKING -> R.drawable.lumy_working
                MascotState.CELEBRATING -> R.drawable.lumy_celebrating
                MascotState.HAPPY -> R.drawable.lumy_celebrating
                MascotState.EXCITED -> R.drawable.lumy_jump
                MascotState.CURIOUS -> R.drawable.lumy_thinking
                MascotState.WINK -> R.drawable.lumy_celebrating
                MascotState.LOVE -> R.drawable.lumy_celebrating
                MascotState.SLEEPING -> R.drawable.lumy_sleep
                MascotState.TIRED -> R.drawable.lumy_sleep
                MascotState.SAD -> R.drawable.lumy_sad
                MascotState.PLAYING -> R.drawable.lumy_jump
                MascotState.WALKING -> R.drawable.lumy_idle
                MascotState.LOOKING -> R.drawable.lumy_thinking
                MascotState.EATING -> R.drawable.lumy_celebrating
            }
        } else {
            // Default to Aether
            when (state) {
                MascotState.IDLE -> R.drawable.aether_idle
                MascotState.HOVER -> R.drawable.aether_idle
                MascotState.JUMP -> R.drawable.aether_jump
                MascotState.THINKING -> R.drawable.aether_thinking
                MascotState.WORKING -> R.drawable.aether_working
                MascotState.CELEBRATING -> R.drawable.aether_celebrating
                MascotState.HAPPY -> R.drawable.aether_celebrating
                MascotState.EXCITED -> R.drawable.aether_jump
                MascotState.CURIOUS -> R.drawable.aether_thinking
                MascotState.WINK -> R.drawable.aether_celebrating
                MascotState.LOVE -> R.drawable.aether_celebrating
                MascotState.SLEEPING -> R.drawable.aether_sleep
                MascotState.TIRED -> R.drawable.aether_sleep
                MascotState.SAD -> R.drawable.aether_sad
                MascotState.PLAYING -> R.drawable.aether_jump
                MascotState.WALKING -> R.drawable.aether_idle
                MascotState.LOOKING -> R.drawable.aether_thinking
                MascotState.EATING -> R.drawable.aether_celebrating
            }
        }
    }

    @DrawableRes
    fun getHeroSprite(characterId: String): Int {
        return if (characterId.equals("lumy", ignoreCase = true) || characterId.equals("spark", ignoreCase = true)) {
            R.drawable.lumy_hero
        } else {
            R.drawable.aether_hero
        }
    }
}
