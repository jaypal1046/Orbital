package com.orbital.memory.hindsight

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sqrt

class HindsightMemoryEngineImpl(
    private val dao: HindsightDao,
    private val embeddingProvider: TextEmbeddingProvider = LocalFastEmbeddingProvider()
) : HindsightMemoryEngine {

    companion object {
        private const val WEIGHT_VECTOR = 0.40f
        private const val WEIGHT_KEYWORD = 0.30f
        private const val WEIGHT_TEMPORAL = 0.15f
        private const val WEIGHT_CONFIDENCE = 0.15f
        private const val RECALL_THRESHOLD = 0.30f
    }

    override suspend fun retain(
        bankId: String,
        category: MemoryType,
        contextKey: String,
        summary: String,
        detailJson: String
    ): Long = withContext(Dispatchers.IO) {
        val embedding = embeddingProvider.generateEmbedding(summary)
        val rawEmbeddingStr = embedding.joinToString(",")

        val entity = MemoryEntity(
            bankId = bankId,
            category = category,
            contextKey = contextKey.lowercase().trim(),
            summary = summary.trim(),
            detailJson = detailJson,
            rawEmbedding = rawEmbeddingStr,
            confidence = 1.0f,
            reinforcementCount = 1,
            createdTimestamp = System.currentTimeMillis(),
            lastAccessedTimestamp = System.currentTimeMillis()
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
        if (allMemories.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val queryTokens = tokenize(query)
        val normalizedContext = contextKey?.lowercase()?.trim()

        val scored = allMemories.map { memory ->
            // 1. Vector Cosine Similarity
            val memEmbedding = parseEmbedding(memory.rawEmbedding)
            val vectorSim = if (memEmbedding != null) cosineSimilarity(queryEmbedding, memEmbedding) else 0f

            // 2. Keyword BM25 / Jaccard similarity
            val memTokens = tokenize(memory.summary)
            val keywordSim = computeTokenOverlap(queryTokens, memTokens)

            // 3. Temporal Recency (exponential decay: half-life ~ 7 days)
            val hoursElapsed = ((now - memory.lastAccessedTimestamp) / (1000f * 60f * 60f)).coerceAtLeast(0f)
            val temporalWeight = exp(-0.004f * hoursElapsed) // e^(-lambda * t)

            // 4. Context Bonus & Confidence
            val contextBonus = if (normalizedContext != null && memory.contextKey == normalizedContext) 0.25f else 0.0f
            val baseScore = (WEIGHT_VECTOR * vectorSim) +
                    (WEIGHT_KEYWORD * keywordSim) +
                    (WEIGHT_TEMPORAL * temporalWeight) +
                    (WEIGHT_CONFIDENCE * memory.confidence) +
                    contextBonus

            val reinforcementBonus = (memory.reinforcementCount - 1) * 0.05f
            val finalScore = (baseScore + reinforcementBonus).coerceIn(0.0f, 1.0f)

            ScoredMemory(
                memory = memory,
                score = finalScore,
                vectorSimilarity = vectorSim
            )
        }

        val topResults = scored
            .filter { it.score >= RECALL_THRESHOLD }
            .sortedByDescending { it.score }
            .take(limit)

        // Reinforce top accessed memories
        topResults.forEach { dao.reinforceMemory(it.memory.id) }

        topResults
    }

    override suspend fun buildPromptContext(
        bankId: String,
        query: String,
        contextKey: String?
    ): String = withContext(Dispatchers.IO) {
        val recalled = recall(bankId, query, contextKey, limit = 4)
        if (recalled.isEmpty()) return@withContext ""

        buildString {
            appendLine("### RECALLED LONG-TERM MEMORIES (Learned Preferences & Patterns):")
            recalled.forEach { item ->
                appendLine("- [${item.memory.category.name}] ${item.memory.summary}")
            }
            appendLine()
        }
    }

    override suspend fun forget(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteMemory(id)
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
        return if (denom > 0f) (dot / denom).coerceIn(-1.0f, 1.0f) else 0f
    }

    private fun computeTokenOverlap(queryTokens: Set<String>, targetTokens: Set<String>): Float {
        if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0f
        val intersection = queryTokens.intersect(targetTokens).size
        val union = (queryTokens + targetTokens).size
        return (intersection.toFloat() / union.toFloat()).coerceIn(0f, 1f)
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()
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
