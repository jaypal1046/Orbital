package com.orbital.chat

import com.orbital.data.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatEngine {
    val messages: StateFlow<List<ChatMessage>>
    val streamingContent: MutableStateFlow<String>
    val isStreaming: MutableStateFlow<Boolean>
    val activeProvider: MutableStateFlow<String?>
    val streamingChunk: StateFlow<String>
        get() = streamingContent

    fun setCharacter(character: String)
    fun clearMessages()
    suspend fun sendMessage(message: String)
    suspend fun handleStreamCompletion()
    suspend fun executeTTSAndActions()
}