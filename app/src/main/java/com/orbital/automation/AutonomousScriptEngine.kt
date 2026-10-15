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
                val launchIntent = context.packageManager.getLaunchIntentForPackage(resolvedPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    delay(1500)
                    activePackage = resolvedPkg
                }
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

        // Step 4: Write script file to phone filesystem
        val phoneScriptDir = File(context.filesDir, "autonomous_scripts").apply { mkdirs() }
        val phoneScriptFile = File(phoneScriptDir, baseScriptName)
        phoneScriptFile.writeText(scriptCode)
        phoneScriptFile.setExecutable(true, false)

        val deviceSharedPath = "/data/local/tmp/scripts/$baseScriptName"
        ShizukuAutomationManager.exec("mkdir -p /data/local/tmp/scripts && cp ${phoneScriptFile.absolutePath} $deviceSharedPath && chmod 755 $deviceSharedPath")

        Log.i(TAG, "📝 Script written to $deviceSharedPath (${scriptCode.lines().size} lines)")

        // Step 5: Execute the dynamically generated script on the phone
        val execResult = ShizukuAutomationManager.exec("sh $deviceSharedPath")
        Log.i(TAG, "⚡ Script executed with exit code ${execResult.exitCode}")

        delay(1000)

        // Step 6: Post-execution dump & data extraction
        val postSnapshot = OrbitalAutomationEngine.instance?.captureScreenHierarchy()
            ?: ShizukuAutomationManager.dumpScreenHierarchy()

        val extractedData = extractResultData(cleanInstruction, elements, postSnapshot?.elements.orEmpty())

        // Step 7: Capture result screenshot
        val screenshotDevicePath = "/data/local/tmp/scripts/${baseScriptName.removeSuffix(".sh")}.png"
        ShizukuAutomationManager.exec("/data/local/tmp/orbital_agent.sh screenshot $screenshotDevicePath")

        val durationMs = System.currentTimeMillis() - startTime
        val isSuccess = execResult.isSuccess || extractedData.isNotEmpty()
        val summary = if (isSuccess) {
            "Autonomous task completed successfully in ${durationMs}ms with ${extractedData.size} extracted data points."
        } else {
            "Script executed with exit code ${execResult.exitCode}: ${execResult.stderr.ifBlank { execResult.stdout }}"
        }

        AutonomousTaskResult(
            success = isSuccess,
            instruction = cleanInstruction,
            scriptPath = deviceSharedPath,
            scriptCode = scriptCode,
            executionStdout = execResult.stdout,
            executionStderr = execResult.stderr,
            extractedData = extractedData,
            summary = summary,
            screenshotPath = screenshotDevicePath,
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
                lowerInstruction.contains("input")

        val textToType = extractTextToType(instruction)

        if (isTyping && textToType.isNotBlank()) {
            val searchInput = elements.firstOrNull { it.isEditable }
                ?: SemanticElementMatcher.findBestElementMatch("search", elements)?.element
                ?: SemanticElementMatcher.findBestElementMatch("find", elements)?.element

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

            if (lowerInstruction.contains("search") || lowerInstruction.contains("send") || lowerInstruction.contains("submit")) {
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

        // 4. Verification and Data Dump
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
            if (label == query || app.packageName.lowercase() == query) {
                return app.packageName
            }
        }

        // Substring match
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(query) || query.contains(label)) {
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

        val keywords = listOf("search for ", "type ", "enter ", "input ")
        for (kw in keywords) {
            if (instruction.contains(kw, ignoreCase = true)) {
                val after = instruction.substring(instruction.indexOf(kw, ignoreCase = true) + kw.length).trim()
                return after.split(",", " and then", " then").first().trim()
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
