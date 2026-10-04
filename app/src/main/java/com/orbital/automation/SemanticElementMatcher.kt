package com.orbital.automation

import kotlin.math.max
import kotlin.math.min

data class SemanticMatchResult(
    val element: UIElement,
    val score: Double,
    val matchedTerm: String,
    val matchType: String // EXACT, SYNONYM, FUZZY, PARTIAL
)

object SemanticElementMatcher {

    private val SYNONYM_MAP: Map<String, Set<String>> = mapOf(
        "cart" to setOf("bag", "basket", "trolley", "checkout", "shopping cart", "my cart"),
        "search" to setOf("find", "explore", "query", "lookup", "magnifier", "search here"),
        "send" to setOf("submit", "forward", "post", "dispatch", "reply", "send message"),
        "save" to setOf("done", "apply", "confirm", "ok", "update", "keep", "save changes"),
        "cancel" to setOf("dismiss", "close", "abort", "back", "exit", "not now", "skip", "no"),
        "delete" to setOf("remove", "trash", "bin", "erase", "clear", "discard"),
        "login" to setOf("sign in", "log in", "authenticate", "continue", "next", "sign-in"),
        "profile" to setOf("account", "my profile", "settings", "user", "me", "avatar"),
        "play" to setOf("stream", "listen", "start", "resume", "play music", "play video"),
        "share" to setOf("send to", "forward", "quickshare", "export", "share via")
    )

    fun findBestElementMatch(
        query: String,
        elements: List<UIElement>,
        minScoreThreshold: Double = 0.55
    ): SemanticMatchResult? {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isBlank() || elements.isEmpty()) return null

        var bestMatch: SemanticMatchResult? = null
        var highestScore = 0.0

        for (el in elements) {
            val label = el.text.ifBlank { el.contentDescription ?: el.viewId ?: "" }.lowercase().trim()
            if (label.isBlank()) continue

            // 1. Exact Match (Score = 1.0)
            if (label == cleanQuery) {
                return SemanticMatchResult(el, 1.0, label, "EXACT")
            }

            // 2. Direct Substring Match (Score = 0.85 - 0.95)
            if (label.contains(cleanQuery) || cleanQuery.contains(label)) {
                val score = 0.85 + (min(label.length, cleanQuery.length).toDouble() / max(label.length, cleanQuery.length)) * 0.1
                if (score > highestScore) {
                    highestScore = score
                    bestMatch = SemanticMatchResult(el, score, label, "PARTIAL")
                }
            }

            // 3. Synonym Group Match (Score = 0.80)
            val querySynonyms = SYNONYM_MAP[cleanQuery] ?: SYNONYM_MAP.entries.firstOrNull { it.value.contains(cleanQuery) }?.value
            if (querySynonyms != null && querySynonyms.any { syn -> label.contains(syn) }) {
                val score = 0.80
                if (score > highestScore) {
                    highestScore = score
                    bestMatch = SemanticMatchResult(el, score, label, "SYNONYM")
                }
            }

            // 4. Levenshtein Fuzzy Similarity Match
            val similarity = computeLevenshteinSimilarity(cleanQuery, label)
            if (similarity >= minScoreThreshold && similarity > highestScore) {
                highestScore = similarity
                bestMatch = SemanticMatchResult(el, similarity, label, "FUZZY")
            }
        }

        return if (highestScore >= minScoreThreshold) bestMatch else null
    }

    fun computeLevenshteinSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }

        val distance = dp[len1][len2]
        val maxLen = max(len1, len2)
        return 1.0 - (distance.toDouble() / maxLen)
    }
}
