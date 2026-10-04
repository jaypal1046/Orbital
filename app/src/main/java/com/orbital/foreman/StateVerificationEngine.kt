package com.orbital.foreman

import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import java.security.MessageDigest

/**
 * Criterion for validating post-action screen state.
 */
sealed interface StateVerificationCriterion {
    data class ContainsText(val query: String, val caseSensitive: Boolean = false) : StateVerificationCriterion
    data class ElementPresent(val viewId: String? = null, val textOrDesc: String? = null) : StateVerificationCriterion
    data class ElementAbsent(val textOrDesc: String) : StateVerificationCriterion
    data class PackageMatches(val expectedPackage: String) : StateVerificationCriterion
    data class StateMutated(val preActionHash: String) : StateVerificationCriterion
    data class Custom(val description: String, val predicate: (ScreenHierarchySnapshot) -> Boolean) : StateVerificationCriterion
}

data class VerificationResult(
    val isVerified: Boolean,
    val reason: String,
    val matchedElement: UIElement? = null,
    val stateHash: String? = null
)

data class StateDelta(
    val hasMutated: Boolean,
    val packageChanged: Boolean,
    val addedTexts: List<String>,
    val removedTexts: List<String>,
    val preHash: String,
    val postHash: String
)

class StateVerificationEngine {

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
            return md5(sb.toString())
        }

        private fun md5(input: String): String {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Evaluates a criterion against the latest screen hierarchy snapshot.
     */
    fun verify(
        snapshot: ScreenHierarchySnapshot?,
        criterion: StateVerificationCriterion
    ): VerificationResult {
        if (criterion is StateVerificationCriterion.Custom) {
            val passed = try {
                criterion.predicate(snapshot ?: ScreenHierarchySnapshot("com.orbital.system", "System", emptyList()))
            } catch (e: Exception) {
                false
            }
            return VerificationResult(
                isVerified = passed,
                reason = if (passed) "Custom assertion '${criterion.description}' satisfied" else "Custom assertion '${criterion.description}' failed",
                stateHash = computeStateHash(snapshot)
            )
        }

        if (snapshot == null) {
            return VerificationResult(
                isVerified = false,
                reason = "Screen hierarchy snapshot is null or accessibility service unavailable"
            )
        }

        val currentHash = computeStateHash(snapshot)

        return when (criterion) {
            is StateVerificationCriterion.ContainsText -> {
                val match = snapshot.elements.firstOrNull { el ->
                    val txt = el.text
                    val desc = el.contentDescription.orEmpty()
                    if (criterion.caseSensitive) {
                        txt.contains(criterion.query) || desc.contains(criterion.query)
                    } else {
                        txt.contains(criterion.query, ignoreCase = true) || desc.contains(criterion.query, ignoreCase = true)
                    }
                }
                if (match != null) {
                    VerificationResult(
                        isVerified = true,
                        reason = "Found text '${criterion.query}' on screen",
                        matchedElement = match,
                        stateHash = currentHash
                    )
                } else {
                    VerificationResult(
                        isVerified = false,
                        reason = "Text '${criterion.query}' not found on current screen (${snapshot.packageName})",
                        stateHash = currentHash
                    )
                }
            }

            is StateVerificationCriterion.ElementPresent -> {
                val match = snapshot.elements.firstOrNull { el ->
                    val idMatch = criterion.viewId != null && el.viewId?.equals(criterion.viewId, ignoreCase = true) == true
                    val textMatch = criterion.textOrDesc != null && (
                        el.text.contains(criterion.textOrDesc, ignoreCase = true) ||
                        el.contentDescription?.contains(criterion.textOrDesc, ignoreCase = true) == true
                    )
                    idMatch || textMatch
                }
                if (match != null) {
                    VerificationResult(
                        isVerified = true,
                        reason = "Target element present on screen",
                        matchedElement = match,
                        stateHash = currentHash
                    )
                } else {
                    VerificationResult(
                        isVerified = false,
                        reason = "Target element (id=${criterion.viewId}, text=${criterion.textOrDesc}) not found",
                        stateHash = currentHash
                    )
                }
            }

            is StateVerificationCriterion.ElementAbsent -> {
                val stillPresent = snapshot.elements.any { el ->
                    el.text.contains(criterion.textOrDesc, ignoreCase = true) ||
                    el.contentDescription?.contains(criterion.textOrDesc, ignoreCase = true) == true
                }
                if (!stillPresent) {
                    VerificationResult(
                        isVerified = true,
                        reason = "Element '${criterion.textOrDesc}' is successfully absent",
                        stateHash = currentHash
                    )
                } else {
                    VerificationResult(
                        isVerified = false,
                        reason = "Element '${criterion.textOrDesc}' is still present on screen",
                        stateHash = currentHash
                    )
                }
            }

            is StateVerificationCriterion.PackageMatches -> {
                val matches = snapshot.packageName.equals(criterion.expectedPackage, ignoreCase = true) ||
                              snapshot.packageName.contains(criterion.expectedPackage, ignoreCase = true)
                if (matches) {
                    VerificationResult(
                        isVerified = true,
                        reason = "Foreground package matches '${criterion.expectedPackage}'",
                        stateHash = currentHash
                    )
                } else {
                    VerificationResult(
                        isVerified = false,
                        reason = "Expected package '${criterion.expectedPackage}' but found '${snapshot.packageName}'",
                        stateHash = currentHash
                    )
                }
            }

            is StateVerificationCriterion.StateMutated -> {
                val changed = currentHash != criterion.preActionHash
                if (changed) {
                    VerificationResult(
                        isVerified = true,
                        reason = "Screen state mutated (hash: ${criterion.preActionHash.take(8)} -> ${currentHash.take(8)})",
                        stateHash = currentHash
                    )
                } else {
                    VerificationResult(
                        isVerified = false,
                        reason = "Screen state unchanged after action (hash: ${currentHash.take(8)})",
                        stateHash = currentHash
                    )
                }
            }

            is StateVerificationCriterion.Custom -> {
                val passed = try {
                    criterion.predicate(snapshot)
                } catch (e: Exception) {
                    false
                }
                VerificationResult(
                    isVerified = passed,
                    reason = if (passed) "Custom assertion '${criterion.description}' satisfied" else "Custom assertion '${criterion.description}' failed",
                    stateHash = currentHash
                )
            }
        }
    }

    /**
     * Compares two snapshots to produce a structured delta.
     */
    fun computeDelta(
        pre: ScreenHierarchySnapshot?,
        post: ScreenHierarchySnapshot?
    ): StateDelta {
        val preHash = computeStateHash(pre)
        val postHash = computeStateHash(post)

        val preTexts = pre?.elements?.mapNotNull { it.text.takeIf { t -> t.isNotBlank() } }?.toSet().orEmpty()
        val postTexts = post?.elements?.mapNotNull { it.text.takeIf { t -> t.isNotBlank() } }?.toSet().orEmpty()

        val added = postTexts.minus(preTexts).toList()
        val removed = preTexts.minus(postTexts).toList()
        val pkgChanged = pre?.packageName != post?.packageName

        return StateDelta(
            hasMutated = preHash != postHash,
            packageChanged = pkgChanged,
            addedTexts = added,
            removedTexts = removed,
            preHash = preHash,
            postHash = postHash
        )
    }
}
