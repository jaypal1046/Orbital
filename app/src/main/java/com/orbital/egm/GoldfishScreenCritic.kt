package com.orbital.egm

import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import java.security.MessageDigest

/**
 * The Goldfish Critic: A stateless, zero-context evaluator that inspects live UI snapshots
 * without conversational priming or optimism.
 *
 * It prevents "Hallucinated Success" where an action was dispatched but the target UI state
 * was never actually reached.
 */
class GoldfishScreenCritic {

    companion object {
        fun computeStateHash(snapshot: ScreenHierarchySnapshot?): String {
            if (snapshot == null) return "empty"
            val sb = StringBuilder()
            sb.append(snapshot.packageName).append("|")
            sb.append(snapshot.activityTitle.orEmpty()).append("|")
            snapshot.elements.forEach { el ->
                sb.append(el.viewId.orEmpty()).append(":")
                sb.append(el.text).append(":")
                sb.append(el.contentDescription.orEmpty()).append(":")
                sb.append(el.isClickable).append(";")
            }
            val bytes = MessageDigest.getInstance("MD5").digest(sb.toString().toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Statelessly evaluates whether the current live snapshot satisfies the assertion.
     *
     * @param snapshot The fresh UI hierarchy snapshot.
     * @param assertion The expected condition to verify.
     * @param preActionHash Optional pre-action state hash to check for stalled/unchanged screens.
     */
    fun evaluate(
        snapshot: ScreenHierarchySnapshot?,
        assertion: GoldfishAssertion,
        preActionHash: String? = null
    ): GoldfishVerdict {
        if (snapshot == null) {
            return GoldfishVerdict.HallucinatedSuccess(
                explanation = "Screen snapshot is null or unavailable.",
                expectedState = assertion.toString(),
                observedStateHash = "empty"
            )
        }

        val currentHash = computeStateHash(snapshot)

        // Check if screen remained stalled when a mutation was required
        if (preActionHash != null && preActionHash != "empty" && preActionHash == currentHash && assertion !is GoldfishAssertion.ScreenMutated) {
            // Screen didn't change at all
            val matches = evaluateAssertionInternally(snapshot, assertion)
            return if (matches.matchedElement != null || matches.passed) {
                // Already satisfied
                GoldfishVerdict.Verified(
                    explanation = "Condition met (screen was already in target state).",
                    matchedElement = matches.matchedElement,
                    stateHash = currentHash
                )
            } else {
                GoldfishVerdict.StateStalled(
                    explanation = "Screen state hash unchanged ($currentHash). Action had no effect on UI.",
                    stateHash = currentHash
                )
            }
        }

        val matchResult = evaluateAssertionInternally(snapshot, assertion)
        return if (matchResult.passed) {
            GoldfishVerdict.Verified(
                explanation = matchResult.reason,
                matchedElement = matchResult.matchedElement,
                stateHash = currentHash
            )
        } else {
            GoldfishVerdict.HallucinatedSuccess(
                explanation = matchResult.reason,
                expectedState = assertion.toString(),
                observedStateHash = currentHash
            )
        }
    }

    private data class InternalMatchResult(
        val passed: Boolean,
        val reason: String,
        val matchedElement: UIElement? = null
    )

    private fun evaluateAssertionInternally(
        snapshot: ScreenHierarchySnapshot,
        assertion: GoldfishAssertion
    ): InternalMatchResult {
        return when (assertion) {
            is GoldfishAssertion.TextPresent -> {
                val cleanQuery = assertion.targetText.trim()
                val match = snapshot.elements.firstOrNull { el ->
                    if (assertion.caseSensitive) {
                        el.text.contains(cleanQuery) || el.contentDescription?.contains(cleanQuery) == true
                    } else {
                        el.text.contains(cleanQuery, ignoreCase = true) ||
                            el.contentDescription?.contains(cleanQuery, ignoreCase = true) == true
                    }
                }
                if (match != null) {
                    InternalMatchResult(true, "Found text '$cleanQuery' in element: ${match.viewId ?: match.text}", match)
                } else {
                    InternalMatchResult(false, "Text '$cleanQuery' not found in current UI snapshot (${snapshot.elements.size} elements).")
                }
            }

            is GoldfishAssertion.ElementPresent -> {
                val match = snapshot.elements.firstOrNull { el ->
                    val idMatch = assertion.viewId == null || el.viewId?.contains(assertion.viewId, ignoreCase = true) == true
                    val textMatch = assertion.descriptionOrText == null ||
                        el.text.contains(assertion.descriptionOrText, ignoreCase = true) ||
                        el.contentDescription?.contains(assertion.descriptionOrText, ignoreCase = true) == true
                    idMatch && textMatch
                }
                if (match != null) {
                    InternalMatchResult(true, "Element matched criteria: ${match.viewId ?: match.text}", match)
                } else {
                    InternalMatchResult(false, "Element matching criteria (id=${assertion.viewId}, text=${assertion.descriptionOrText}) not present.")
                }
            }

            is GoldfishAssertion.PackageActive -> {
                val isMatch = snapshot.packageName.equals(assertion.expectedPackage.trim(), ignoreCase = true)
                if (isMatch) {
                    InternalMatchResult(true, "Active package matches '${assertion.expectedPackage}'.")
                } else {
                    InternalMatchResult(false, "Expected package '${assertion.expectedPackage}' but found '${snapshot.packageName}'.")
                }
            }

            is GoldfishAssertion.ScreenMutated -> {
                val currentHash = computeStateHash(snapshot)
                val mutated = currentHash != assertion.preStateHash
                if (mutated) {
                    InternalMatchResult(true, "Screen state mutated from ${assertion.preStateHash} to $currentHash.")
                } else {
                    InternalMatchResult(false, "Screen did not mutate. Hash remains ${assertion.preStateHash}.")
                }
            }

            is GoldfishAssertion.CustomPredicate -> {
                val passed = try {
                    assertion.predicate(snapshot)
                } catch (e: Exception) {
                    false
                }
                if (passed) {
                    InternalMatchResult(true, "Custom assertion '${assertion.label}' satisfied.")
                } else {
                    InternalMatchResult(false, "Custom assertion '${assertion.label}' failed.")
                }
            }
        }
    }
}
