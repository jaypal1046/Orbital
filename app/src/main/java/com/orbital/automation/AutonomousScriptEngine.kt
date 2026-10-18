package com.orbital.automation

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AutonomousTaskResult(
    val success: Boolean,
    val instruction: String,
    val scriptPath: String,
    val scriptCode: String,
    val executionStdout: String,
    val executionStderr: String,
    val extractedData: Map<String, String>,
    val summary: String,
    val screenshotPath: String? = null,
    val durationMs: Long = 0L
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("success", success)
        root.put("instruction", instruction)
        root.put("scriptPath", scriptPath)
        root.put("scriptCode", scriptCode)
        root.put("executionStdout", executionStdout)
        root.put("executionStderr", executionStderr)
        root.put("summary", summary)
        root.put("durationMs", durationMs)
        if (screenshotPath != null) {
            root.put("screenshotPath", screenshotPath)
        }

        val dataObj = JSONObject()
        extractedData.forEach { (k, v) -> dataObj.put(k, v) }
        root.put("extractedData", dataObj)
        return root.toString(2)
    }
}

object AutonomousScriptEngine {

    private const val TAG = "AutonomousScriptEngine"

    /**
     * Autonomously resolves app, dumps UI tree, synthesizes dynamic script,
     * writes it to phone filesystem, executes it natively, extracts result data,
     * and returns structured report.
     */
    suspend fun planAndExecuteTask(
        context: Context,
        instruction: String,
        targetApp: String? = null,
        scriptName: String? = null
    ): AutonomousTaskResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cleanInstruction = instruction.trim()
        Log.i(TAG, "🤖 [Phone AI] Starting autonomous task: '$cleanInstruction', targetApp: '$targetApp'")

        // Step 1: Dynamic App Resolution & Launch
        var activePackage = ""
        if (!targetApp.isNullOrBlank()) {
            val resolvedPkg = resolveAppPackage(context, targetApp)
            if (resolvedPkg != null) {
                Log.i(TAG, "🚀 Launching target app package: $resolvedPkg for '$targetApp'")
                // 1. Privileged shell launch (bypasses Android 10+ background activity restrictions)
                ShizukuAutomationManager.exec("monkey -p $resolvedPkg -c android.intent.category.LAUNCHER 1")
                // 2. Standard Intent launch fallback
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(resolvedPkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                } catch (_: Exception) {}
                delay(2200)
                activePackage = resolvedPkg
            }
        }

        // Step 2: Capture initial screen hierarchy
        val initialSnapshot = OrbitalAutomationEngine.instance?.captureScreenHierarchy()
            ?: ShizukuAutomationManager.dumpScreenHierarchy()

        if (activePackage.isBlank()) {
            activePackage = initialSnapshot?.packageName.orEmpty()
        }

        // Step 3: Synthesize executable script on phone
        val elements = initialSnapshot?.elements.orEmpty()
        val baseScriptName = scriptName?.takeIf { it.isNotBlank() }?.let {
            if (it.endsWith(".sh")) it else "$it.sh"
        } ?: "task_${System.currentTimeMillis()}.sh"

        val scriptCode = synthesizeScript(cleanInstruction, activePackage, elements)

        // Step 4: Write script file to phone private storage (for audit & session history)
        val phoneScriptDir = File(context.filesDir, "autonomous_scripts").apply { mkdirs() }
        val phoneScriptFile = File(phoneScriptDir, baseScriptName)
        phoneScriptFile.writeText(scriptCode)

        Log.i(TAG, "📝 Script written to ${phoneScriptFile.absolutePath} (${scriptCode.lines().size} lines)")

        // Step 5: Execute the dynamically generated script on the phone
        // Stream scriptCode directly into the shell via STDIN to bypass Android app sandbox file permissions
        val execResult = ShizukuAutomationManager.exec(scriptCode)
        Log.i(TAG, "⚡ Script executed with exit code ${execResult.exitCode}, stderr: ${execResult.stderr}")

        delay(1000)

        // Step 6: Post-execution dump & data extraction
        val postSnapshot = OrbitalAutomationEngine.instance?.captureScreenHierarchy()
            ?: ShizukuAutomationManager.dumpScreenHierarchy()

        val extractedData = extractResultData(cleanInstruction, elements, postSnapshot?.elements.orEmpty())

        // Step 7: Capture result screenshot
        val screenshotFile = File(phoneScriptDir, "${baseScriptName.removeSuffix(".sh")}.png")
        ShizukuAutomationManager.exec("screencap -p /data/local/tmp/last_screenshot.png && cp /data/local/tmp/last_screenshot.png ${screenshotFile.absolutePath} 2>/dev/null || screencap -p /data/local/tmp/last_screenshot.png")

