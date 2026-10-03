package com.orbital.memory.hindsight

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class MemoryType {
    HABIT,              // User habits & recurrent preferences (e.g. preferred cab tier, default app choices)
    APP_QUIRK,          // App-specific automation workarounds & timing requirements
    PROCEDURAL_TRACE,   // Successful multi-step automation sequences
    FACTUAL_RELATION    // User identity, aliases, relational defaults
}

@Entity(
    tableName = "hindsight_memories",
    indices = [
        Index(value = ["bankId", "category"]),
        Index(value = ["contextKey"]),
        Index(value = ["lastAccessedTimestamp"])
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: String = "default_user",
    val category: MemoryType,
    val contextKey: String,
    val summary: String,
    val detailJson: String = "{}",
    val rawEmbedding: String = "", // Comma-separated Float string for SQLite portability
    val confidence: Float = 1.0f,
    val reinforcementCount: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)

data class ScoredMemory(
    val memory: MemoryEntity,
    val score: Float,
    val vectorSimilarity: Float
)

@Serializable
data class MemoryReflection(
    val summary: String,
    val category: String,
    val contextKey: String,
    val confidence: Float
)
