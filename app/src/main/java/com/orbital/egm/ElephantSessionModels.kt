package com.orbital.egm

import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement

/**
 * Invariants and assertions checked statelessly by the Goldfish critic.
 */
sealed interface GoldfishAssertion {
    data class TextPresent(val targetText: String, val caseSensitive: Boolean = false) : GoldfishAssertion
    data class ElementPresent(val viewId: String? = null, val descriptionOrText: String? = null) : GoldfishAssertion
    data class PackageActive(val expectedPackage: String) : GoldfishAssertion
    data class ScreenMutated(val preStateHash: String) : GoldfishAssertion
    data class CustomPredicate(val label: String, val predicate: (ScreenHierarchySnapshot) -> Boolean) : GoldfishAssertion
}

/**
 * Zero-bias verdict rendered by the Goldfish critic after inspecting live UI state.
 */
sealed interface GoldfishVerdict {
    val isVerified: Boolean
    val explanation: String

    data class Verified(
        override val explanation: String,
        val matchedElement: UIElement? = null,
        val stateHash: String
    ) : GoldfishVerdict {
        override val isVerified: Boolean = true
    }

    data class HallucinatedSuccess(
        override val explanation: String,
        val expectedState: String,
        val observedStateHash: String
    ) : GoldfishVerdict {
        override val isVerified: Boolean = false
    }

    data class ObstacleDetected(
        override val explanation: String,
        val obstacleType: String,
        val dismissalHint: String?
    ) : GoldfishVerdict {
        override val isVerified: Boolean = false
    }

    data class StateStalled(
        override val explanation: String,
        val stateHash: String
    ) : GoldfishVerdict {
        override val isVerified: Boolean = false
    }
}

/**
 * Represents an autonomous execution step coordinated between the Elephant and the Goldfish.
 */
data class EgmStep(
    val stepId: String,
    val stepName: String,
    val expectedOutcome: String,
    val assertion: GoldfishAssertion,
    val maxRetries: Int = 2
)

/**
 * Deep-context session state maintained by the Elephant orchestrator.
 */
data class ElephantSessionState(
    val sessionId: String,
    val goalDescription: String,
    val completedSteps: List<EgmStep> = emptyList(),
    val activeStep: EgmStep? = null,
    val stateHistoryHashes: List<String> = emptyList(),
    val isHalted: Boolean = false,
    val failureReason: String? = null
)
