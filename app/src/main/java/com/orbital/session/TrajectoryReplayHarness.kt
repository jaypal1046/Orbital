package com.orbital.session

import com.orbital.automation.ScreenHierarchySnapshot
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReplayStepResult(
    val stepIndex: Int,
    val eventType: String,
    val payloadSummary: String,
    val success: Boolean
)

data class TrajectoryReplayReport(
    val sessionId: String,
    val totalEvents: Int,
    val successfulSteps: Int,
    val failedSteps: Int,
    val stepResults: List<ReplayStepResult>
)

object TrajectoryReplayHarness {

    private val json = Json { ignoreUnknownKeys = true }

    fun replaySessionFile(
        sessionFile: File,
        actionEvaluator: (eventType: String, payload: String) -> Boolean = { _, _ -> true }
    ): TrajectoryReplayReport {
        if (!sessionFile.exists()) {
            return TrajectoryReplayReport(
                sessionId = sessionFile.nameWithoutExtension,
                totalEvents = 0,
                successfulSteps = 0,
                failedSteps = 0,
                stepResults = emptyList()
            )
        }

        val lines = sessionFile.readLines().filter { it.isNotBlank() }
        val stepResults = mutableListOf<ReplayStepResult>()
        var successCount = 0
        var failCount = 0

        lines.forEachIndexed { index, line ->
            try {
                val element = json.parseToJsonElement(line).jsonObject
                val type = element["type"]?.jsonPrimitive?.content ?: "UNKNOWN"
                val payload = element["payload"]?.toString() ?: ""

                val ok = actionEvaluator(type, payload)
                if (ok) successCount++ else failCount++

                stepResults.add(
                    ReplayStepResult(
                        stepIndex = index + 1,
                        eventType = type,
                        payloadSummary = payload.take(60),
                        success = ok
                    )
                )
            } catch (_: Exception) {
                failCount++
            }
        }

        return TrajectoryReplayReport(
            sessionId = sessionFile.nameWithoutExtension,
            totalEvents = lines.size,
            successfulSteps = successCount,
            failedSteps = failCount,
            stepResults = stepResults
        )
    }
}
