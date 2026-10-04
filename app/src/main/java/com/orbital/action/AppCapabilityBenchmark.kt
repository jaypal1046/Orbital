package com.orbital.action

import com.orbital.file.UniversalFileEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapabilityBenchmarkSummary(
    val timestamp: String,
    val totalApps: Int,
    val categoryCounts: Map<AppCategory, Int>,
    val apps: List<DynamicAppInfo>
) {
    fun toMarkdownReport(): String {
        val sb = StringBuilder()
        sb.append("# 📱 Orbital On-Device App Capability Matrix & Benchmark\n\n")
        sb.append("- **Timestamp:** $timestamp\n")
        sb.append("- **Total Installed Launchable Apps:** **$totalApps**\n")
        sb.append("- **Indexed Categories:** ${categoryCounts.size}\n\n")

        sb.append("### 📊 Distribution by Semantic Domain:\n\n")
        sb.append("| Category | App Count | Key Capabilities |\n")
        sb.append("| :--- | :---: | :--- |\n")
        categoryCounts.entries.sortedByDescending { it.value }.forEach { (cat, count) ->
            val sampleApps = apps.filter { it.category == cat }.take(3).joinToString(", ") { it.name }
            sb.append("| **${cat.displayName}** | **$count** | $sampleApps |\n")
        }

        sb.append("\n### 📋 Detailed App Capability Index:\n\n")
        sb.append("| App Name | Package Identifier | Category | Action Syntax Hint |\n")
        sb.append("| :--- | :--- | :--- | :--- |\n")
        apps.forEach { app ->
            sb.append("| **${app.name}** | `${app.packageName}` | ${app.category.displayName} | `${app.actionHint}` |\n")
        }

        return sb.toString()
    }
}

class AppCapabilityBenchmark(
    private val capabilityManager: AppCapabilityManager
) {

    /**
     * Runs full capability indexing and exports a benchmark matrix report.
     */
    fun runBenchmark(outputDir: File? = null): CapabilityBenchmarkSummary {
        val apps = capabilityManager.getInstalledApps(forceRefresh = true)
        val categoryCounts = apps.groupBy { it.category }.mapValues { it.value.size }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val summary = CapabilityBenchmarkSummary(
            timestamp = dateFormat.format(Date()),
            totalApps = apps.size,
            categoryCounts = categoryCounts,
            apps = apps
        )

        if (outputDir != null && outputDir.exists() && outputDir.isDirectory) {
            val reportFile = File(outputDir, "orbital_capabilities_matrix.md")
            UniversalFileEngine.writeTextFile(reportFile, summary.toMarkdownReport(), overwrite = true)
        }

        return summary
    }
}
