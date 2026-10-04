package com.orbital.chat

import com.orbital.data.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

data class CompactionResult(
    val compactedMessages: List<ChatMessage>,
    val wasCompacted: Boolean,
    val originalCount: Int,
    val compactedCount: Int,
    val tokensSavedEstimate: Int
)

@Singleton
class ContextCompactionEngine @Inject constructor() {

    companion object {
        const val DEFAULT_MAX_TURNS = 10
        const val DEFAULT_MAX_CHARS = 6000
        const val RECENT_TURNS_TO_PRESERVE = 4
    }

    /**
     * Compacts chat message history if it exceeds turn or character thresholds.
     */
    fun compact(
        messages: List<ChatMessage>,
        maxTurns: Int = DEFAULT_MAX_TURNS,
        maxChars: Int = DEFAULT_MAX_CHARS,
        preserveRecentCount: Int = RECENT_TURNS_TO_PRESERVE
    ): CompactionResult {
        if (messages.size <= maxTurns) {
            val totalLength = messages.sumOf { it.content?.length ?: 0 }
            if (totalLength <= maxChars) {
                return CompactionResult(
                    compactedMessages = messages,
                    wasCompacted = false,
                    originalCount = messages.size,
                    compactedCount = messages.size,
                    tokensSavedEstimate = 0
                )
            }
        }

        // Separate system messages (if any at index 0) from conversation
        val systemMessage = messages.firstOrNull { it.role == "system" }
        val dialogMessages = if (systemMessage != null) messages.drop(1) else messages

        if (dialogMessages.size <= preserveRecentCount) {
            return CompactionResult(
                compactedMessages = messages,
                wasCompacted = false,
                originalCount = messages.size,
                compactedCount = messages.size,
                tokensSavedEstimate = 0
            )
        }

        val olderMessages = dialogMessages.dropLast(preserveRecentCount)
        val recentMessages = dialogMessages.takeLast(preserveRecentCount)

        val summarySb = StringBuilder()
        summarySb.append("[COMPACTED CONTEXT SUMMARY (${olderMessages.size} turns)]\n")

        olderMessages.forEach { msg ->
            val cleanContent = msg.content.orEmpty().lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            when (msg.role) {
                "user" -> summarySb.append("• User: \"${cleanContent.take(60)}\"\n")
                "assistant" -> {
                    if (!msg.actionLabel.isNullOrBlank()) {
                        summarySb.append("• Assistant: Executed ${msg.actionLabel}\n")
                    } else {
                        summarySb.append("• Assistant: \"${cleanContent.take(40)}\"\n")
                    }
                }
                else -> summarySb.append("• ${msg.role}: \"${cleanContent.take(40)}\"\n")
            }
        }

        val compactedMessage = ChatMessage(
            role = "assistant",
            content = summarySb.toString().trim(),
            actionLabel = "📦 Compacted History (${olderMessages.size} turns)"
        )

        val resultList = mutableListOf<ChatMessage>()
        if (systemMessage != null) {
            resultList.add(systemMessage)
        }
        resultList.add(compactedMessage)
        resultList.addAll(recentMessages)

        val originalLength = messages.sumOf { it.content?.length ?: 0 }
        val compactedLength = resultList.sumOf { it.content?.length ?: 0 }
        val savedChars = (originalLength - compactedLength).coerceAtLeast(0)
        val tokensSaved = savedChars / 4

        return CompactionResult(
            compactedMessages = resultList,
            wasCompacted = true,
            originalCount = messages.size,
            compactedCount = resultList.size,
            tokensSavedEstimate = tokensSaved
        )
    }
}
