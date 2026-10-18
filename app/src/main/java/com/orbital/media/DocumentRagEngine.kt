package com.orbital.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object DocumentRagEngine {

    private const val MAX_DIRECT_CHARS = 16000
    private const val CHUNK_SIZE = 1500
    private const val CHUNK_OVERLAP = 200

    data class Chunk(
        val index: Int,
        val text: String,
        var score: Double = 0.0
    )

    /**
     * Builds an optimized, prompt-ready RAG context payload from a document
     * based on the user's specific query/request.
     * Isolated on Dispatchers.Default with parallel chunk scoring across CPU cores.
     */
    suspend fun buildRagPayload(fileName: String, rawText: String, userQuery: String): String = withContext(Dispatchers.Default) {
        val cleanText = rawText.trim()
        if (cleanText.isBlank()) {
            return@withContext "--- [Attached Document: $fileName] ---\n(Empty document or no readable text extracted)"
        }

        // 1. Small / Medium Document: Inject Full Content Directly
        if (cleanText.length <= MAX_DIRECT_CHARS) {
            return@withContext """
                --- [ATTACHED DOCUMENT CONTENT: $fileName] ---
                $cleanText
                
                [SYSTEM DIRECTIVE: The complete text of '$fileName' has been parsed and provided above. Analyze, summarize, or answer the user's prompt directly using this text. Do NOT attempt to launch external apps or capture device screens.]
            """.trimIndent()
        }

        // 2. Large Document: Parallel Chunk & Semantic Retrieval (RAG)
        val chunks = splitIntoChunks(cleanText)
        val queryKeywords = extractKeywords(userQuery)

        // Parallel chunk scoring across available CPU threads
        if (queryKeywords.isNotEmpty()) {
            val batchSize = 10
            chunks.chunked(batchSize).map { batch ->
                async {
                    batch.forEach { chunk ->
                        chunk.score = scoreChunk(chunk.text, queryKeywords)
                    }
                }
            }.awaitAll()
        }

        // Select top chunks sorted by relevance, up to max budget
        val topChunks = if (queryKeywords.isNotEmpty()) {
            chunks.sortedByDescending { it.score }.take(8).sortedBy { it.index }
        } else {
            // If user asked general prompt like "summarize this", take beginning, middle, and end
            val selected = mutableListOf<Chunk>()
            selected.addAll(chunks.take(4))
            if (chunks.size > 8) {
                selected.addAll(chunks.subList(chunks.size / 2 - 1, chunks.size / 2 + 1))
                selected.addAll(chunks.takeLast(2))
            }
            selected.distinctBy { it.index }.sortedBy { it.index }
        }

        val compiledSections = topChunks.joinToString("\n\n") { chunk ->
            "[Section ${chunk.index + 1} / ${chunks.size}]:\n${chunk.text}"
        }

        """
            --- [ATTACHED DOCUMENT RAG CONTEXT: $fileName (${cleanText.length} characters, ${chunks.size} sections)] ---
            Executive Summary / Retrieved Sections based on your request:
            
            $compiledSections
            
            [SYSTEM DIRECTIVE: Relevant passages from '$fileName' have been retrieved and provided above. Answer the user's question directly from these passages. Do NOT launch external apps or capture device screens.]
        """.trimIndent()
    }

    private fun splitIntoChunks(text: String): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var start = 0
        var chunkIndex = 0

        while (start < text.length) {
            val end = (start + CHUNK_SIZE).coerceAtMost(text.length)
            val chunkStr = text.substring(start, end).trim()
            if (chunkStr.isNotBlank()) {
                chunks.add(Chunk(index = chunkIndex++, text = chunkStr))
            }
            if (end >= text.length) break
            start += (CHUNK_SIZE - CHUNK_OVERLAP)
        }

        return chunks
    }

    private fun extractKeywords(query: String): List<String> {
        val stopWords = setOf(
            "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "for", "of", "what", "how",
            "why", "can", "you", "tell", "me", "about", "this", "that", "do", "think", "please", "summarize", "read"
        )
        val words = query.lowercase().split(Regex("[^a-zA-Z0-9]+"))
        return words.filter { it.length > 2 && it !in stopWords }
    }

    private fun scoreChunk(chunkText: String, keywords: List<String>): Double {
        if (keywords.isEmpty()) return 1.0
        val lower = chunkText.lowercase()
        var score = 0.0
        for (kw in keywords) {
            val count = Regex(Regex.escape(kw)).findAll(lower).count()
            if (count > 0) {
                score += count * 1.5 + kw.length * 0.2
            }
        }
        return score
    }
}
