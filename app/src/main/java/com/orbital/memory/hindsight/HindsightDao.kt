package com.orbital.memory.hindsight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HindsightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("SELECT * FROM hindsight_memories WHERE bankId = :bankId AND contextKey = :contextKey")
    suspend fun getMemoriesByContext(bankId: String, contextKey: String): List<MemoryEntity>

    @Query("SELECT * FROM hindsight_memories WHERE bankId = :bankId AND category = :category ORDER BY lastAccessedTimestamp DESC LIMIT :limit")
    suspend fun getRecentByCategory(bankId: String, category: MemoryType, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM hindsight_memories WHERE bankId = :bankId ORDER BY lastAccessedTimestamp DESC LIMIT 200")
    suspend fun getAllActiveMemories(bankId: String): List<MemoryEntity>

    @Query("UPDATE hindsight_memories SET reinforcementCount = reinforcementCount + 1, lastAccessedTimestamp = :now WHERE id = :id")
    suspend fun reinforceMemory(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM hindsight_memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM hindsight_memories WHERE bankId = :bankId")
    suspend fun clearBank(bankId: String)
}
