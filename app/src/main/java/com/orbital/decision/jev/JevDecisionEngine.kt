package com.orbital.decision.jev

/**
 * Universal System 1 Decision Interface.
 * Provides sub-100ms non-autoregressive decisions, choice distributions, and continuous scoring.
 */
interface JevDecisionEngine {

    /**
     * Rapid binary evaluation (Yes/No) with confidence thresholding.
     * E.g. "Is the desired destination button visible on this screen?"
     */
    suspend fun decideBinary(
        context: String,
        question: String,
        confidenceThreshold: Float = 0.70f
    ): BooleanDecision

    /**
     * Rapid multi-choice selection with full probability distribution.
     * E.g. Selecting the best matching UI element from a list of candidate nodes.
     */
    suspend fun <T : Any> decideChoice(
        context: String,
        prompt: String,
        candidates: List<T>,
        keyExtractor: (T) -> String,
        confidenceThreshold: Float = 0.50f
    ): ChoiceDecision<T>

    /**
     * Evaluates a continuous relevance, safety, or risk score between 0.0 and 1.0.
     */
    suspend fun scoreRelevance(
        context: String,
        candidate: String
    ): ScoreDecision
}
