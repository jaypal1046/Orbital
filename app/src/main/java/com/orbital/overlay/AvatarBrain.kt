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
import com.orbital.ui.MascotState
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * AvatarBrain manages autonomous life-like personality behaviors ("Living Companion"),
 * natural screen wandering (top-to-bottom, bottom-to-top, edge patrolling, diagonal swoops),
 * playful dashes, thinking/blinking loops, and emotional responsiveness.
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

    // Movement tracking
    private var isMovingDown = true // Toggles top <-> bottom vertical roaming

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
            thinkingWobbleAnimator = ObjectAnimator.ofFloat(characterImage, "rotation", -5f, 5f).apply {
                duration = 750
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }

            // Rapid concentrated blinks while thinking
            thinkingAnimationJob?.cancel()
            thinkingAnimationJob = brainScope?.launch(Dispatchers.Main) {
                while (isThinking && isActive) {
                    delay(1000L + random.nextInt(900).toLong())
                    if (!isThinking) break
                    performBlinkAnimation(fast = true)
                }
            }
        } else {
            thinkingAnimationJob?.cancel()
            thinkingWobbleAnimator?.cancel()
            characterImage.animate().rotation(0f).setDuration(200).start()
            if (canPerformAutonomousAction()) {
                onStateChanged(MascotState.IDLE)
            }
        }
    }

    /**
     * Autonomous decision ticker: runs every 3.5–7 seconds to pick a lively movement or interaction.
     */
    private fun startDecisionLoop() {
        decisionLoopJob?.cancel()
        decisionLoopJob = brainScope?.launch(Dispatchers.Main) {
            // Initial warm-up delay before first autonomous action
            delay(2500)
            while (isActive) {
                val baseDelay = when (personality) {
                    Personality.PLAYFUL -> 3500L + random.nextInt(3000)
                    Personality.CURIOUS -> 4500L + random.nextInt(3500)
                    Personality.CALM -> 6000L + random.nextInt(4000)
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
     * Decision engine: picks lifelike movements across the full screen.
     */
    private fun decideNextAction() {
        val roll = random.nextInt(100)
        when {
            // 40% chance: Smooth Vertical Roaming (Top <-> Bottom along screen)
            roll < 40 -> performVerticalRoam()

            // 25% chance: Playful Edge Patrol (Glide smoothly along left/right edge)
            roll < 65 -> performEdgePatrol()

            // 15% chance: Diagonal Floating Swoop across screen
            roll < 80 -> performDiagonalSwoop()

            // 12% chance: Playful Hop & Spin in place
            roll < 92 -> performPlayfulHop()

            // 8% chance: Curious Look Around / Peek
            else -> performLookAround()
        }
    }

    /**
     * Smoothly roams vertically from top of screen to bottom, and back up, with graceful sine-wave bobbing.
     */
    private fun performVerticalRoam() {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val avatar = avatarContainerProvider() ?: return
        val characterImage = characterImageProvider() ?: return

        val avatarSize = avatar.width.coerceAtLeast(140)
        val minY = 120 // Safely below status bar
        val maxY = (screenHeight - avatarSize - 220).coerceAtLeast(minY + 200)

        // Decide destination based on current vertical position and cycle
        val currentY = windowParams.y
        val targetY = if (currentY < (minY + maxY) / 2) {
            // Currently near top -> glide down towards bottom
            isMovingDown = true
            maxY - random.nextInt(120)
        } else {
            // Currently near bottom -> glide up towards top
            isMovingDown = false
            minY + random.nextInt(120)
        }

        // Keep horizontal position on the preferred edge with slight float sway
        val isLeftEdge = windowParams.x < screenWidth / 2
        val edgeX = if (isLeftEdge) 24 else (screenWidth - avatarSize - 24).coerceAtLeast(24)

        val startX = windowParams.x
        val startY = windowParams.y
        val deltaY = targetY - startY

        isRoaming = true
        onStateChanged(MascotState.WALKING)

        // Tilt/face towards movement direction
        val targetScaleX = if (isLeftEdge) 1.0f else -1.0f
        characterImage.animate()
            .scaleX(targetScaleX)
            .rotation(if (deltaY > 0) 6f else -6f)
            .setDuration(220)
            .start()

        // Walking / floating micro-bob
        currentWalkBobAnimator?.cancel()
        currentWalkBobAnimator = ObjectAnimator.ofFloat(characterImage, "translationY", 0f, -10f, 0f).apply {
            duration = 320
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val distance = Math.abs(targetY - startY).toFloat()
        val durationMs = (distance * 4.2f).coerceIn(1800f, 4000f).toLong()

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
                // Smooth glide on Y, with subtle sine-wave sway on X
                val sway = (sin(frac * PI * 2.0) * 18.0).toInt()
                windowParams.x = (startX + (edgeX - startX) * frac).toInt() + sway
                windowParams.y = (startY + (targetY - startY) * frac).toInt()
                updateLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishRoaming(characterImage)
                }
            })
            start()
        }
    }

    /**
     * Patrols smoothly along the current edge up and down.
     */
    private fun performEdgePatrol() {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val avatar = avatarContainerProvider() ?: return
        val characterImage = characterImageProvider() ?: return

        val avatarSize = avatar.width.coerceAtLeast(140)
        val isLeft = windowParams.x < screenWidth / 2
        val targetX = if (isLeft) 20 else (screenWidth - avatarSize - 20).coerceAtLeast(20)

        val minY = 140
        val maxY = (screenHeight - avatarSize - 240).coerceAtLeast(minY + 150)
        val targetY = minY + random.nextInt((maxY - minY).coerceAtLeast(1))

        val startX = windowParams.x
        val startY = windowParams.y

        isRoaming = true
        onStateChanged(MascotState.WALKING)

        characterImage.animate()
            .scaleX(if (isLeft) 1.0f else -1.0f)
            .setDuration(180)
            .start()

        val distance = Math.hypot((targetX - startX).toDouble(), (targetY - startY).toDouble()).toFloat()
        val durationMs = (distance * 3.8f).coerceIn(1400f, 3200f).toLong()

        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
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
                    finishRoaming(characterImage)
                }
            })
            start()
        }
    }

    /**
     * Playful diagonal swoop across the display.
     */
    private fun performDiagonalSwoop() {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val avatar = avatarContainerProvider() ?: return
        val characterImage = characterImageProvider() ?: return

        val avatarSize = avatar.width.coerceAtLeast(140)
        val minX = 20
        val maxX = (screenWidth - avatarSize - 20).coerceAtLeast(minX)
        val minY = 120
        val maxY = (screenHeight - avatarSize - 220).coerceAtLeast(minY)

        // Switch edges across screen
        val targetX = if (windowParams.x < screenWidth / 2) maxX else minX
        val targetY = if (windowParams.y < (minY + maxY) / 2) maxY - random.nextInt(160) else minY + random.nextInt(160)

        val startX = windowParams.x
        val startY = windowParams.y
        val deltaX = targetX - startX

        isRoaming = true
        onStateChanged(MascotState.EXCITED)

        characterImage.animate()
            .scaleX(if (deltaX >= 0) 1.15f else -1.15f)
            .scaleY(0.9f)
            .rotation(if (deltaX >= 0) 12f else -12f)
            .setDuration(180)
            .start()

        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                if (!canPerformAutonomousAction()) {
                    cancel()
                    return@addUpdateListener
                }
                val frac = anim.animatedValue as Float
                // Curved parabolic arc trajectory
                val arcOffset = (sin(frac * PI) * 50.0).toInt()
                windowParams.x = (startX + (targetX - startX) * frac).toInt()
                windowParams.y = (startY + (targetY - startY) * frac).toInt() - arcOffset
                updateLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishRoaming(characterImage)
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

        val hopY = ObjectAnimator.ofFloat(characterImage, "translationY", 0f, -24f, 0f).apply {
            duration = 480
            interpolator = OvershootInterpolator(1.8f)
        }
        val squashX = ObjectAnimator.ofFloat(characterImage, "scaleX", 1f, 1.22f, 0.9f, 1f).apply {
            duration = 480
        }
        val squashY = ObjectAnimator.ofFloat(characterImage, "scaleY", 1f, 0.82f, 1.18f, 1f).apply {
            duration = 480
        }

        AnimatorSet().apply {
            playTogether(hopY, squashX, squashY)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (canPerformAutonomousAction()) {
                        onStateChanged(MascotState.HAPPY)
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

        val lookTilt = ObjectAnimator.ofFloat(characterImage, "rotation", 0f, -14f, 14f, 0f).apply {
            duration = 1200
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
     * Natural periodic eye blinking loop.
     */
    private fun startBlinkLoop() {
        blinkLoopJob?.cancel()
        blinkLoopJob = brainScope?.launch(Dispatchers.Main) {
            while (isActive) {
                delay(2200L + random.nextInt(2800))
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
        val blinkDuration = if (fast) 65L else 100L

        characterImage.animate()
            .scaleY(0.15f)
            .setDuration(blinkDuration)
            .withEndAction {
                characterImage.animate()
                    .scaleY(1.0f)
                    .setDuration(blinkDuration)
                    .start()
            }
            .start()
    }

    private fun finishRoaming(characterImage: ImageView) {
        isRoaming = false
        currentWalkBobAnimator?.cancel()
        characterImage.translationY = 0f
        characterImage.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .rotation(0f)
            .setDuration(240)
            .start()
        if (canPerformAutonomousAction()) {
            onStateChanged(MascotState.IDLE)
        }
    }

    private fun stopMovement() {
        isRoaming = false
        moveAnimator?.cancel()
        currentWalkBobAnimator?.cancel()
        characterImageProvider()?.let {
            it.translationY = 0f
            it.scaleX = 1f
            it.scaleY = 1f
            it.rotation = 0f
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
