package com.orbital.automation

import android.graphics.Rect
import com.orbital.file.UniversalFileEngine
import com.orbital.foreman.AntiLoopDetector
import com.orbital.foreman.LoopVerdict
import com.orbital.search.SearchCategory
import com.orbital.search.SearchableItem
import com.orbital.search.SemanticDeviceSearchEngine
import com.orbital.security.SecurityVaultEngine
import com.orbital.skills.DeclarativeSkillEngine
import com.orbital.voice.LiveStatusNarrator
import com.orbital.voice.StatusNarrationEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmokeStepResult(
    val testId: String,
    val name: String,
    val isPassed: Boolean,
    val durationMs: Long,
    val message: String
)

data class SmokeTestReport(
    val timestamp: String,
    val totalTests: Int,
    val passedCount: Int,
    val failedCount: Int,
    val steps: List<SmokeStepResult>
) {
    fun toMarkdownReport(): String {
        val sb = StringBuilder()
        sb.append("# Orbital Live On-Device Smoke Test Report\n\n")
        sb.append("- **Timestamp:** $timestamp\n")
        sb.append("- **Summary:** $passedCount / $totalTests Passed (${if (failedCount == 0) "✅ 100% HEALTHY" else "⚠️ $failedCount FAILED"})\n\n")
        sb.append("| Test ID | Subsystem | Status | Duration | Details |\n")
        sb.append("| :--- | :--- | :---: | :---: | :--- |\n")
        for (step in steps) {
            val statusIcon = if (step.isPassed) "✅ Pass" else "❌ Fail"
            sb.append("| **${step.testId}** | ${step.name} | $statusIcon | ${step.durationMs}ms | ${step.message} |\n")
        }
        return sb.toString()
    }
}

