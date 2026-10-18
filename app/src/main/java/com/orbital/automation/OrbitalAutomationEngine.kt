package com.orbital.automation

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

data class UIElement(
    val text: String,
    val contentDescription: String?,
    val viewId: String?,
    val className: String,
    val isClickable: Boolean,
    val isScrollable: Boolean = false,
    val isEditable: Boolean,
    val bounds: Rect,
    val hintText: String? = null,
    val isPassword: Boolean = false
)

data class ScreenHierarchySnapshot(
    val packageName: String,
    val activityTitle: String?,
    val elements: List<UIElement>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPromptSummary(): String {
        val sb = StringBuilder()
        sb.append("Current Screen Foreground App: $packageName\n")
        if (!activityTitle.isNullOrBlank()) {
            sb.append("Screen Title: $activityTitle\n")
        }
        val labeledElements = elements.mapIndexedNotNull { index, el ->
            val type = when {
                el.isPassword -> "[Password Field]"
                el.isEditable -> "[Input Field]"
                el.isClickable -> "[Button/Tab]"
                else -> "[Text]"
            }
            val label = if (el.isEditable) el.hintText ?: el.viewId ?: ""
                else el.text.ifBlank { el.contentDescription ?: el.viewId ?: "" }
            if (label.isNotBlank()) {
                val idStr = if (el.viewId != null) " (id: ${el.viewId})" else ""
                val hintStr = el.hintText?.takeIf { el.isEditable && it != label }?.let { " (hint: $it)" }.orEmpty()
                "  ${index + 1}. $type \"$label\"$hintStr$idStr"
            } else null
        }

        if (labeledElements.isNotEmpty()) {
            sb.append("Visible UI Elements & Controls (${labeledElements.size}):\n")
            labeledElements.forEach { sb.append(it).append("\n") }
        } else {
            sb.append("Visible UI Elements: 0 text/interactive elements detected.\n")
            sb.append("• Notice: The screen may still be loading, rendering a canvas/custom view, or secured.\n")
            sb.append("• Quick Action: Tap 'Read Live Screen' to re-scan or switch to Web Search fallback.\n")
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Universal dynamic on-device automation engine.
 * Completely replaces Android AccessibilityService with rootless ADB Wireless (Shizuku),
 * local daemon script execution, and standard Android Intents.
 * Zero AccessibilityService registered -> 100% Google Pay and banking payment safe.
 */
class OrbitalAutomationEngine {

    companion object {
        private const val TAG = "OrbitalAutomation"

        @Volatile
        private var _instance: OrbitalAutomationEngine? = OrbitalAutomationEngine()
        val instance: OrbitalAutomationEngine?
            get() {
                if (_instance == null) {
                    _instance = OrbitalAutomationEngine()
                }
                return _instance
            }

        private val _currentForegroundPackage = MutableStateFlow<String>("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        private val _lastScreenSnapshot = MutableStateFlow<ScreenHierarchySnapshot?>(null)
        val lastScreenSnapshot: StateFlow<ScreenHierarchySnapshot?> = _lastScreenSnapshot.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            return ShellAutomationManager.isReady()
        }

        fun openSettings(context: Context) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun captureScreenHierarchy(): ScreenHierarchySnapshot? {
        val snapshot = runBlocking {
            ShizukuAutomationManager.dumpScreenHierarchy()
        } ?: return null

        if (snapshot.packageName.isNotBlank()) {
            _currentForegroundPackage.value = snapshot.packageName
        }
        _lastScreenSnapshot.value = snapshot
        return snapshot
    }

    fun clickElementByText(query: String, exact: Boolean = false): Boolean {
        val snapshot = captureScreenHierarchy() ?: return false
        val cleanQuery = query.lowercase().trim()

        val target = snapshot.elements.firstOrNull { el ->
            val text = el.text.lowercase().trim()
            val desc = el.contentDescription?.lowercase()?.trim() ?: ""
            if (exact) {
                text == cleanQuery || desc == cleanQuery
            } else {
                text.contains(cleanQuery) || desc.contains(cleanQuery)
            }
        } ?: return false

        val centerX = target.bounds.centerX()
        val centerY = target.bounds.centerY()
        return tapCoordinates(centerX.toFloat(), centerY.toFloat())
    }

    fun clickElementById(viewId: String): Boolean {
        val snapshot = captureScreenHierarchy() ?: return false
        val cleanId = viewId.trim()

        val target = snapshot.elements.firstOrNull { el ->
            el.viewId?.equals(cleanId, ignoreCase = true) == true ||
            el.viewId?.substringAfterLast('/')?.equals(cleanId.substringAfterLast('/'), ignoreCase = true) == true
        } ?: return false

        val centerX = target.bounds.centerX()
        val centerY = target.bounds.centerY()
        return tapCoordinates(centerX.toFloat(), centerY.toFloat())
    }

    suspend fun clickElementSmart(
        goalDescription: String,
        ranker: com.orbital.decision.jev.AccessibilityNodeRanker = com.orbital.decision.jev.AccessibilityNodeRanker(com.orbital.decision.jev.JevLocalFallbackEngine())
    ): Boolean {
        val snapshot = captureScreenHierarchy() ?: return false
        if (snapshot.elements.isEmpty()) return false

        val ranked = ranker.rankElementsForGoal(
            goalDescription = goalDescription,
            screenContext = snapshot.toPromptSummary(),
            elements = snapshot.elements
        )

        val topMatch = ranked.firstOrNull() ?: return false
        val bounds = topMatch.element.bounds
        return tapCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    fun inputText(text: String, targetHintOrLabel: String? = null): Boolean {
        if (!targetHintOrLabel.isNullOrBlank()) {
            clickElementByText(targetHintOrLabel, exact = false)
            Thread.sleep(150)
        }
        return runBlocking {
            ShizukuAutomationManager.typeText(text)
        }
    }

    fun performScroll(forward: Boolean = true): Boolean {
        val screenHeight = 2400
        val screenWidth = 1080
        val midX = screenWidth / 2
        val startY = if (forward) (screenHeight * 0.75).toInt() else (screenHeight * 0.25).toInt()
        val endY = if (forward) (screenHeight * 0.25).toInt() else (screenHeight * 0.75).toInt()

        return runBlocking {
            ShizukuAutomationManager.swipe(midX, startY, midX, endY, 300)
        }
    }

    fun swipeDirection(direction: String): Boolean {
        val screenHeight = 2400
        val screenWidth = 1080
        val midX = screenWidth / 2
        val midY = screenHeight / 2

        return runBlocking {
            when (direction.uppercase().trim()) {
                "UP" -> ShizukuAutomationManager.swipe(midX, (screenHeight * 0.75).toInt(), midX, (screenHeight * 0.25).toInt(), 300)
                "DOWN" -> ShizukuAutomationManager.swipe(midX, (screenHeight * 0.25).toInt(), midX, (screenHeight * 0.75).toInt(), 300)
                "LEFT" -> ShizukuAutomationManager.swipe((screenWidth * 0.85).toInt(), midY, (screenWidth * 0.15).toInt(), midY, 300)
                "RIGHT" -> ShizukuAutomationManager.swipe((screenWidth * 0.15).toInt(), midY, (screenWidth * 0.85).toInt(), midY, 300)
                else -> ShizukuAutomationManager.swipe(midX, (screenHeight * 0.75).toInt(), midX, (screenHeight * 0.25).toInt(), 300)
            }
        }
    }

    fun swipeCoordinates(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        return runBlocking {
            ShizukuAutomationManager.swipe(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt(), durationMs)
        }
    }

    fun tapCoordinates(x: Float, y: Float): Boolean {
        return runBlocking {
            ShizukuAutomationManager.tap(x.toInt(), y.toInt())
        }
    }

    suspend fun captureScreenshotAsync(): File? {
        val cacheDir = File("/sdcard/Download").takeIf { it.exists() && it.canWrite() }
            ?: File("/data/local/tmp")
        val file = File(cacheDir, "orbital_shot_${System.currentTimeMillis()}.png")

        val success = ShizukuAutomationManager.captureScreenshot(file)
        return if (success && file.exists()) file else null
    }

    fun pressGlobalKey(key: String): Boolean {
        return runBlocking {
            ShizukuAutomationManager.pressKey(key)
        }
    }

    suspend fun executeScript(context: Context, script: String): ShellResult {
        return ShizukuAutomationManager.executeDynamicScript(context, script)
    }
}

