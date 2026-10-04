package com.orbital.automation

data class AccessibilityIssue(
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

data class AccessibilityAuditReport(
    val packageName: String,
    val overallScore: Int, // 0 to 100
    val totalElements: Int,
    val issues: List<AccessibilityIssue>
) {
    fun toMarkdownReport(): String {
        return buildString {
            append("## ♿ Mobile Accessibility & Usability Audit Report\n")
            append("• **App Package:** `$packageName`\n")
            append("• **Accessibility Health Score:** **$overallScore / 100**\n")
            append("• **Total Elements Evaluated:** $totalElements\n")
            append("• **Total Issues Identified:** ${issues.size}\n\n")

            if (issues.isEmpty()) {
                append("🎉 **Outstanding!** No accessibility or touch-target issues detected on this screen.\n")
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

object AccessibilityAuditorEngine {

    private const val MIN_TOUCH_TARGET_SIZE_PX = 96 // Equivalent to ~48dp on typical xhdpi displays

    fun auditScreen(snapshot: ScreenHierarchySnapshot): AccessibilityAuditReport {
        val issues = mutableListOf<AccessibilityIssue>()
        var deductions = 0

        snapshot.elements.forEach { el ->
            val label = el.text.ifBlank { el.contentDescription ?: "" }

            // Check 1: Clickable element with missing label/contentDescription
            if (el.isClickable && label.isBlank()) {
                issues.add(
                    AccessibilityIssue(
                        severity = IssueSeverity.HIGH,
                        element = el,
                        issueType = "Missing Accessible Label",
                        description = "Interactive control (${el.className}) has no text or contentDescription.",
                        suggestion = "Add an 'android:contentDescription' attribute or accessible label."
                    )
                )
                deductions += 15
            }

            // Check 2: Clickable element with small touch target
            if (el.isClickable) {
                val width = el.bounds.right - el.bounds.left
                val height = el.bounds.bottom - el.bounds.top
                if ((width in 1 until MIN_TOUCH_TARGET_SIZE_PX) || (height in 1 until MIN_TOUCH_TARGET_SIZE_PX)) {
                    issues.add(
                        AccessibilityIssue(
                            severity = IssueSeverity.MEDIUM,
                            element = el,
                            issueType = "Small Touch Target",
                            description = "Touch bounds are ${width}x${height}px, below the recommended 48dp minimum.",
                            suggestion = "Increase padding or minimum touch dimensions to at least 48x48dp."
                        )
                    )
                    deductions += 10
                }
            }
        }

        val finalScore = (100 - deductions).coerceIn(0, 100)
        return AccessibilityAuditReport(
            packageName = snapshot.packageName,
            overallScore = finalScore,
            totalElements = snapshot.elements.size,
            issues = issues
        )
    }
}
