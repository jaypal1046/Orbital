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
    SAD("Sad / Error", "🥺")
}

object MascotSpriteHelper {

    @DrawableRes
    fun getSprite(characterId: String, state: MascotState): Int {
        val isLumy = characterId.equals("lumy", ignoreCase = true) || characterId.equals("spark", ignoreCase = true)
        
        return if (isLumy) {
            when (state) {
                MascotState.IDLE -> R.drawable.lumy_idle
                MascotState.HOVER -> R.drawable.lumy_hover
                MascotState.JUMP -> R.drawable.lumy_jump
                MascotState.THINKING -> R.drawable.lumy_thinking
                MascotState.WORKING -> R.drawable.lumy_working
                MascotState.CELEBRATING -> R.drawable.lumy_celebrating
                MascotState.HAPPY -> R.drawable.lumy_happy
                MascotState.EXCITED -> R.drawable.lumy_excited
                MascotState.CURIOUS -> R.drawable.lumy_curious
                MascotState.WINK -> R.drawable.lumy_wink
                MascotState.LOVE -> R.drawable.lumy_love
                MascotState.SLEEPING -> R.drawable.lumy_sleep
                MascotState.TIRED -> R.drawable.lumy_tired
                MascotState.SAD -> R.drawable.lumy_sad
            }
        } else {
            // Default to Aether
            when (state) {
                MascotState.IDLE -> R.drawable.aether_idle
                MascotState.HOVER -> R.drawable.aether_hover
                MascotState.JUMP -> R.drawable.aether_jump
                MascotState.THINKING -> R.drawable.aether_thinking
                MascotState.WORKING -> R.drawable.aether_working
                MascotState.CELEBRATING -> R.drawable.aether_celebrating
                MascotState.HAPPY -> R.drawable.aether_happy
                MascotState.EXCITED -> R.drawable.aether_excited
                MascotState.CURIOUS -> R.drawable.aether_curious
                MascotState.WINK -> R.drawable.aether_wink
                MascotState.LOVE -> R.drawable.aether_love
                MascotState.SLEEPING -> R.drawable.aether_sleep
                MascotState.TIRED -> R.drawable.aether_tired
                MascotState.SAD -> R.drawable.aether_sad
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
