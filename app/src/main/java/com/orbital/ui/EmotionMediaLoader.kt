package com.orbital.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import com.orbital.R

/**
 * EmotionMediaLoader manages dynamic loading and playback of 3–5 second
 * Animated WebP (or GIF) video-like loops for avatar emotions, with seamless
 * fallback to static drawables.
 */
object EmotionMediaLoader {

    private const val TAG = "EmotionMediaLoader"

    /**
     * Attempts to find an animated WebP / resource for a given character and state.
     * Looks in res/raw or res/drawable for:
     * 1. "<char>_<state>_anim"
     * 2. "<char>_<state>"
     * Returns resource ID if found, or 0 if not present.
     */
    fun getAnimatedWebpResId(context: Context, characterId: String, state: MascotState): Int {
        val cleanChar = characterId.lowercase().trim()
        val directStateName = state.name.lowercase()

        // List of candidate state names to try (direct match first, then thematic alias)
        val candidates = mutableListOf(directStateName)
        when (state) {
            MascotState.HOVER -> candidates.add("idle")
            MascotState.EXCITED, MascotState.PLAYING -> candidates.add("jump")
            MascotState.CURIOUS, MascotState.LOOKING -> candidates.add("thinking")
            MascotState.HAPPY, MascotState.WINK, MascotState.LOVE, MascotState.EATING -> candidates.add("celebrating")
            MascotState.TIRED -> candidates.add("sleeping")
            MascotState.WORKING -> candidates.add("working")
            else -> {}
        }

        for (cand in candidates) {
            // Check raw anim
            var resId = context.resources.getIdentifier("${cleanChar}_${cand}_anim", "raw", context.packageName)
            if (resId != 0) return resId

            // Check raw direct
            resId = context.resources.getIdentifier("${cleanChar}_${cand}", "raw", context.packageName)
            if (resId != 0) return resId

            // Check drawable anim
            resId = context.resources.getIdentifier("${cleanChar}_${cand}_anim", "drawable", context.packageName)
            if (resId != 0) return resId
        }

        return 0
    }

    /**
     * Loads either an animated WebP (looping 3-5s clip) or fallback static sprite
     * into the provided ImageView with smooth crossfade and memory safety.
     */
    fun loadEmotion(
        context: Context,
        characterId: String,
        state: MascotState,
        imageView: ImageView,
        animateTransition: Boolean = true
    ) {
        val animResId = getAnimatedWebpResId(context, characterId, state)

        if (animResId != 0) {
            // Found Animated WebP / GIF loop!
            val loaded = playAnimatedResource(context, animResId, imageView, animateTransition)
            if (loaded) return
        }

        // Fallback: Load static high-res image
        val staticResId = MascotSpriteHelper.getSprite(characterId, state)
        loadStaticSprite(staticResId, imageView, animateTransition)
    }

    /**
     * Plays animated WebP from resId with infinite loop.
     */
    fun playAnimatedResource(
        context: Context,
        @RawRes @DrawableRes resId: Int,
        imageView: ImageView,
        animateTransition: Boolean
    ): Boolean {
        try {
            // Stop previous animatable if any
            stopAnimation(imageView)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.resources, resId)
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }

                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable.start()
                } else if (drawable is Animatable) {
                    drawable.start()
                }

                applyDrawableToImageView(imageView, drawable, animateTransition)
                return true
            } else {
                val drawable = ContextCompat.getDrawable(context, resId)
                if (drawable != null) {
                    if (drawable is Animatable) {
                        drawable.start()
                    }
                    applyDrawableToImageView(imageView, drawable, animateTransition)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode animated WebP resId: $resId, falling back to static", e)
        }
        return false
    }

    /**
     * Loads a static sprite with a gentle squash/fade transition.
     */
    private fun loadStaticSprite(
        @DrawableRes staticResId: Int,
        imageView: ImageView,
        animateTransition: Boolean
    ) {
        stopAnimation(imageView)
        if (animateTransition) {
            imageView.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .alpha(0.75f)
                .setDuration(100)
                .withEndAction {
                    imageView.setImageResource(staticResId)
                    imageView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(140)
                        .start()
                }
                .start()
        } else {
            imageView.setImageResource(staticResId)
            imageView.alpha = 1.0f
            imageView.scaleX = 1.0f
            imageView.scaleY = 1.0f
        }
    }

    private fun applyDrawableToImageView(imageView: ImageView, drawable: Drawable, animateTransition: Boolean) {
        if (animateTransition) {
            imageView.animate()
                .alpha(0.75f)
                .setDuration(100)
                .withEndAction {
                    imageView.setImageDrawable(drawable)
                    imageView.animate()
                        .alpha(1.0f)
                        .setDuration(140)
                        .start()
                }
                .start()
        } else {
            imageView.setImageDrawable(drawable)
            imageView.alpha = 1.0f
        }
    }

    /**
     * Pauses or stops any active animation on the ImageView to conserve battery.
     */
    fun stopAnimation(imageView: ImageView) {
        val currentDrawable = imageView.drawable
        if (currentDrawable is Animatable && currentDrawable.isRunning) {
            currentDrawable.stop()
        }
    }
}
