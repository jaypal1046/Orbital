package com.orbital.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getMessagesSince(sinceTimestamp: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun observeMessagesSince(sinceTimestamp: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldMessages(cutoffTimestamp: Long): Int

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
