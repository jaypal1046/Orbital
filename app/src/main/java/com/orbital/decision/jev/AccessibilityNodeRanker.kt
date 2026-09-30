package com.orbital.decision.jev

import com.orbital.automation.UIElement

data class ScoredCandidateElement(
    val element: UIElement,
    val score: Float,
    val displayLabel: String,
    val isTopMatch: Boolean
)

/**
 * Ranks live UI elements dynamically using the JEV Decision Engine.
 * Operates purely on live accessibility hierarchies with zero hardcoding.
 */
class AccessibilityNodeRanker(
    private val decisionEngine: JevDecisionEngine
) {

    suspend fun rankElementsForGoal(
        goalDescription: String,
        screenContext: String,
        elements: List<UIElement>
    ): List<ScoredCandidateElement> {
        if (elements.isEmpty()) return emptyList()

        // 1. Extract dynamic readable labels from UI elements
        val labeledElements = elements.map { el ->
            val label = when {
                el.text.isNotBlank() -> el.text
                !el.contentDescription.isNullOrBlank() -> el.contentDescription
                !el.viewId.isNullOrBlank() -> el.viewId
                else -> el.className.substringAfterLast('.')
            }
            Pair(el, label)
        }

        // 2. Perform fast decision choice
        val choice = decisionEngine.decideChoice(
            context = "Active Screen Context: $screenContext\nUser Goal: $goalDescription",
            prompt = "Identify the UI element that directly executes the user goal",
            candidates = labeledElements,
            keyExtractor = { it.second }
        )

        val topMatchedElement = choice.value.first

        return labeledElements.map { (element, label) ->
            val prob = choice.distribution[Pair(element, label)] ?: if (element == topMatchedElement) choice.confidence else 0.0f
            ScoredCandidateElement(
                element = element,
                score = prob,
                displayLabel = label,
                isTopMatch = (element == topMatchedElement)
            )
        }.sortedByDescending { it.score }
    }
}