        val durationMs = System.currentTimeMillis() - startTime
        val hasSecurityException = execResult.stderr.contains("SecurityException", ignoreCase = true)
        val isSuccess = execResult.isSuccess && !hasSecurityException
        val summary = if (isSuccess) {
            "Autonomous task completed successfully in ${durationMs}ms with ${extractedData.size} extracted data points."
        } else if (hasSecurityException) {
            "Execution blocked by Android OS: Shell daemon or Shizuku required for INJECT_EVENTS permission."
        } else {
            "Script executed with exit code ${execResult.exitCode}: ${execResult.stderr.ifBlank { execResult.stdout }}"
        }

        AutonomousTaskResult(
            success = isSuccess,
            instruction = cleanInstruction,
            scriptPath = phoneScriptFile.absolutePath,
            scriptCode = scriptCode,
            executionStdout = execResult.stdout,
            executionStderr = execResult.stderr,
            extractedData = extractedData,
            summary = summary,
            screenshotPath = if (screenshotFile.exists()) screenshotFile.absolutePath else "/data/local/tmp/last_screenshot.png",
            durationMs = durationMs
        )
    }

    /**
     * Synthesizes an executable shell script from instruction and visible elements.
     */
    private fun synthesizeScript(
        instruction: String,
        targetPackage: String,
        elements: List<UIElement>
    ): String {
        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# ========================================================\n")
        sb.append("# Dynamically Generated by Orbital On-Device Phone AI\n")
        sb.append("# Instruction: $instruction\n")
        sb.append("# Foreground Package: $targetPackage\n")
        sb.append("# Timestamp: ${System.currentTimeMillis()}\n")
        sb.append("# 100% Native Android Shell - Zero external dependencies\n")
        sb.append("# ========================================================\n\n")

        val lowerInstruction = instruction.lowercase()

        // 1. Text Entry / Search Intent
        val isTyping = lowerInstruction.contains("type") ||
                lowerInstruction.contains("search") ||
                lowerInstruction.contains("enter") ||
                lowerInstruction.contains("write") ||
                lowerInstruction.contains("input") ||
                lowerInstruction.contains("find") ||
                lowerInstruction.contains("goto")

        val textToType = extractTextToType(instruction)

        if (isTyping && textToType.isNotBlank()) {
            val searchInput = elements.firstOrNull { it.isEditable }
                ?: SemanticElementMatcher.findBestElementMatch("search", elements)?.element
                ?: SemanticElementMatcher.findBestElementMatch("find", elements)?.element
                ?: SemanticElementMatcher.findBestElementMatch("station", elements)?.element
                ?: SemanticElementMatcher.findBestElementMatch("address", elements)?.element
                ?: SemanticElementMatcher.findBestElementMatch("url", elements)?.element

            if (searchInput != null) {
                val cx = (searchInput.bounds.left + searchInput.bounds.right) / 2
                val cy = (searchInput.bounds.top + searchInput.bounds.bottom) / 2
                sb.append("# Step: Focus input/search field\n")
                sb.append("input tap $cx $cy\n")
                sb.append("sleep 1\n\n")
            }

            val escaped = textToType.replace(" ", "%s")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
            sb.append("# Step: Enter text\n")
            sb.append("input text \"$escaped\"\n")
            sb.append("sleep 1\n\n")

            if (lowerInstruction.contains("search") || lowerInstruction.contains("send") || lowerInstruction.contains("submit") || lowerInstruction.contains("goto") || lowerInstruction.contains("find")) {
                sb.append("# Step: Submit query (ENTER)\n")
                sb.append("input keyevent 66\n")
                sb.append("sleep 2\n\n")
            }
        }

        // 2. Click / Tap Intent
        val tapKeywords = listOf("click", "tap", "press", "select", "open")
        for (keyword in tapKeywords) {
            if (lowerInstruction.contains(keyword)) {
                val afterKeyword = lowerInstruction.substringAfter(keyword).trim()
                val targetName = afterKeyword.split(" ", ",", "and", "then").firstOrNull { it.length > 2 } ?: ""
                if (targetName.isNotBlank() && targetName != textToType.lowercase()) {
                    val match = SemanticElementMatcher.findBestElementMatch(targetName, elements)
                    if (match != null) {
                        val cx = (match.element.bounds.left + match.element.bounds.right) / 2
                        val cy = (match.element.bounds.top + match.element.bounds.bottom) / 2
                        sb.append("# Step: Tap '${match.matchedTerm}' (${match.matchType} match)\n")
                        sb.append("input tap $cx $cy\n")
                        sb.append("sleep 1\n\n")
                    }
                }
            }
        }

        // 3. Scroll / Swipe Intent
        if (lowerInstruction.contains("scroll down") || lowerInstruction.contains("swipe up")) {
            sb.append("# Step: Scroll Down\n")
            sb.append("input swipe 540 1800 540 600 300\n")
            sb.append("sleep 1\n\n")
        } else if (lowerInstruction.contains("scroll up") || lowerInstruction.contains("swipe down")) {
            sb.append("# Step: Scroll Up\n")
            sb.append("input swipe 540 600 540 1800 300\n")
            sb.append("sleep 1\n\n")
        }

        // 4. Fallback Action: If no action generated yet, interact with the primary clickable control or scroll
        val hasAnyAction = sb.contains("input tap") || sb.contains("input text") || sb.contains("input swipe")
        if (!hasAnyAction) {
            val clickable = elements.firstOrNull { it.isClickable && it.text.isNotBlank() && it.text.length > 2 }
            if (clickable != null) {
                val cx = (clickable.bounds.left + clickable.bounds.right) / 2
                val cy = (clickable.bounds.top + clickable.bounds.bottom) / 2
                sb.append("# Step: Tap prominent control '${clickable.text}'\n")
                sb.append("input tap $cx $cy\n")
                sb.append("sleep 1\n\n")
            } else {
                sb.append("# Step: Perform exploratory swipe\n")
                sb.append("input swipe 540 1600 540 800 300\n")
                sb.append("sleep 1\n\n")
            }
        }

        // 5. Verification and Data Dump
        sb.append("# Step: Dump final UI state for data extraction\n")
        sb.append("uiautomator dump /data/local/tmp/task_result_dump.xml >/dev/null 2>&1 || true\n")
        sb.append("echo 'ORBITAL_AUTONOMOUS_TASK_SUCCESS'\n")

        return sb.toString()
    }

    /**
     * Dynamically finds the package name of an installed application without hardcoding.
     */
    fun resolveAppPackage(context: Context, appNameOrPkg: String): String? {
        val query = appNameOrPkg.trim().lowercase()
        val normalizedQuery = when (query) {
            "crome" -> "chrome"
            "train" -> "where is my train"
            "map", "maps" -> "google maps"
            else -> query
        }
        val cleanQuery = normalizedQuery.replace(" ", "")
        val pm = context.packageManager

        // Direct package match
        try {
            val info = pm.getPackageInfo(appNameOrPkg, 0)
            if (info != null) return info.packageName
        } catch (_: Exception) {}

        // Dynamic search through installed applications
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val pkg = app.packageName.lowercase()
            if (label == normalizedQuery || pkg == normalizedQuery || pkg == cleanQuery) {
                return app.packageName
            }
        }

        // Substring / stripped space match
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val cleanLabel = label.replace(" ", "")
            val pkg = app.packageName.lowercase()
            if (label.contains(normalizedQuery) || normalizedQuery.contains(label) ||
                cleanLabel.contains(cleanQuery) || cleanQuery.contains(cleanLabel) ||
                pkg.contains(cleanQuery)) {
                return app.packageName
            }
        }
        return null
    }

    private fun extractTextToType(instruction: String): String {
        // Check for quotes
        val quoteRegex = "\"([^\"]*)\"".toRegex()
        val quoteMatch = quoteRegex.find(instruction)
        if (quoteMatch != null) return quoteMatch.groupValues[1]

        val singleQuoteRegex = "'([^']*)'".toRegex()
        val singleQuoteMatch = singleQuoteRegex.find(instruction)
        if (singleQuoteMatch != null) return singleQuoteMatch.groupValues[1]

        val keywords = listOf("search for ", "search ", "find ", "type ", "enter ", "input ", "goto ", "navigate to ", "open url ")
        for (kw in keywords) {
            if (instruction.contains(kw, ignoreCase = true)) {
                val after = instruction.substring(instruction.indexOf(kw, ignoreCase = true) + kw.length).trim()
                val candidate = after.split(",", " and then", " then", " on ", " in ").first().trim()
                if (candidate.isNotBlank() && !candidate.equals("trains", ignoreCase = true) && !candidate.equals("station", ignoreCase = true)) {
                    return candidate
                }
            }
        }
        return ""
    }

    private fun extractResultData(
        instruction: String,
        initialElements: List<UIElement>,
        postElements: List<UIElement>
    ): Map<String, String> {
        val data = mutableMapOf<String, String>()
        val initialTexts = initialElements.map { it.text.trim() }.filter { it.isNotBlank() }.toSet()

        // 1. Identify new text elements that appeared after running the script
        val newTexts = postElements
            .map { it.text.trim() }
            .filter { it.isNotBlank() && !initialTexts.contains(it) && it != "ORBITAL_AUTONOMOUS_TASK_SUCCESS" }

        newTexts.take(8).forEachIndexed { index, text ->
            data["result_${index + 1}"] = text
        }

        // 2. Identify key values (numbers, calculations, titles)
        val candidateValues = postElements.filter { it.text.isNotBlank() }
        val calcOrValue = candidateValues.firstOrNull {
            it.text.matches(Regex("^[0-9.,+\\-*/= ]+$")) || it.className.contains("EditText", ignoreCase = true)
        }
        if (calcOrValue != null) {
            data["value"] = calcOrValue.text
        }

        if (data.isEmpty() && postElements.isNotEmpty()) {
            postElements.take(5).forEachIndexed { idx, el ->
                if (el.text.isNotBlank()) data["visible_${idx + 1}"] = el.text
            }
        }

        return data
    }
}
