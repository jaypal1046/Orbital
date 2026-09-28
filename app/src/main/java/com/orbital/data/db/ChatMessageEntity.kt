package com.orbital.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["role"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val encryptedContent: String,
    val providerName: String? = null,
    val modelName: String? = null,
    val actionLabel: String? = null,
    val actionDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
