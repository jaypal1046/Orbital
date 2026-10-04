package com.orbital.session

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton

enum class SessionEventType {
    USER_INPUT,
    MODEL_THOUGHT,
    MODEL_OUTPUT,
    TOOL_DISPATCH,
    OBSERVATION,
    OBSTACLE_CLEARED,
    VERIFICATION,
    ERROR,
    SYSTEM
}

@Serializable
data class SessionLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val type: SessionEventType,
    val source: String,
    val summary: String,
    val payload: Map<String, String> = emptyMap()
)

@Singleton
class SessionEventLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SessionEventLogger"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    private val sessionsDir: File by lazy {
        File(context.filesDir, "sessions").apply { if (!exists()) mkdirs() }
    }

    /**
     * Appends an event to the session's JSONL log file.
     */
    suspend fun logEvent(
        sessionId: String,
        type: SessionEventType,
        source: String,
        summary: String,
        payload: Map<String, String> = emptyMap()
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val file = File(sessionsDir, "$sessionId.jsonl")
            val entry = SessionLogEntry(
                sessionId = sessionId,
                type = type,
                source = source,
                summary = summary,
                payload = payload
            )
            val jsonLine = json.encodeToString(entry)
            FileWriter(file, true).use { writer ->
                writer.appendLine(jsonLine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log session event to JSONL", e)
        }
    }

    /**
     * Reads all events from a session's JSONL log file.
     */
    suspend fun readTranscript(
        sessionId: String,
        limit: Int = 500
    ): List<SessionLogEntry> = withContext(Dispatchers.IO) {
        val file = File(sessionsDir, "$sessionId.jsonl")
        if (!file.exists()) return@withContext emptyList()

        try {
            file.readLines()
                .filter { it.isNotBlank() }
                .takeLast(limit)
                .mapNotNull { line ->
                    runCatching { json.decodeFromString<SessionLogEntry>(line) }.getOrNull()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read transcript for session $sessionId", e)
            emptyList()
        }
    }

    /**
     * Returns the raw JSONL text content for a session transcript.
     */
    suspend fun readTranscriptRaw(sessionId: String): String = withContext(Dispatchers.IO) {
        val file = File(sessionsDir, "$sessionId.jsonl")
        if (!file.exists()) "" else file.readText()
    }

    /**
     * Returns the transcript file for exporting or sharing.
     */
    fun getTranscriptFile(sessionId: String): File {
        return File(sessionsDir, "$sessionId.jsonl")
    }

    /**
     * Deletes a session transcript.
     */
    suspend fun deleteTranscript(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(sessionsDir, "$sessionId.jsonl")
        if (file.exists()) file.delete() else true
    }
}
