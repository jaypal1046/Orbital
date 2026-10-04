package com.orbital.chat

import com.google.common.truth.Truth.assertThat
import com.orbital.data.ChatMessage
import org.junit.Before
import org.junit.Test

class ContextCompactionEngineTest {

    private lateinit var compactionEngine: ContextCompactionEngine

    @Before
    fun setUp() {
        compactionEngine = ContextCompactionEngine()
    }

    @Test
    fun `compact returns uncompacted list when message count is small`() {
        val messages = listOf(
            ChatMessage(role = "user", content = "Hello"),
            ChatMessage(role = "assistant", content = "Hi! How can I help?")
        )

        val result = compactionEngine.compact(messages, maxTurns = 5)
        assertThat(result.wasCompacted).isFalse()
        assertThat(result.compactedMessages).isEqualTo(messages)
    }

    @Test
    fun `compact summarizes older messages and preserves recent turns`() {
        val messages = mutableListOf<ChatMessage>()
        for (i in 1..8) {
            messages.add(ChatMessage(role = "user", content = "User prompt number $i requesting detailed assistance with device settings and screen navigation."))
            messages.add(ChatMessage(role = "assistant", content = "Assistant response $i with detailed step-by-step instructions and long output diagnostics for the user.", actionLabel = "Action $i"))
        }

        // Total 16 messages. Compact with maxTurns = 6, preserveRecentCount = 4
        val result = compactionEngine.compact(messages, maxTurns = 6, preserveRecentCount = 4)

        assertThat(result.wasCompacted).isTrue()
        // Result should have: 1 summary message + 4 recent messages = 5 messages
        assertThat(result.compactedMessages.size).isEqualTo(5)
        assertThat(result.compactedMessages[0].content).contains("[COMPACTED CONTEXT SUMMARY")
        assertThat(result.compactedMessages[0].content).contains("User prompt number 1")
        assertThat(result.compactedMessages.last().content).contains("Assistant response 8")
        assertThat(result.tokensSavedEstimate).isGreaterThan(0)
    }

    @Test
    fun `compact preserves system message at head if present`() {
        val systemMsg = ChatMessage(role = "system", content = "You are Orbital AI.")
        val messages = mutableListOf(systemMsg)
        for (i in 1..10) {
            messages.add(ChatMessage(role = "user", content = "Command $i"))
            messages.add(ChatMessage(role = "assistant", content = "Result $i"))
        }

        val result = compactionEngine.compact(messages, maxTurns = 5, preserveRecentCount = 2)

        assertThat(result.wasCompacted).isTrue()
        assertThat(result.compactedMessages.first().role).isEqualTo("system")
        assertThat(result.compactedMessages.first().content).isEqualTo("You are Orbital AI.")
        assertThat(result.compactedMessages[1].content).contains("[COMPACTED CONTEXT SUMMARY")
    }
}
