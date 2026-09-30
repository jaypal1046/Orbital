package com.orbital.decision.jev

import kotlinx.serialization.Serializable

@Serializable
enum class DecisionKind {
    BINARY,
    CHOICE,
    SCORE,
    CLASSIFICATION
}

@Serializable
data class DecisionMetadata(
    val latencyMs: Long,
    val modelIdentifier: String,
    val tokenUsage: Int = 0,
    val isLocalExecution: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface JevDecision<out T> {
    val value: T
    val confidence: Float // 0.0f to 1.0f
    val metadata: DecisionMetadata
    val reasoningHint: String?
}

@Serializable
data class BooleanDecision(
    override val value: Boolean,
    override val confidence: Float,
    override val metadata: DecisionMetadata,
    override val reasoningHint: String? = null
) : JevDecision<Boolean>

data class ChoiceDecision<T>(
    override val value: T,
    val selectedIndex: Int,
    val distribution: Map<T, Float>,
    override val confidence: Float,
    override val metadata: DecisionMetadata,
    override val reasoningHint: String? = null
) : JevDecision<T>

@Serializable
data class ScoreDecision(
    override val value: Float, // Normalized 0.0f to 1.0f
    override val confidence: Float,
    override val metadata: DecisionMetadata,
    override val reasoningHint: String? = null
) : JevDecision<Float>
