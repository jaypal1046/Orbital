package com.orbital.memory.hindsight

import kotlin.math.abs
import kotlin.math.sqrt

interface TextEmbeddingProvider {
    suspend fun generateEmbedding(text: String): FloatArray
}

/**
 * Fast on-device L2-normalized 64-dimensional hashed n-gram embedding generator.
 * Produces deterministic, zero-latency dense vectors without external neural models or API calls.
 */
class LocalFastEmbeddingProvider(
    private val dimensions: Int = 64
) : TextEmbeddingProvider {

    override suspend fun generateEmbedding(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        val cleaned = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length > 1 }

        if (tokens.isEmpty()) {
            return vector
        }

        // 1. Unigram feature hashing
        for (token in tokens) {
            val h1 = abs(token.hashCode()) % dimensions
            val h2 = abs((token.hashCode() * 31) xor 0x5bd1e995) % dimensions
            vector[h1] += 1.0f
            vector[h2] += 0.5f

            // Character tri-grams for subword robustness
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val tri = token.substring(i, i + 3)
                    val hTri = abs(tri.hashCode()) % dimensions
                    vector[hTri] += 0.3f
                }
            }
        }

        // 2. L2 Normalization (Unit Length)
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0.0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }
}