class LiveDeviceSmokeRunner(
    private val deepLinkSynthesizer: DeepLinkIntentSynthesizer = DeepLinkIntentSynthesizer(),
    private val searchEngine: SemanticDeviceSearchEngine = SemanticDeviceSearchEngine(),
    private val voiceNarrator: LiveStatusNarrator = LiveStatusNarrator(),
    private val antiLoopDetector: AntiLoopDetector = AntiLoopDetector()
) {

    /**
     * Executes an autonomous sanity check sweep across on-device modules.
     */
    fun runSmokeSuite(reportOutputDir: File? = null): SmokeTestReport {
        val results = mutableListOf<SmokeStepResult>()

        // 1. DeepLink Intent Synthesizer Test
        results.add(runStep("SMOKE-01", "Intent Acceleration") {
            val res = deepLinkSynthesizer.synthesizeIntent("Open Wi-Fi settings")
            if (res.isAccelerated && res.intentAction == "android.settings.WIFI_SETTINGS") {
                "Synthesized 1-hop intent shortcut (savings: ${res.stepSavingsEstimate} steps)"
            } else {
                throw IllegalStateException("Intent synthesis failed")
            }
        })

        // 2. Semantic Device Search Test
        results.add(runStep("SMOKE-02", "Semantic Device Search") {
            searchEngine.setIndex(
                listOf(
                    SearchableItem(
                        id = "calc_app",
                        title = "Calculator",
                        category = SearchCategory.APP,
                        keywords = listOf("math", "sum"),
                        actionPayload = "com.google.android.calculator"
                    )
                )
            )
            val matches = searchEngine.search("math")
            if (matches.isNotEmpty() && matches.first().item.id == "calc_app") {
                "Matched 'math' query to Calculator (${matches.first().matchReason})"
            } else {
                throw IllegalStateException("Search matching failed")
            }
        })

        // 3. Security Vault Sensitive Detection Test
        results.add(runStep("SMOKE-03", "Security Vault Masking") {
            val el = UIElement(
                text = "Enter Password",
                contentDescription = null,
                viewId = "edit_password",
                className = "android.widget.EditText",
                isClickable = true,
                isScrollable = false,
                isEditable = true,
                bounds = Rect(0, 0, 100, 50)
            )
            val snapshot = ScreenHierarchySnapshot(
                packageName = "com.example.login",
                activityTitle = "Login",
                elements = listOf(el)
            )
            val sensitive = SecurityVaultEngine.inspectScreen(snapshot)
            if (sensitive.hasSensitiveFields && sensitive.riskCategories.contains("PASSWORD_ENTRY")) {
                "Successfully detected and flagged password node for screen masking"
            } else {
                throw IllegalStateException("Security vault failed to flag password field")
            }
        })

        // 4. Anti-Loop Deadlock Protection Test
        results.add(runStep("SMOKE-04", "Anti-Loop Heuristic") {
            antiLoopDetector.reset()
            antiLoopDetector.recordStep(1, "CLICK", "Next", "state_1")
            antiLoopDetector.recordStep(2, "CLICK", "Next", "state_1")
            val verdict = antiLoopDetector.recordStep(3, "CLICK", "Next", "state_1")
            if (verdict is LoopVerdict.RepetitiveActionDetected) {
                "Detected repetitive click loop (recovery: ${verdict.suggestedRecovery})"
            } else {
                throw IllegalStateException("Anti-loop detector missed repetitive click state")
            }
        })

        // 5. Voice & HUD Narration Synthesis Test
        results.add(runStep("SMOKE-05", "Live HUD Narration") {
            val cue = voiceNarrator.synthesizeNarration(
                StatusNarrationEvent(title = "Cleared Obstacle: Promo Dialog")
            )
            if (cue == "Dismissing popup dialog") {
                "Synthesized concise voice cue: '$cue'"
            } else {
                throw IllegalStateException("Voice narration synthesis unexpected: $cue")
            }
        })

        // 6. Declarative Skill Parsing Test
        results.add(runStep("SMOKE-06", "Declarative Skill Parser") {
            val raw = """
                ---
                name: Battery Health Checker
                description: Inspects device power level and battery state
                triggers: ["battery", "power"]
                ---
                ### Instructions
                - Check battery percentage
            """.trimIndent()
            val parsed = DeclarativeSkillEngine.parseSkillMarkdown(raw)
            if (parsed != null && parsed.name == "Battery Health Checker" && parsed.triggers.contains("battery")) {
                "Parsed declarative SKILL.md with frontmatter metadata"
            } else {
                throw IllegalStateException("Declarative skill parser failed")
            }
        })

        // 7. Universal File Engine Operations Test
        results.add(runStep("SMOKE-07", "Universal File Engine") {
            val tempFile = File.createTempFile("orbital_smoke_", ".txt")
            try {
                val writeRes = UniversalFileEngine.writeTextFile(tempFile, "Smoke test payload line 1\nSmoke test payload line 2")
                val readRes = UniversalFileEngine.readTextFile(tempFile)
                if (writeRes is com.orbital.file.FileOperationResult.Success && readRes is com.orbital.file.FileOperationResult.Success) {
                    "Universal File Engine read/write verified"
                } else {
                    throw IllegalStateException("File operations failed")
                }
            } finally {
                tempFile.delete()
            }
        })

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val report = SmokeTestReport(
            timestamp = dateFormat.format(Date()),
            totalTests = results.size,
            passedCount = results.count { it.isPassed },
            failedCount = results.count { !it.isPassed },
            steps = results
        )

        if (reportOutputDir != null && reportOutputDir.exists() && reportOutputDir.isDirectory) {
            val reportFile = File(reportOutputDir, "orbital_smoke_report.md")
            UniversalFileEngine.writeTextFile(reportFile, report.toMarkdownReport(), overwrite = true)
        }

        return report
    }

    private inline fun runStep(testId: String, name: String, block: () -> String): SmokeStepResult {
        val start = System.currentTimeMillis()
        return try {
            val msg = block()
            SmokeStepResult(
                testId = testId,
                name = name,
                isPassed = true,
                durationMs = System.currentTimeMillis() - start,
                message = msg
            )
        } catch (e: Exception) {
            SmokeStepResult(
                testId = testId,
                name = name,
                isPassed = false,
                durationMs = System.currentTimeMillis() - start,
                message = e.message ?: "Unknown error"
            )
        }
    }
}
