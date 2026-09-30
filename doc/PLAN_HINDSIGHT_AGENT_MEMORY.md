# Deep Technical Plan: Hindsight Semantic Agent Memory & Experience Graph (Kotlin Native)

## 1. Executive Summary & Source Analysis
- **Source Repository**: [`vectorize-io/hindsight`](https://github.com/vectorize-io/hindsight) (Long-term Agent Memory & Continuous Learning System).
- **Core Mechanism**: Unlike basic RAG (which just retrieves flat chunks by semantic similarity), Hindsight employs a **Retain $\rightarrow$ Recall $\rightarrow$ Reflect** cycle across four hybrid retrieval dimensions:
  1. **Semantic Vector Search**: Cosine similarity over on-device embeddings.
  2. **Keyword / Exact Search**: SQLite FTS5 index for specific identifiers, names, package names.
  3. **Temporal Decay & Recency**: Exponential half-life decay on memory relevance.
  4. **Graph / Relational Associations**: Associating concepts, tools, and past error resolutions.
- **Orbital Problem Solved**:
  - Dynamically learning user habits (e.g., preferred cab tier, standard contact nicknames, food order preferences) without hardcoding.
  - Recording app automation quirks (e.g., "App X needs a 500ms delay after opening search").
  - Preserving conversational context across sessions while keeping prompt token counts minimal.

---

## 2. Mathematical Formulations & Algorithms

### 2.1 Hybrid Ranking Score ($S_{hybrid}$)
For a candidate memory $m$ given query $q$, the combined score is computed as:

$$S(m, q) = w_v \cdot \text{CosineSimilarity}(v_q, v_m) + w_k \cdot \text{BM25Score}(q, m) + w_t \cdot e^{-\lambda (t_{now} - t_{last})} + w_r \cdot \text{Confidence}(m)$$

*Recommended Weights*: $w_v = 0.40, w_k = 0.30, w_t = 0.15, w_r = 0.15, \lambda = 0.0001 \text{ hr}^{-1}$.

### 2.2 Cosine Similarity Vector Calculation in Kotlin
$$\text{CosineSimilarity}(A, B) = \frac{\sum_{i=1}^d A_i B_i}{\sqrt{\sum_{i=1}^d A_i^2} \cdot \sqrt{\sum_{i=1}^d B_i^2}}$$

---

## 3. Kotlin Architecture (`com.orbital.memory.hindsight`)

```
app/src/main/java/com/orbital/memory/hindsight/
├── HindsightContracts.kt         # Data entities, memory categories, retrieval queries
├── db/
│   ├── HindsightDatabase.kt      # Room Database definition with FTS5 support
│   ├── MemoryEntity.kt           # Room table schema
│   ├── HindsightDao.kt           # DAOs for vector, keyword, and category queries
│   └── VectorTypeConverter.kt    # Serialization for FloatArray embeddings
├── embedding/
│   ├── TextEmbeddingProvider.kt  # Interface for on-device/remote embedding generation
│   └── LiteRtEmbeddingProvider.kt # On-device LiteRT / MediaPipe embedding generator
├── HindsightMemoryEngine.kt      # Core interface for Retain, Recall, Reflect
└── HindsightMemoryEngineImpl.kt  # Hybrid scoring and retrieval implementation
```

---

## 4. Complete Kotlin Implementation Blueprint

### 4.1 Room Entity & Database Schema (`MemoryEntity.kt`)

```kotlin
package com.orbital.memory.hindsight.db

import androidx.room.*

enum class MemoryType {
    HABIT,              // User habits & recurrent preferences
    APP_QUIRK,          // App-specific automation workarounds & delays
    PROCEDURAL_TRACE,   // Successful multi-step action sequences
    FACTUAL_RELATION    // User identity, aliases, preferred defaults
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
    val bankId: String = "default_user", // Multi-user / profile isolation
    val category: MemoryType,
    val contextKey: String, // e.g. "cab_booking", "com.whatsapp", "food_delivery"
    val summary: String,
    val detailJson: String,
    val rawEmbedding: String, // Comma-separated Float string for SQLite portability
    val confidence: Float = 1.0f,
    val reinforcementCount: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)
```

### 4.2 Room DAO (`HindsightDao.kt`)

```kotlin
package com.orbital.memory.hindsight.db

import androidx.room.*

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
}
```

### 4.3 High-Performance Hybrid Search Engine (`HindsightMemoryEngineImpl.kt`)

```kotlin
package com.orbital.memory.hindsight

import com.orbital.memory.hindsight.db.HindsightDao
import com.orbital.memory.hindsight.db.MemoryEntity
import com.orbital.memory.hindsight.db.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class HindsightMemoryEngineImpl(
    private val dao: HindsightDao,
    private val embeddingProvider: TextEmbeddingProvider
) : HindsightMemoryEngine {

    override suspend fun retain(
        bankId: String,
        category: MemoryType,
        contextKey: String,
        summary: String,
        detailJson: String
    ): Long = withContext(Dispatchers.IO) {
        val embedding = embeddingProvider.generateEmbedding(summary)
        val embeddingStr = embedding.joinToString(",")

        val entity = MemoryEntity(
            bankId = bankId,
            category = category,
            contextKey = contextKey,
            summary = summary,
            detailJson = detailJson,
            rawEmbedding = embeddingStr,
            confidence = 1.0f,
            reinforcementCount = 1
        )
        dao.insertMemory(entity)
    }

    override suspend fun recall(
        bankId: String,
        query: String,
        contextKey: String?,
        limit: Int
    ): List<ScoredMemory> = withContext(Dispatchers.IO) {
        val queryEmbedding = embeddingProvider.generateEmbedding(query)
        val allMemories = dao.getAllActiveMemories(bankId)
        val now = System.currentTimeMillis()

        val scored = allMemories.map { memory ->
            val memEmbedding = parseEmbedding(memory.rawEmbedding)
            val vectorSim = if (memEmbedding != null) cosineSimilarity(queryEmbedding, memEmbedding) else 0f
            val keywordSim = if (memory.summary.contains(query, ignoreCase = true) || memory.contextKey.contains(query, ignoreCase = true)) 1.0f else 0.0f
            val hoursElapsed = (now - memory.lastAccessedTimestamp) / (1000f * 60 * 60)
            val temporalWeight = kotlin.math.exp(-0.001f * hoursElapsed)
            val contextBonus = if (contextKey != null && memory.contextKey.equals(contextKey, ignoreCase = true)) 0.3f else 0.0f

            val totalScore = (0.40f * vectorSim) + (0.30f * keywordSim) + (0.15f * temporalWeight) + contextBonus

            ScoredMemory(
                memory = memory,
                score = totalScore,
                vectorSimilarity = vectorSim
            )
        }

        val topResults = scored.filter { it.score > 0.35f }
            .sortedByDescending { it.score }
            .take(limit)

        // Reinforce top recalled memories
        topResults.forEach { dao.reinforceMemory(it.memory.id) }

        topResults
    }

    override suspend fun buildPromptContext(bankId: String, query: String, contextKey: String?): String {
        val recalled = recall(bankId, query, contextKey, limit = 4)
        if (recalled.isEmpty()) return ""

        return buildString {
            appendLine("### RECALLED LONG-TERM MEMORIES (Learned Preferences & Quirks):")
            recalled.forEach { item ->
                appendLine("- [${item.memory.category}] ${item.memory.summary}")
            }
            appendLine()
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun parseEmbedding(raw: String): FloatArray? {
        if (raw.isBlank()) return null
        return try {
            raw.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }
}
```

---

## 5. Integration with Orbital `ChatViewModel`

1. **Pre-Turn Prompt Enrichment**:
   Before dispatching a request to Gemini/Claude, `HindsightMemoryEngine.buildPromptContext()` prepends dynamically recalled habits and app quirks.
2. **Post-Turn Reflection**:
   When an automation task completes successfully, `Hindsight` extracts key user parameters (e.g. choice of destination filter or seat preference) and saves them as `MemoryType.HABIT` with zero hardcoding.
