package com.orbital.automation

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TestedScreen(
    val stateHash: String,
    val controls: List<String>,
    val fields: List<String>,
    val reachedBy: String? = null,
    val leftUntouched: List<String> = emptyList()
)

@Serializable
data class AppTestRecord(
    val packageName: String,
    val appName: String,
    val screens: List<TestedScreen>,
    val blockedReason: String? = null,
    val testedAt: Long = System.currentTimeMillis()
)

fun ScreenHierarchySnapshot.testStateHash(): String =
    (packageName + elements.joinToString { element ->
        val label = if (element.isEditable) element.hintText ?: element.viewId
            else element.text.takeIf { it.isNotBlank() } ?: element.contentDescription ?: element.viewId
        "${element.className}:${element.isClickable}:${element.isEditable}:$label"
    }).hashCode().toString()

class AppTestCache(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "orbital_app_test_cache",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun save(record: AppTestRecord) {
        val previous = prefs.getString(record.packageName, null)?.let {
            runCatching { json.decodeFromString<AppTestRecord>(it) }.getOrNull()
        }
        val merged = record.copy(
            screens = ((previous?.screens.orEmpty() + record.screens)
                .asReversed().distinctBy { it.stateHash }.asReversed()).takeLast(30),
            blockedReason = record.blockedReason ?: previous?.blockedReason?.takeIf { record.screens.isEmpty() }
        )
        prefs.edit().putString(record.packageName, json.encodeToString(merged)).apply()
    }

    fun relevantContext(query: String, foregroundPackage: String?): String {
        val records = prefs.all.values.mapNotNull { raw ->
            (raw as? String)?.let { runCatching { json.decodeFromString<AppTestRecord>(it) }.getOrNull() }
        }
        val relevant = records.filter {
            query.contains(it.appName, ignoreCase = true) ||
                query.contains(it.packageName, ignoreCase = true) ||
                it.packageName == foregroundPackage
        }.ifEmpty { records.sortedByDescending { it.testedAt }.take(1) }
            .sortedByDescending { it.testedAt }.take(3)
        if (relevant.isEmpty()) return ""
        return buildString {
            appendLine("Observed app test history (only screens actually reached):")
            relevant.forEach { record ->
                appendLine("${record.appName} (${record.packageName}): ${record.screens.size} screens observed")
                record.screens.takeLast(12).forEach { screen ->
                    appendLine("- Reached via ${screen.reachedBy ?: "launch"}; controls: ${screen.controls.joinToString().take(250)}; fields: ${screen.fields.joinToString().take(150)}")
                    if (screen.leftUntouched.isNotEmpty()) appendLine("  Left untouched: ${screen.leftUntouched.joinToString().take(250)}")
                }
                record.blockedReason?.let { appendLine("- Stopped: $it") }
            }
        }.take(5000)
    }
}
