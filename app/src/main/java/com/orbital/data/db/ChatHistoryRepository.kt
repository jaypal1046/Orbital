package com.orbital.data.db

import com.orbital.data.ChatMessage
import com.orbital.ui.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val encryptionHelper: ChatEncryptionHelper
) {

    companion object {
        const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    }

    suspend fun saveMessage(
        role: String,
        content: String,
        providerName: String? = null,
        modelName: String? = null,
        actionLabel: String? = null,
        actionDetails: String? = null
    ): ChatMessageEntity = withContext(Dispatchers.IO) {
        val encrypted = encryptionHelper.encrypt(content)
        val entity = ChatMessageEntity(
            role = role,
            encryptedContent = encrypted,
            providerName = providerName,
            modelName = modelName,
            actionLabel = actionLabel,
            actionDetails = actionDetails,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(entity)
        entity
    }

    suspend fun loadRecentHistory30Days(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        val entities = chatDao.getMessagesSince(cutoff)
        entities.map { entity ->
            val decrypted = encryptionHelper.decrypt(entity.encryptedContent)
            ChatMessage(
                role = entity.role,
                content = decrypted,
                actionLabel = entity.actionLabel,
                actionDetails = entity.actionDetails
            )
        }
    }

    suspend fun loadRecentUiMessages30Days(): List<UiMessage> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        val entities = chatDao.getMessagesSince(cutoff)
        entities.map { entity ->
            val decrypted = encryptionHelper.decrypt(entity.encryptedContent)
            UiMessage(
                id = entity.id,
                role = entity.role,
                content = decrypted,
                providerName = entity.providerName,
                modelName = entity.modelName,
                actionLabel = entity.actionLabel,
                actionDetails = entity.actionDetails,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun pruneOlderThan30Days(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        chatDao.pruneOldMessages(cutoff)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        chatDao.clearAllMessages()
    }
}
