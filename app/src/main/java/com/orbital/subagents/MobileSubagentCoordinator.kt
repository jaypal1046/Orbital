package com.orbital.subagents

import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class SubagentTaskResult {
    data class WatcherTriggered(val eventText: String, val matchedElement: UIElement?, val timestamp: Long = System.currentTimeMillis()) : SubagentTaskResult()
    data class CrawlerCompleted(val visitedScreens: Int, val discoveredElements: Int, val transitionLog: List<String>) : SubagentTaskResult()
    data class TimedOut(val message: String) : SubagentTaskResult()
}

class MobileSubagentCoordinator(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {

    private val activeWatchers = mutableMapOf<String, CompletableDeferred<SubagentTaskResult>>()

    fun spawnWatcher(
        targetQuery: String,
        timeoutMs: Long = 30_000,
        screenProvider: () -> ScreenHierarchySnapshot?
    ): CompletableDeferred<SubagentTaskResult> {
        val watcherId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<SubagentTaskResult>()
        activeWatchers[watcherId] = deferred

        scope.launch {
            val startTime = System.currentTimeMillis()
            val cleanTarget = targetQuery.lowercase().trim()

            while (isActive && System.currentTimeMillis() - startTime < timeoutMs) {
                val snapshot = screenProvider()
                if (snapshot != null) {
                    val match = snapshot.elements.firstOrNull { el ->
                        val text = el.text.lowercase()
                        val desc = el.contentDescription?.lowercase() ?: ""
                        text.contains(cleanTarget) || desc.contains(cleanTarget)
                    }
                    if (match != null) {
                        val result = SubagentTaskResult.WatcherTriggered(match.text.ifBlank { match.contentDescription ?: targetQuery }, match)
                        deferred.complete(result)
                        activeWatchers.remove(watcherId)
                        return@launch
                    }
                }
                delay(800)
            }

            val timeoutResult = SubagentTaskResult.TimedOut("Watcher for '$targetQuery' timed out after ${timeoutMs / 1000}s.")
            deferred.complete(timeoutResult)
            activeWatchers.remove(watcherId)
        }

        return deferred
    }

    fun spawnAppCrawler(
        maxSteps: Int = 10,
        clickExecutor: (UIElement) -> Boolean,
        screenProvider: () -> ScreenHierarchySnapshot?
    ): CompletableDeferred<SubagentTaskResult> {
        val deferred = CompletableDeferred<SubagentTaskResult>()

        scope.launch {
            val visitedScreens = mutableSetOf<String>()
            val transitionLog = mutableListOf<String>()
            var discoveredElementsCount = 0

            for (step in 1..maxSteps) {
                val snapshot = screenProvider() ?: break
                val screenKey = "${snapshot.packageName}_${snapshot.elements.size}"
                visitedScreens.add(screenKey)
                discoveredElementsCount += snapshot.elements.size

                val clickables = snapshot.elements.filter { it.isClickable && it.text.isNotBlank() }
                if (clickables.isEmpty()) {
                    transitionLog.add("Step $step: No clickable elements on screen.")
                    break
                }

                val targetToClick = clickables.first()
                transitionLog.add("Step $step: Clicked [${targetToClick.text}] on ${snapshot.packageName}")
                clickExecutor(targetToClick)
                delay(1000)
            }

            val result = SubagentTaskResult.CrawlerCompleted(
                visitedScreens = visitedScreens.size,
                discoveredElements = discoveredElementsCount,
                transitionLog = transitionLog
            )
            deferred.complete(result)
        }

        return deferred
    }
}
