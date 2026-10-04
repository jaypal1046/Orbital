package com.orbital.automation

import android.graphics.Rect

data class ElementDelta(
    val previousElement: UIElement,
    val currentElement: UIElement,
    val changeDescription: String
)

data class DomDelta(
    val previousPackage: String?,
    val currentPackage: String,
    val addedElements: List<UIElement>,
    val removedElements: List<UIElement>,
    val modifiedElements: List<ElementDelta>,
    val unchangedCount: Int,
    val isAppTransition: Boolean
) {
    val totalChanges: Int get() = addedElements.size + removedElements.size + modifiedElements.size

    fun toDeltaSummary(): String {
        val sb = StringBuilder()
        if (isAppTransition) {
            sb.append("🔄 Foreground App Changed: $previousPackage ➔ $currentPackage\n")
        } else {
            sb.append("📱 Screen DOM Delta ($currentPackage):\n")
        }

        if (addedElements.isNotEmpty()) {
            sb.append("➕ Added Elements (${addedElements.size}):\n")
            addedElements.take(15).forEachIndexed { idx, el ->
                val label = el.text.ifBlank { el.contentDescription ?: el.viewId ?: el.className }
                sb.append("  ${idx + 1}. [${elementTypeName(el)}] \"$label\"\n")
            }
            if (addedElements.size > 15) sb.append("  ...[${addedElements.size - 15} more added]\n")
        }

        if (modifiedElements.isNotEmpty()) {
            sb.append("✏️ Modified Elements (${modifiedElements.size}):\n")
            modifiedElements.take(10).forEachIndexed { idx, delta ->
                sb.append("  ${idx + 1}. ${delta.changeDescription}\n")
            }
        }

        if (removedElements.isNotEmpty()) {
            sb.append("➖ Removed Elements (${removedElements.size}):\n")
            removedElements.take(10).forEachIndexed { idx, el ->
                val label = el.text.ifBlank { el.contentDescription ?: el.viewId ?: "" }
                if (label.isNotBlank()) {
                    sb.append("  ${idx + 1}. \"$label\"\n")
                }
            }
        }

        sb.append("📊 Summary: ${addedElements.size} added, ${modifiedElements.size} modified, ${removedElements.size} removed, $unchangedCount unchanged.")
        return sb.toString()
    }

    private fun elementTypeName(el: UIElement): String = when {
        el.isEditable -> "Input"
        el.isClickable -> "Button"
        else -> "Text"
    }
}

class DeltaDomEngine {

    private var lastSnapshot: ScreenHierarchySnapshot? = null

    fun reset() {
        lastSnapshot = null
    }

    fun computeDelta(currentSnapshot: ScreenHierarchySnapshot): DomDelta {
        val prev = lastSnapshot
        lastSnapshot = currentSnapshot

        if (prev == null) {
            return DomDelta(
                previousPackage = null,
                currentPackage = currentSnapshot.packageName,
                addedElements = currentSnapshot.elements,
                removedElements = emptyList(),
                modifiedElements = emptyList(),
                unchangedCount = 0,
                isAppTransition = false
            )
        }

        val isAppTransition = prev.packageName != currentSnapshot.packageName
        val prevElements = prev.elements
        val currElements = currentSnapshot.elements

        val matchedPrevIndices = mutableSetOf<Int>()
        val matchedCurrIndices = mutableSetOf<Int>()
        val modified = mutableListOf<ElementDelta>()

        // 1. Identify exact matches (viewId or text+bounds)
        currElements.forEachIndexed { currIdx, currEl ->
            val prevIdx = prevElements.indexOfFirst { prevEl ->
                !matchedPrevIndices.contains(prevElements.indexOf(prevEl)) &&
                isSameElement(prevEl, currEl)
            }
            if (prevIdx != -1) {
                matchedPrevIndices.add(prevIdx)
                matchedCurrIndices.add(currIdx)
                val prevEl = prevElements[prevIdx]
                if (prevEl.text != currEl.text || prevEl.isClickable != currEl.isClickable || prevEl.isEditable != currEl.isEditable) {
                    val desc = "Element [${currEl.viewId ?: currEl.className}] updated text: '${prevEl.text}' ➔ '${currEl.text}'"
                    modified.add(ElementDelta(prevEl, currEl, desc))
                }
            }
        }

        // 2. Identify additions and removals
        val added = currElements.filterIndexed { idx, _ -> !matchedCurrIndices.contains(idx) }
        val removed = prevElements.filterIndexed { idx, _ -> !matchedPrevIndices.contains(idx) }
        val unchanged = matchedCurrIndices.size - modified.size

        return DomDelta(
            previousPackage = prev.packageName,
            currentPackage = currentSnapshot.packageName,
            addedElements = added,
            removedElements = removed,
            modifiedElements = modified,
            unchangedCount = unchanged.coerceAtLeast(0),
            isAppTransition = isAppTransition
        )
    }

    private fun isSameElement(a: UIElement, b: UIElement): Boolean {
        if (!a.viewId.isNullOrBlank() && !b.viewId.isNullOrBlank() && a.viewId == b.viewId) {
            return true
        }
        return a.className == b.className && a.bounds == b.bounds
    }
}
