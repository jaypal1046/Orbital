package com.orbital.session

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SessionEventLoggerTest {

    private lateinit var context: Context
    private lateinit var logger: SessionEventLogger
    private lateinit var testSessionId: String

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        logger = SessionEventLogger(context)
        testSessionId = "test_session_${UUID.randomUUID()}"
    }

    @After
    fun tearDown() = runTest {
        logger.deleteTranscript(testSessionId)
    }

    @Test
    fun `logEvent appends JSONL entries and readTranscript retrieves them`() = runTest {
        logger.logEvent(
            sessionId = testSessionId,
            type = SessionEventType.USER_INPUT,
            source = "PHONE_UI",
            summary = "Turn on flashlight"
        )

        logger.logEvent(
            sessionId = testSessionId,
            type = SessionEventType.TOOL_DISPATCH,
            source = "REACT_ENGINE",
            summary = "Executed FLASHLIGHT action",
            payload = mapOf("action" to "FLASHLIGHT", "status" to "SUCCESS")
        )

        val transcript = logger.readTranscript(testSessionId)
        assertThat(transcript).hasSize(2)
        assertThat(transcript[0].type).isEqualTo(SessionEventType.USER_INPUT)
        assertThat(transcript[0].summary).isEqualTo("Turn on flashlight")
        assertThat(transcript[1].type).isEqualTo(SessionEventType.TOOL_DISPATCH)
        assertThat(transcript[1].payload["action"]).isEqualTo("FLASHLIGHT")
    }

    @Test
    fun `readTranscriptRaw returns raw JSONL content with newlines`() = runTest {
        logger.logEvent(
            sessionId = testSessionId,
            type = SessionEventType.SYSTEM,
            source = "TEST",
            summary = "System initialized"
        )

        val raw = logger.readTranscriptRaw(testSessionId)
        assertThat(raw).contains("\"type\":\"SYSTEM\"")
        assertThat(raw).contains("\"summary\":\"System initialized\"")
    }

    @Test
    fun `getTranscriptFile points to valid file path`() {
        val file = logger.getTranscriptFile(testSessionId)
        assertThat(file.name).isEqualTo("$testSessionId.jsonl")
    }
}
