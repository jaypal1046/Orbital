package com.orbital.decision.jev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed on-device fallback evaluator.
 * Employs calibrated n-gram token overlap and logit soft-matching to deliver <15ms deterministic decisions offline.
 */
class JevLocalFallbackEngine : JevDecisionEngine {

    companion object {
        private const val MODEL_ID = "orbital-local-system1-v1"
    }

    override suspend fun decideBinary(
        context: String,
        question: String,
        confidenceThreshold: Float
    ): BooleanDecision = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val score = computeSemanticRelevance(context, question)
        val latency = max(1L, System.currentTimeMillis() - start)

        val isAffirmative = score >= confidenceThreshold
        BooleanDecision(
            value = isAffirmative,
            confidence = score,
            metadata = DecisionMetadata(
                latencyMs = latency,
                modelIdentifier = MODEL_ID,
                isLocalExecution = true
            ),
            reasoningHint = "Local on-device logit evaluation (Score: ${(score * 100).toInt()}%)"
        )
    }

    override suspend fun <T : Any> decideChoice(
        context: String,
        prompt: String,
        candidates: List<T>,
        keyExtractor: (T) -> String,
        confidenceThreshold: Float
    ): ChoiceDecision<T> = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        require(candidates.isNotEmpty()) { "Candidate list must not be empty" }

        if (candidates.size == 1) {
            val single = candidates.first()
            return@withContext ChoiceDecision(
                value = single,
                selectedIndex = 0,
                distribution = mapOf(single to 1.0f),
                confidence = 1.0f,
                metadata = DecisionMetadata(
                    latencyMs = max(1L, System.currentTimeMillis() - start),
                    modelIdentifier = MODEL_ID,
                    isLocalExecution = true
                )
            )
        }

        val promptTokens = tokenize(prompt)
        val contextTokens = tokenize(context)
        val queryTokens = (promptTokens + contextTokens).toSet()

        // 1. Calculate raw relevance logits for each candidate
        val rawScores = candidates.map { candidate ->
            val label = keyExtractor(candidate)
            computeCandidateLogit(queryTokens, label)
        }

        // 2. Compute Softmax distribution with temperature calibration T = 0.5
        val temperature = 0.5f
        val maxLogit = rawScores.maxOrNull() ?: 0.0f
        val expScores = rawScores.map { exp((it - maxLogit) / temperature) }
        val sumExp = expScores.sum().coerceAtLeast(1e-6f)
        val probabilities = expScores.map { it / sumExp }

        var bestIndex = 0
        var highestProb = -1.0f
        for (i in probabilities.indices) {
            if (probabilities[i] > highestProb) {
                highestProb = probabilities[i]
                bestIndex = i
            }
        }

        val distribution = candidates.mapIndexed { idx, item ->
            item to probabilities[idx]
        }.toMap()

        val latency = max(1L, System.currentTimeMillis() - start)
        ChoiceDecision(
            value = candidates[bestIndex],
            selectedIndex = bestIndex,
            distribution = distribution,
            confidence = highestProb,
            metadata = DecisionMetadata(
                latencyMs = latency,
                modelIdentifier = MODEL_ID,
                isLocalExecution = true
            ),
            reasoningHint = "Local decision selected option '$bestIndex' with confidence ${(highestProb * 100).toInt()}%"
        )
    }

    override suspend fun scoreRelevance(
        context: String,
        candidate: String
    ): ScoreDecision = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val score = computeSemanticRelevance(context, candidate)
        val latency = max(1L, System.currentTimeMillis() - start)

        ScoreDecision(
            value = score,
            confidence = score,
            metadata = DecisionMetadata(
                latencyMs = latency,
                modelIdentifier = MODEL_ID,
                isLocalExecution = true
            )
        )
    }

    private fun computeCandidateLogit(queryTokens: Set<String>, label: String): Float {
        val candidateTokens = tokenize(label)
        if (candidateTokens.isEmpty() || queryTokens.isEmpty()) return 0.05f

        var score = 0.0f
        for (qToken in queryTokens) {
            val qRoot = qToken.trimEnd('s', 'd', 'g', 'e')
            for (cToken in candidateTokens) {
                val cRoot = cToken.trimEnd('s', 'd', 'g', 'e')
                when {
                    qToken == cToken -> score += 4.0f // Exact match
                    qRoot.length >= 3 && cRoot.length >= 3 && (qRoot == cRoot || qToken.startsWith(cRoot) || cToken.startsWith(qRoot)) -> score += 2.5f // Root / prefix match
                    qToken.contains(cToken) || cToken.contains(qToken) -> score += 1.5f // Substring match
                }
            }
        }

        val normalization = (queryTokens.size + candidateTokens.size).toFloat()
        return (score / normalization).coerceIn(0.01f, 10.0f)
    }

    private fun computeSemanticRelevance(source: String, target: String): Float {
        val sTokens = tokenize(source)
        val tTokens = tokenize(target)
        if (sTokens.isEmpty() || tTokens.isEmpty()) return 0.0f

        val intersection = sTokens.intersect(tTokens)
        val overlap = (2.0f * intersection.size) / (sTokens.size + tTokens.size)
        return min(1.0f, max(0.0f, overlap * 1.5f))
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()
    }
}
