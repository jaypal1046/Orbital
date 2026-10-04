package com.orbital.search

import com.orbital.automation.SemanticElementMatcher

enum class SearchCategory {
    APP,
    FILE,
    SETTING,
    ACTION,
    SHORTCUT
}

data class SearchableItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val category: SearchCategory,
    val keywords: List<String> = emptyList(),
    val actionPayload: String,
    val iconHint: String = "🔍"
)

data class ScoredSearchResult(
    val item: SearchableItem,
    val relevanceScore: Double,
    val matchReason: String
)

class SemanticDeviceSearchEngine(
    private val minimumRelevanceThreshold: Double = 0.35
) {
    private val indexedItems = mutableListOf<SearchableItem>()

    fun setIndex(items: List<SearchableItem>) {
        indexedItems.clear()
        indexedItems.addAll(items)
    }

    fun registerItem(item: SearchableItem) {
        indexedItems.removeAll { it.id == item.id }
        indexedItems.add(item)
    }

    fun clearIndex() {
        indexedItems.clear()
    }

    /**
     * Executes natural language semantic search across indexed device resources.
     */
    fun search(query: String, maxResults: Int = 10, categoryFilter: SearchCategory? = null): List<ScoredSearchResult> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val candidates = if (categoryFilter != null) {
            indexedItems.filter { it.category == categoryFilter }
        } else {
            indexedItems
        }

        val scored = candidates.mapNotNull { item ->
            val score = computeRelevance(trimmed, item)
            if (score.relevanceScore >= minimumRelevanceThreshold) score else null
        }

        return scored.sortedByDescending { it.relevanceScore }.take(maxResults)
    }

    private fun computeRelevance(query: String, item: SearchableItem): ScoredSearchResult {
        val titleLower = item.title.lowercase()
        val subtitleLower = item.subtitle?.lowercase().orEmpty()

        // 1. Exact title match
        if (titleLower == query) {
            return ScoredSearchResult(item, 1.0, "Exact title match")
        }

        // 2. Prefix match
        if (titleLower.startsWith(query)) {
            val score = 0.9 + (query.length.toDouble() / titleLower.length) * 0.08
            return ScoredSearchResult(item, score, "Title prefix match")
        }

        // 3. Substring match
        if (titleLower.contains(query)) {
            val score = 0.8 + (query.length.toDouble() / titleLower.length) * 0.05
            return ScoredSearchResult(item, score, "Title contains query")
        }

        // 4. Keyword exact / prefix match
        val matchedKeyword = item.keywords.firstOrNull { kw ->
            val kwLower = kw.lowercase()
            kwLower == query || kwLower.startsWith(query) || kwLower.contains(query)
        }
        if (matchedKeyword != null) {
            return ScoredSearchResult(item, 0.75, "Keyword match: '$matchedKeyword'")
        }

        // 5. Subtitle match
        if (subtitleLower.contains(query)) {
            return ScoredSearchResult(item, 0.65, "Subtitle contains query")
        }

        // 6. Fuzzy string similarity via Levenshtein distance
        val titleSim = SemanticElementMatcher.computeLevenshteinSimilarity(query, titleLower)
        if (titleSim >= minimumRelevanceThreshold) {
            return ScoredSearchResult(item, titleSim * 0.85, "Fuzzy title similarity (${(titleSim * 100).toInt()}%)")
        }

        // 7. Token overlap matching
        val queryTokens = query.split(Regex("\\s+")).filter { it.length > 2 }
        val titleTokens = titleLower.split(Regex("\\s+"))
        if (queryTokens.isNotEmpty()) {
            val matchingTokens = queryTokens.count { qTok -> titleTokens.any { tTok -> tTok.contains(qTok) } }
            val overlapScore = matchingTokens.toDouble() / queryTokens.size
            if (overlapScore > 0.4) {
                return ScoredSearchResult(item, overlapScore * 0.6, "Token overlap match")
            }
        }

        return ScoredSearchResult(item, 0.0, "No match")
    }
}
