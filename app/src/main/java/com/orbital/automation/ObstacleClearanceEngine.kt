package com.orbital.automation

import android.graphics.Rect
import android.util.Log

/**
 * Identifies and clears unexpected blocking UI obstacles (permissions dialogs,
 * promo sheets, rating prompts, dismissible overlays) during automation.
 *
 * Adheres strictly to Zero Hardcoding Rule: uses generic dynamic accessibility patterns.
 */
data class ObstacleInfo(
    val type: ObstacleType,
    val title: String?,
    val dismissActionElement: UIElement?,
    val dismissActionLabel: String
)

enum class ObstacleType {
    PERMISSION_DIALOG,
    PROMO_MODAL,
    SYSTEM_ALERT,
    DISMISSIBLE_BANNER
}

class ObstacleClearanceEngine {

    companion object {
        private const val TAG = "ObstacleClearanceEngine"

        // Generic patterns for dismissive / positive dialog actions
        private val DISMISS_PATTERNS = listOf(
            "dismiss", "close", "✕", "x", "not now", "skip", "cancel",
            "no thanks", "later", "maybe later", "remind me later",
            "got it", "continue without", "decline", "ignore"
        )

        private val PERMISSION_PATTERNS = listOf(
            "while using the app", "only this time", "allow", "allow all the time",
            "precise", "approximate", "don't allow", "deny"
        )

        private val SYSTEM_PERMISSION_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller"
        )
    }

    /**
     * Inspects a screen hierarchy snapshot to detect if a blocking obstacle or dialog is present.
     */
    fun detectObstacle(snapshot: ScreenHierarchySnapshot): ObstacleInfo? {
        val elements = snapshot.elements
        if (elements.isEmpty()) return null

        val pkg = snapshot.packageName.lowercase()

        // 1. Check for Android System Runtime Permission Dialog
        if (SYSTEM_PERMISSION_PACKAGES.any { pkg.contains(it) } || pkg.contains("permissioncontroller")) {
            val allowBtn = elements.firstOrNull { el ->
                if (!el.isClickable && !el.className.contains("Button", ignoreCase = true)) return@firstOrNull false
                val label = (el.text.ifBlank { el.contentDescription ?: "" }).lowercase().trim()
                PERMISSION_PATTERNS.any { pattern ->
                    label == pattern || (pattern == "allow" && label == "allow") || label == "allow only while using the app"
                }
            }
            if (allowBtn != null) {
                return ObstacleInfo(
                    type = ObstacleType.PERMISSION_DIALOG,
                    title = snapshot.activityTitle ?: "System Permission Request",
                    dismissActionElement = allowBtn,
                    dismissActionLabel = allowBtn.text.ifBlank { allowBtn.contentDescription ?: "Allow" }
                )
            }
        }

        // 2. Check for standard modal dialogs, promotional popups, or rating overlays
        val dismissBtn = elements.firstOrNull { el ->
            if (!el.isClickable && !el.className.contains("Button", ignoreCase = true)) return@firstOrNull false
            val text = el.text.lowercase().trim()
            val desc = (el.contentDescription ?: "").lowercase().trim()
            DISMISS_PATTERNS.any { pattern ->
                text == pattern || desc == pattern || text == "close dialog" || desc == "close dialog"
            }
        }

        if (dismissBtn != null) {
            val titleCandidate = elements.firstOrNull { !it.isClickable && it.text.isNotBlank() }?.text
            return ObstacleInfo(
                type = ObstacleType.PROMO_MODAL,
                title = titleCandidate,
                dismissActionElement = dismissBtn,
                dismissActionLabel = dismissBtn.text.ifBlank { dismissBtn.contentDescription ?: "Dismiss" }
            )
        }

        return null
    }

    /**
     * Attempts to autonomously clear the detected obstacle.
     */
    fun clearObstacle(
        service: OrbitalAccessibilityService,
        obstacle: ObstacleInfo
    ): Boolean {
        Log.i(TAG, "Attempting to clear obstacle: ${obstacle.type} (${obstacle.dismissActionLabel})")

        val targetEl = obstacle.dismissActionElement
        if (targetEl != null) {
            // First try clicking by bounds center
            val bounds = targetEl.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                val tapped = service.tapCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                if (tapped) {
                    Log.d(TAG, "Cleared obstacle by tapping coordinates: (${bounds.centerX()}, ${bounds.centerY()})")
                    return true
                }
            }

            // Fallback: Click by element text
            val label = targetEl.text.ifBlank { targetEl.contentDescription ?: obstacle.dismissActionLabel }
            if (label.isNotBlank()) {
                val clicked = service.clickElementByText(label, exact = true)
                if (clicked) {
                    Log.d(TAG, "Cleared obstacle by clicking text: '$label'")
                    return true
                }
            }
        }

        // Fallback: Dispatch BACK key for non-system modal overlays
        if (obstacle.type != ObstacleType.PERMISSION_DIALOG) {
            val backPressed = service.pressGlobalKey("BACK")
            Log.d(TAG, "Attempted clearing obstacle with BACK key: result=$backPressed")
            return backPressed
        }

        return false
    }

    /**
     * Inspects screen and clears any obstacle if one is detected.
     */
    fun autoClearIfPresent(service: OrbitalAccessibilityService): Boolean {
        val snapshot = service.captureScreenHierarchy() ?: return false
        val obstacle = detectObstacle(snapshot) ?: return false
        return clearObstacle(service, obstacle)
    }
}
