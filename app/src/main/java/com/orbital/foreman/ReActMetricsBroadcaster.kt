package com.orbital.foreman

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ReActMetricEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val stepIndex: Int,
    val stepName: String,
    val status: String,
    val durationMs: Long,
    val stateHash: String,
    val note: String? = null
)

@Singleton
class ReActMetricsBroadcaster @Inject constructor() {

    private val _metricEvents = MutableSharedFlow<ReActMetricEvent>(extraBufferCapacity = 64)
    val metricEvents: SharedFlow<ReActMetricEvent> = _metricEvents.asSharedFlow()

    fun emitMetric(
        stepIndex: Int,
        stepName: String,
        status: String,
        durationMs: Long,
        stateHash: String,
        note: String? = null
    ) {
        _metricEvents.tryEmit(
            ReActMetricEvent(
                stepIndex = stepIndex,
                stepName = stepName,
                status = status,
                durationMs = durationMs,
                stateHash = stateHash,
                note = note
            )
        )
    }
}
