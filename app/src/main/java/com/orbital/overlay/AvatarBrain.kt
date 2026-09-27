package com.orbital.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import com.orbital.ui.MascotEventBus
import com.orbital.ui.MascotState
import kotlinx.coroutines.*
import java.util.Random

/**
 * AvatarBrain manages autonomous life-like personality behaviors ("Tiny Brain"),
 * natural screen wandering, playful dashes, thinking/blinking loops,
 * and emotional responsiveness for the floating overlay companion.
 */
class AvatarBrain(
    private val windowManager: WindowManager,
    private val overlayViewProvider: () -> View?,
    private val avatarContainerProvider: () -> View?,
    private val characterImageProvider: () -> ImageView?,
    private val windowParams: WindowManager.LayoutParams,
    private val getScreenDimensions: () -> Pair<Int, Int>,
    private val onStateChanged: (MascotState) -> Unit
) {

    enum class Personality {
        PLAYFUL,
        CURIOUS,
        CALM
    }

    private val random = Random()
    private var brainScope: CoroutineScope? = null
    private var decisionLoopJob: Job? = null
    private var blinkLoopJob: Job? = null
    private var thinkingAnimationJob: Job? = null

    // State flags
    private var isUserInteracting = false
    private var isChatPanelOpen = false
    private var isThinking = false
    private var isRoaming = false
    private var personality = Personality.PLAYFUL

    // Movement animators
    private var moveAnimator: ValueAnimator? = null
    private var currentWalkBobAnimator: ObjectAnimator? = null
    private var thinkingWobbleAnimator: ObjectAnimator? = null

    /**
     * Start the autonomous brain ticker & life loops.
     */
    fun start(scope: CoroutineScope) {
        brainScope = scope
        startBlinkLoop()
        startDecisionLoop()
    }

    /**
     * Stop and cleanup all animators and jobs.
     */
    fun stop() {
        stopMovement()
        decisionLoopJob?.cancel()
        blinkLoopJob?.cancel()
        thinkingAnimationJob?.cancel()
        thinkingWobbleAnimator?.cancel()
        moveAnimator?.cancel()
        currentWalkBobAnimator?.cancel()
    }

    fun setPersonality(newPersonality: Personality) {
        personality = newPersonality
    }

    /**
     * Notify brain when user starts or stops touching/dragging the bubble.
     */
    fun onUserInteracting(interacting: Boolean) {
        isUserInteracting = interacting
        if (interacting) {
            stopMovement()
        }
    }

    /**
     * Notify brain when chat dialog opens or closes.
     */
    fun onChatPanelVisibilityChanged(isOpen: Boolean) {
        isChatPanelOpen = isOpen
        if (isOpen) {
            stopMovement()
        }
    }

    /**
     * Trigger Thinking & Blinking mode when AI query is sent or processing.
     */
    fun onThinking(active: Boolean) {
        isThinking = active
        val characterImage = characterImageProvider() ?: return

        if (active) {
            stopMovement()
            onStateChanged(MascotState.THINKING)
            
            // Start thinking head tilt & concentrated micro-wobble
            thinkingWobbleAnimator?.cancel()
            thinkingWobbleAnimator = ObjectAnimator.ofFloat(characterImage, "rotation", -4f, 4f).apply {
                duration = 800
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }

            // Rapid concentrated blinks while thinking
            thinkingAnimationJob?.cancel()
            thinkingAnimationJob = brainScope?.launch(Dispatchers.Main) {
                while (isThinking && isActive) {
                    delay(1200L + random.nextInt(1000).toLong())
                    if (!isThinking) break
                    performBlinkAnimation(fast = true)
                }
            }
        } else {
            thinkingAnimationJob?.cancel()
            thinkingWobbleAnimator?.cancel()
            characterImage.animate().rotation(0f).setDuration(200).start()
        }
    }

    /**
     * Autonomous decision ticker: runs every 4–10 seconds to pick a playful or wandering action.
     */
    private fun startDecisionLoop() {
        decisionLoopJob?.cancel()
        decisionLoopJob = brainScope?.launch(Dispatchers.Main) {
            while (isActive) {
                // Determine tick delay based on personality
                val baseDelay = when (personality) {
                    Personality.PLAYFUL -> 4500L + random.nextInt(4000)
                    Personality.CURIOUS -> 6000L + random.nextInt(5000)
                    Personality.CALM -> 8000L + random.nextInt(7000)
                }
                delay(baseDelay)

                if (canPerformAutonomousAction()) {
                    decideNextAction()
                }
            }
        }
    }

    private fun canPerformAutonomousAction(): Boolean {
        return !isUserInteracting && !isChatPanelOpen && !isThinking && !isRoaming
    }

    /**
     * Roll probabilities for autonomous behaviors (Walking, Dashing, Playing, Looking Around).
     */
    private fun decideNextAction() {
        val roll = random.nextInt(100)
        when {
            roll < 45 -> performWander() // 45% chance: walk to a new spot
            roll < 65 -> performPlayfulHop() // 20% chance: playful hop/spin
            roll < 80 -> performLookAround() // 15% chance: look around curiously
            roll < 90 -> performDash() // 10% chance: fast playful dash
            else -> {
                // 10% chance: stay cozy & idle
                onStateChanged(MascotState.IDLE)
            }
        }
    }

    /**
     * Smoothly wander (walk) across the screen with natural walking bob and direction flip.
     */
    private fun performWander() {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val avatar = avatarContainerProvider() ?: return
        val characterImage = characterImageProvider() ?: return

        val avatarSize = avatar.width.coerceAtLeast(140)
        val minX = 20
        val maxX = (screenWidth - avatarSize - 20).coerceAtLeast(minX)
        val minY = 100 // Below notification bar
        val maxY = (screenHeight - avatarSize - 180).coerceAtLeast(minY)

        val targetX = minX + random.nextInt((maxX - minX).coerceAtLeast(1))
        val targetY = minY + random.nextInt((maxY - minY).coerceAtLeast(1))

        val startX = windowParams.x
        val startY = windowParams.y
        val deltaX = targetX - startX

        isRoaming = true
        onStateChanged(MascotState.WALKING)

        // Flip sprite direction towards target (smooth flip)
        val targetScaleX = if (deltaX >= 0) 1.0f else -1.0f
        characterImage.animate()
            .scaleX(targetScaleX)
            .setDuration(150)
            .start()

        // Walking step bobbing
        currentWalkBobAnimator?.cancel()
        currentWalkBobAnimator = ObjectAnimator.ofFloat(characterImage, "translationY", 0f, -8f, 0f).apply {
            duration = 260
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Window coordinate interpolation
        val distance = Math.hypot((targetX - startX).toDouble(), (targetY - startY).toDouble()).toFloat()
        val durationMs = (distance * 3.5f).coerceIn(1200f, 3000f).toLong()

        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                if (!canPerformAutonomousAction()) {
                    cancel()
                    return@addUpdateListener
                }
                val frac = anim.animatedValue as Float
                windowParams.x = (startX + (targetX - startX) * frac).toInt()
                windowParams.y = (startY + (targetY - startY) * frac).toInt()
                updateLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isRoaming = false
                    currentWalkBobAnimator?.cancel()
                    characterImage.translationY = 0f
                    // Smoothly restore default facing
                    characterImage.animate().scaleX(1.0f).setDuration(200).start()
                    if (canPerformAutonomousAction()) {
                        onStateChanged(MascotState.IDLE)
                    }
                }
            })
            start()
        }
    }

    /**
     * Fast playful dash across the screen.
     */
    private fun performDash() {
        val (screenWidth, _) = getScreenDimensions()
        val avatar = avatarContainerProvider() ?: return
        val characterImage = characterImageProvider() ?: return

        val avatarSize = avatar.width.coerceAtLeast(140)
        val minX = 20
        val maxX = (screenWidth - avatarSize - 20).coerceAtLeast(minX)

        val targetX = if (windowParams.x < screenWidth / 2) maxX else minX
        val startX = windowParams.x
        val deltaX = targetX - startX

        isRoaming = true
        onStateChanged(MascotState.EXCITED)

        // Squash on dash start
        characterImage.animate()
            .scaleX(if (deltaX >= 0) 1.25f else -1.25f)
            .scaleY(0.78f)
            .setDuration(120)
            .withEndAction {
                characterImage.animate()
                    .scaleX(if (deltaX >= 0) 1.0f else -1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .start()
            }
            .start()

        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                if (!canPerformAutonomousAction()) {
                    cancel()
                    return@addUpdateListener
                }
                val frac = anim.animatedValue as Float
                windowParams.x = (startX + (targetX - startX) * frac).toInt()
                updateLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isRoaming = false
                    characterImage.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    if (canPerformAutonomousAction()) {
                        onStateChanged(MascotState.HAPPY)
                    }
                }
            })
            start()
        }
    }

    /**
     * Playful hop / jump in place with squash-and-stretch.
     */
    private fun performPlayfulHop() {
        val characterImage = characterImageProvider() ?: return
        onStateChanged(MascotState.PLAYING)

        val hopY = ObjectAnimator.ofFloat(characterImage, "translationY", 0f, -20f, 0f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.6f)
        }
        val squashX = ObjectAnimator.ofFloat(characterImage, "scaleX", 1f, 1.18f, 0.92f, 1f).apply {
            duration = 450
        }
        val squashY = ObjectAnimator.ofFloat(characterImage, "scaleY", 1f, 0.85f, 1.15f, 1f).apply {
            duration = 450
        }

        AnimatorSet().apply {
            playTogether(hopY, squashX, squashY)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (canPerformAutonomousAction()) {
                        onStateChanged(MascotState.IDLE)
                    }
                }
            })
            start()
        }
    }

    /**
     * Look around curiously (head tilting & slight panning).
     */
    private fun performLookAround() {
        val characterImage = characterImageProvider() ?: return
        onStateChanged(MascotState.LOOKING)

        val lookTilt = ObjectAnimator.ofFloat(characterImage, "rotation", 0f, -12f, 12f, 0f).apply {
            duration = 1100
            interpolator = AccelerateDecelerateInterpolator()
        }

        lookTilt.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (canPerformAutonomousAction()) {
                    onStateChanged(MascotState.IDLE)
                }
            }
        })
        lookTilt.start()
    }

    /**
     * Natural periodic blinking loop (every 2.5–5.5 seconds).
     */
    private fun startBlinkLoop() {
        blinkLoopJob?.cancel()
        blinkLoopJob = brainScope?.launch(Dispatchers.Main) {
            while (isActive) {
                delay(2500L + random.nextInt(3000))
                if (!isThinking && !isUserInteracting) {
                    performBlinkAnimation(fast = false)
                }
            }
        }
    }

    /**
     * Micro scale squash simulating an eye blink.
     */
    private fun performBlinkAnimation(fast: Boolean) {
        val characterImage = characterImageProvider() ?: return
        val blinkDuration = if (fast) 70L else 110L

        characterImage.animate()
            .scaleY(0.18f)
            .setDuration(blinkDuration)
            .withEndAction {
                characterImage.animate()
                    .scaleY(1.0f)
                    .setDuration(blinkDuration)
                    .start()
            }
            .start()
    }

    private fun stopMovement() {
        isRoaming = false
        moveAnimator?.cancel()
        currentWalkBobAnimator?.cancel()
        characterImageProvider()?.let {
            it.translationY = 0f
            it.scaleX = 1f
            it.scaleY = 1f
        }
    }

    private fun updateLayout() {
        val overlay = overlayViewProvider() ?: return
        if (overlay.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(overlay, windowParams)
            } catch (_: Exception) {}
        }
    }
}
