package com.orbital.automation

data class UiIssue(
    val severity: IssueSeverity,
    val element: UIElement,
    val issueType: String,
    val description: String,
    val suggestion: String
)

enum class IssueSeverity {
    HIGH,
    MEDIUM,
    LOW
}

data class UiAuditReport(
    val packageName: String,
    val overallScore: Int, // 0 to 100
    val totalElements: Int,
    val issues: List<UiIssue>
) {
    fun toMarkdownReport(): String {
        return buildString {
            append("## 📱 Mobile Accessibility & Usability Audit Report\n")
            append("• **App Package:** `$packageName`\n")
            append("• **UI Health Score:** **$overallScore / 100**\n")
            append("• **Total Elements Evaluated:** $totalElements\n")
            append("• **Total Issues Identified:** ${issues.size}\n\n")

            if (issues.isEmpty()) {
                append("🎉 **Outstanding!** No UI or touch-target issues detected on this screen.\n")
            } else {
                append("### Detailed Findings:\n")
                issues.forEachIndexed { idx, issue ->
                    val badge = when (issue.severity) {
                        IssueSeverity.HIGH -> "🔴 [HIGH]"
                        IssueSeverity.MEDIUM -> "🟡 [MEDIUM]"
                        IssueSeverity.LOW -> "🟢 [LOW]"
                    }
                    val label = issue.element.text.ifBlank { issue.element.contentDescription ?: issue.element.viewId ?: issue.element.className }
                    append("${idx + 1}. $badge **${issue.issueType}** on `$label`\n")
                    append("   - **Issue:** ${issue.description}\n")
                    append("   - **Recommendation:** ${issue.suggestion}\n\n")
                }
            }
        }
    }
}

object UiAuditorEngine {

    private const val MIN_TOUCH_TARGET_SIZE_PX = 96 // Equivalent to ~48dp on typical xhdpi displays

    fun auditScreen(snapshot: ScreenHierarchySnapshot): UiAuditReport {
        val issues = mutableListOf<UiIssue>()
        var deductions = 0

        snapshot.elements.forEach { el ->
            val label = el.text.ifBlank { el.contentDescription ?: "" }

            // 1. Missing Label on Clickable Elements
            if (el.isClickable && label.isBlank()) {
                issues.add(
                    UiIssue(
                        severity = IssueSeverity.HIGH,
                        element = el,
                        issueType = "Missing Accessible Label",
                        description = "Clickable element ${el.viewId ?: el.className} has no text or content description.",
                        suggestion = "Add an 'android:contentDescription' or descriptive visible text."
                    )
                )
                deductions += 15
            }

            // 2. Small Touch Targets (<48dp standard)
            if (el.isClickable) {
                val width = el.bounds.right - el.bounds.left
                val height = el.bounds.bottom - el.bounds.top
                if (width > 0 && height > 0 && (width < MIN_TOUCH_TARGET_SIZE_PX || height < MIN_TOUCH_TARGET_SIZE_PX)) {
                    issues.add(
                        UiIssue(
                            severity = IssueSeverity.MEDIUM,
                            element = el,
                            issueType = "Small Touch Target",
                            description = "Touch target size (${width}x${height}px) is below minimum recommended 48x48dp.",
                            suggestion = "Increase padding or minWidth/minHeight to at least 48dp."
                        )
                    )
                    deductions += 10
                }
            }

            // 3. Unlabeled Editable Text Fields
            if (el.isEditable && label.isBlank() && el.hintText.isNullOrBlank()) {
                issues.add(
                    UiIssue(
                        severity = IssueSeverity.HIGH,
                        element = el,
                        issueType = "Unlabeled Input Field",
                        description = "Input field ${el.viewId ?: ""} has no visible label or hint text.",
                        suggestion = "Provide a meaningful hint text or floating label."
                    )
                )
                deductions += 15
            }
        }

        val overallScore = (100 - deductions).coerceIn(0, 100)
        return UiAuditReport(
            packageName = snapshot.packageName,
            overallScore = overallScore,
            totalElements = snapshot.elements.size,
            issues = issues
        )
    }
}

// Backwards compatibility aliases
typealias AccessibilityIssue = UiIssue
typealias AccessibilityAuditReport = UiAuditReport
typealias AccessibilityAuditorEngine = UiAuditorEngine
