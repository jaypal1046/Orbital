package com.orbital.action

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class StepStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    INFO
}

@Serializable
data class ExecutionStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: StepStatus = StepStatus.SUCCESS,
    val toolName: String? = null,
    val details: String? = null,
    val durationMs: Long = 0L
)
