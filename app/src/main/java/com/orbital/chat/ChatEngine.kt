package com.orbital.chat

import com.orbital.action.DeviceAction
import com.orbital.data.ChatMessage
import com.orbital.data.db.ChatSessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatEngine {
    val messages: StateFlow<List<ChatMessage>>
    val streamingContent: MutableStateFlow<String>
    val isStreaming: MutableStateFlow<Boolean>
    val activeProvider: MutableStateFlow<String?>
    val pendingConfirmation: StateFlow<DeviceAction?>
    val sessions: StateFlow<List<ChatSessionSummary>>
    val streamingChunk: StateFlow<String>
        get() = streamingContent

    fun setCharacter(character: String)
    fun clearMessages()
    fun newSession()
    fun loadSession(sessionId: String)
    fun renameSession(sessionId: String, title: String)
    suspend fun sendMessage(message: String)
    suspend fun handleStreamCompletion()
    suspend fun executeTTSAndActions()
    fun confirmPendingAction()
    fun cancelPendingAction()
}
