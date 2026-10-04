package com.orbital.decision

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable

@Serializable
data class DisambiguationOption(
    val id: String,
    val text: String,
    val isRecommended: Boolean = false
)

@Serializable
data class DisambiguationQuestion(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val options: List<DisambiguationOption>,
    val allowCustomInput: Boolean = true,
    val isMultiSelect: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMarkdownCard(): String {
        return buildString {
            append("❓ **Question from Orbital:**\n")
            append("$question\n\n")
            options.forEachIndexed { idx, opt ->
                val prefix = if (opt.isRecommended) "⭐ (Recommended) " else ""
                append("- **[Option ${idx + 1}]** $prefix${opt.text}\n")
            }
            if (allowCustomInput) {
                append("\n_💡 Tap an option or type your custom answer below._")
            }
        }
    }
}

data class DisambiguationAnswer(
    val questionId: String,
    val selectedOptionIds: List<String>,
    val customText: String? = null
)

object InteractiveQuestionEngine {

    private val pendingQuestions = ConcurrentHashMap<String, CompletableDeferred<DisambiguationAnswer>>()
    private val activeQuestionStore = ConcurrentHashMap<String, DisambiguationQuestion>()

    fun createQuestion(
        questionText: String,
        options: List<String>,
        recommendedIndex: Int? = 0,
        isMultiSelect: Boolean = false,
        allowCustomInput: Boolean = true
    ): Pair<DisambiguationQuestion, CompletableDeferred<DisambiguationAnswer>> {
        val questionId = UUID.randomUUID().toString()
        val structuredOptions = options.mapIndexed { idx, text ->
            DisambiguationOption(
                id = "opt_$idx",
                text = text,
                isRecommended = idx == recommendedIndex
            )
        }

        val question = DisambiguationQuestion(
            id = questionId,
            question = questionText,
            options = structuredOptions,
            allowCustomInput = allowCustomInput,
            isMultiSelect = isMultiSelect
        )

        val deferred = CompletableDeferred<DisambiguationAnswer>()
        pendingQuestions[questionId] = deferred
        activeQuestionStore[questionId] = question

        return Pair(question, deferred)
    }

    fun submitAnswer(questionId: String, selectedOptionIds: List<String>, customText: String? = null): Boolean {
        val deferred = pendingQuestions.remove(questionId) ?: return false
        activeQuestionStore.remove(questionId)
        val answer = DisambiguationAnswer(questionId, selectedOptionIds, customText)
        return deferred.complete(answer)
    }

    fun getPendingQuestion(questionId: String): DisambiguationQuestion? {
        return activeQuestionStore[questionId]
    }

    fun getAllPendingQuestions(): List<DisambiguationQuestion> {
        return activeQuestionStore.values.toList()
    }
}
