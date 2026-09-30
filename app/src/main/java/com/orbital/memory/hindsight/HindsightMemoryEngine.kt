package com.orbital.memory.hindsight

interface HindsightMemoryEngine {

    /**
     * Retain: Stores a new experience, user habit, or automation quirk.
     */
    suspend fun retain(
        bankId: String = "default_user",
        category: MemoryType,
        contextKey: String,
        summary: String,
        detailJson: String = "{}"
    ): Long

    /**
     * Recall: Performs 4-way hybrid retrieval (Vector + Keyword + Temporal + Context).
     */
    suspend fun recall(
        bankId: String = "default_user",
        query: String,
        contextKey: String? = null,
        limit: Int = 5
    ): List<ScoredMemory>

    /**
     * Reflect: Builds a structured context block to inject into the LLM system prompt.
     */
    suspend fun buildPromptContext(
        bankId: String = "default_user",
        query: String,
        contextKey: String? = null
    ): String

    /**
     * Deletes a specific memory by ID.
     */
    suspend fun forget(id: Long)
}
