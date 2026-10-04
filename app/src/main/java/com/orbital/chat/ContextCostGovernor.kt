package com.orbital.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ModelPricing(
    val modelName: String,
    val promptCostPerMillion: Double,
    val completionCostPerMillion: Double,
    val maxContextTokens: Int
)

data class TurnCostRecord(
    val turnId: String,
    val modelName: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionCostSummary(
    val sessionId: String,
    val totalTurns: Int,
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val totalTokens: Int,
    val totalCostUsd: Double,
    val maxSingleTurnTokens: Int,
    val records: List<TurnCostRecord>
) {
    fun toFormattedHud(): String {
        val df = DecimalFormat("$#,##0.00000")
        val nf = DecimalFormat("#,###")
        return "📊 Tokens: ${nf.format(totalTokens)} (${nf.format(totalPromptTokens)} in / ${nf.format(totalCompletionTokens)} out) | Cost: ${df.format(totalCostUsd)}"
    }

    fun toMarkdownReport(): String {
        val df = DecimalFormat("$#,##0.00000")
        val nf = DecimalFormat("#,###")
        val sb = StringBuilder()
        sb.append("### 💰 Session Token & Cost Ledger\n\n")
        sb.append("- **Session ID:** `$sessionId`\n")
        sb.append("- **Total Turns:** $totalTurns\n")
        sb.append("- **Total Consumption:** **${nf.format(totalTokens)} tokens**\n")
        sb.append("  - Prompt Tokens: ${nf.format(totalPromptTokens)}\n")
        sb.append("  - Completion Tokens: ${nf.format(totalCompletionTokens)}\n")
        sb.append("- **Cumulative Cost:** **${df.format(totalCostUsd)}**\n")
        sb.append("- **Peak Turn Usage:** ${nf.format(maxSingleTurnTokens)} tokens\n\n")

        if (records.isNotEmpty()) {
            sb.append("| Turn | Model | Prompt Tokens | Completion Tokens | Turn Cost |\n")
            sb.append("| :---: | :--- | :---: | :---: | :---: |\n")
            records.forEachIndexed { idx, rec ->
                sb.append("| ${idx + 1} | ${rec.modelName} | ${nf.format(rec.promptTokens)} | ${nf.format(rec.completionTokens)} | ${df.format(rec.costUsd)} |\n")
            }
        }
        return sb.toString()
    }
}

class ContextCostGovernor(
    private val warningThresholdRatio: Double = 0.80
) {
    companion object {
        val PRICING_TABLE = mapOf(
            "gemini-1.5-flash" to ModelPricing("gemini-1.5-flash", 0.075, 0.30, 1_000_000),
            "gemini-1.5-pro" to ModelPricing("gemini-1.5-pro", 1.25, 5.00, 2_000_000),
            "gemini-2.0-flash" to ModelPricing("gemini-2.0-flash", 0.10, 0.40, 1_000_000),
            "claude-3-5-sonnet" to ModelPricing("claude-3-5-sonnet", 3.00, 15.00, 200_000),
            "claude-3-5-haiku" to ModelPricing("claude-3-5-haiku", 0.80, 4.00, 200_000),
            "llama-3.3-70b" to ModelPricing("llama-3.3-70b", 0.59, 0.79, 128_000),
            "gpt-4o" to ModelPricing("gpt-4o", 2.50, 10.00, 128_000),
            "gpt-4o-mini" to ModelPricing("gpt-4o-mini", 0.15, 0.60, 128_000),
            "default" to ModelPricing("default", 0.50, 1.50, 128_000)
        )

        /**
         * Fast character-based token estimator (~3.8 chars per token for structured text/JSON/code).
         */
        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            val words = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val charEstimate = (text.length / 3.8).toInt()
            val wordEstimate = (words.size * 1.3).toInt()
            return maxOf(charEstimate, wordEstimate).coerceAtLeast(1)
        }
    }

    private val records = mutableListOf<TurnCostRecord>()
    private val _currentSummary = MutableStateFlow<SessionCostSummary?>(null)
    val currentSummary: StateFlow<SessionCostSummary?> = _currentSummary.asStateFlow()

    private var activeSessionId: String = "session_default"

    fun startSession(sessionId: String) {
        activeSessionId = sessionId
        records.clear()
        _currentSummary.value = null
    }

    fun recordTurn(
        turnId: String,
        promptText: String,
        completionText: String,
        modelName: String = "gemini-2.0-flash",
        explicitPromptTokens: Int? = null,
        explicitCompletionTokens: Int? = null
    ): TurnCostRecord {
        val promptTokens = explicitPromptTokens ?: estimateTokens(promptText)
        val completionTokens = explicitCompletionTokens ?: estimateTokens(completionText)
        val totalTokens = promptTokens + completionTokens

        val pricing = findPricing(modelName)
        val cost = (promptTokens * (pricing.promptCostPerMillion / 1_000_000.0)) +
                   (completionTokens * (pricing.completionCostPerMillion / 1_000_000.0))

        val record = TurnCostRecord(
            turnId = turnId,
            modelName = pricing.modelName,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            costUsd = cost
        )
        records.add(record)
        updateSummary()
        return record
    }

    fun isNearContextLimit(promptText: String, modelName: String = "default"): Boolean {
        val estimated = estimateTokens(promptText)
        val pricing = findPricing(modelName)
        return estimated >= (pricing.maxContextTokens * warningThresholdRatio)
    }

    fun getSummary(): SessionCostSummary {
        val totalPrompt = records.sumOf { it.promptTokens }
        val totalComp = records.sumOf { it.completionTokens }
        val totalTokens = totalPrompt + totalComp
        val totalCost = records.sumOf { it.costUsd }
        val maxTurn = records.maxOfOrNull { it.totalTokens } ?: 0

        return SessionCostSummary(
            sessionId = activeSessionId,
            totalTurns = records.size,
            totalPromptTokens = totalPrompt,
            totalCompletionTokens = totalComp,
            totalTokens = totalTokens,
            totalCostUsd = totalCost,
            maxSingleTurnTokens = maxTurn,
            records = records.toList()
        )
    }

    private fun updateSummary() {
        _currentSummary.value = getSummary()
    }

    private fun findPricing(modelName: String): ModelPricing {
        val clean = modelName.lowercase().trim()
        return PRICING_TABLE.entries.firstOrNull { clean.contains(it.key) }?.value
            ?: PRICING_TABLE["default"]!!
    }
}
