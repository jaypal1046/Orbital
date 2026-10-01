package com.orbital.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UIElement(
    val text: String,
    val contentDescription: String?,
    val viewId: String?,
    val className: String,
    val isClickable: Boolean,
    val isScrollable: Boolean = false,
    val isEditable: Boolean,
    val bounds: Rect
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
                el.isEditable -> "[Input Field]"
                el.isClickable -> "[Button/Tab]"
                else -> "[Text]"
            }
            val label = el.text.ifBlank { el.contentDescription ?: el.viewId ?: "" }
            if (label.isNotBlank()) {
                val idStr = if (el.viewId != null) " (id: ${el.viewId})" else ""
                "  ${index + 1}. $type \"$label\"$idStr"
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

class OrbitalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OrbitalAccessibility"
        @Volatile
        var instance: OrbitalAccessibilityService? = null
            private set

        private val _currentForegroundPackage = MutableStateFlow<String>("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        private val _lastScreenSnapshot = MutableStateFlow<ScreenHierarchySnapshot?>(null)
        val lastScreenSnapshot: StateFlow<ScreenHierarchySnapshot?> = _lastScreenSnapshot.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${OrbitalAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(serviceName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "OrbitalAccessibilityService connected and ready for autonomous screen interaction.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName) {
            _currentForegroundPackage.value = pkg
            // Capture updated screen hierarchy when state or window changes
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                captureScreenHierarchy()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "OrbitalAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun captureScreenHierarchy(): ScreenHierarchySnapshot? {
        val root = rootInActiveWindow ?: return null
        val elements = mutableListOf<UIElement>()
        val pkg = root.packageName?.toString() ?: _currentForegroundPackage.value

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim()
            val viewId = node.viewIdResourceName

            if (text.isNotBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable || node.isScrollable) {
                elements.add(
                    UIElement(
                        text = text,
                        contentDescription = desc,
                        viewId = viewId,
                        className = node.className?.toString() ?: "",
                        isClickable = node.isClickable,
                        isScrollable = node.isScrollable,
                        isEditable = node.isEditable,
                        bounds = rect
                    )
                )
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        val snapshot = ScreenHierarchySnapshot(
            packageName = pkg,
            activityTitle = null,
            elements = elements
        )
        _lastScreenSnapshot.value = snapshot
        return snapshot
    }

    fun clickElementByText(query: String, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanQuery = query.lowercase().trim()

        val matchingNodes = root.findAccessibilityNodeInfosByText(cleanQuery)
        if (matchingNodes.isNullOrEmpty()) {
            // Fallback: full traversal search
            return findAndClickNodeByPredicate(root) { node ->
                val nodeText = node.text?.toString()?.lowercase()?.trim() ?: ""
                val nodeDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                if (exact) {
                    nodeText == cleanQuery || nodeDesc == cleanQuery
                } else {
                    nodeText.contains(cleanQuery) || nodeDesc.contains(cleanQuery)
                }
            }
        }

        for (node in matchingNodes) {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }

        return false
    }

    fun clickElementById(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (performClickOnNodeOrParent(node)) return true
            }
        }
        return false
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
        val root = rootInActiveWindow ?: return false

        // 1. If target label provided, try finding target edit field
        if (!targetHintOrLabel.isNullOrBlank()) {
            val labelNodes = root.findAccessibilityNodeInfosByText(targetHintOrLabel)
            for (node in labelNodes) {
                if (node.isEditable) {
                    return setNodeText(node, text)
                }
                // Check siblings/parent for editable field
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i)
                        if (sibling != null && sibling.isEditable) {
                            return setNodeText(sibling, text)
                        }
                    }
                }
            }
        }

        // 2. Fallback to currently focused or first editable field
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            return setNodeText(focused, text)
        }

        // 3. Find any editable node
        var foundEditable = false
        findAndClickNodeByPredicate(root) { node ->
            if (node.isEditable && !foundEditable) {
                foundEditable = setNodeText(node, text)
                foundEditable
            } else {
                false
            }
        }
        return foundEditable
    }

    fun performScroll(forward: Boolean = true): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val success = findAndPerformAction(root, action)
        if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Fallback to gesture-based scroll
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels.toFloat()
            val height = displayMetrics.heightPixels.toFloat()
            val startX = width / 2
            val startY = if (forward) height * 0.75f else height * 0.25f
            val endY = if (forward) height * 0.25f else height * 0.75f
            return swipeCoordinates(startX, startY, startX, endY, 300)
        }
        return success
    }

    fun swipeDirection(direction: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        val midX = width / 2
        val midY = height / 2

        return when (direction.uppercase().trim()) {
            "UP" -> swipeCoordinates(midX, height * 0.75f, midX, height * 0.25f, 300)
            "DOWN" -> swipeCoordinates(midX, height * 0.25f, midX, height * 0.75f, 300)
            "LEFT" -> swipeCoordinates(width * 0.85f, midY, width * 0.15f, midY, 300)
            "RIGHT" -> swipeCoordinates(width * 0.15f, midY, width * 0.85f, midY, 300)
            else -> performScroll(forward = true)
        }
    }

    fun swipeCoordinates(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun tapCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun pressGlobalKey(key: String): Boolean {
        return when (key.uppercase().trim()) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "POWER_DIALOG" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "LOCK_SCREEN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false
            "TAKE_SCREENSHOT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) else false
            else -> false
        }
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        // If not marked clickable, still attempt click on the node itself
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findAndClickNodeByPredicate(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(root)) {
            if (performClickOnNodeOrParent(root)) return true
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null && findAndClickNodeByPredicate(child, predicate)) {
                return true
            }
        }
        return false
    }

    private fun findAndPerformAction(root: AccessibilityNodeInfo, action: Int): Boolean {
        if (root.actionList.any { it.id == action }) {
            return root.performAction(action)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null && findAndPerformAction(child, action)) {
                return true
            }
        }
        return false
    }
}
