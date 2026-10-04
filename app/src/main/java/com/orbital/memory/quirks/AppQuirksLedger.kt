package com.orbital.memory.quirks

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AppQuirk(
    val packageName: String,
    val failedAction: String,
    val workaroundDescription: String,
    val confidence: Float = 1.0f,
    val occurrenceCount: Int = 1,
    val lastObservedTimestamp: Long = System.currentTimeMillis()
)

@Singleton
class AppQuirksLedger @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "orbital_app_quirks_ledger"
        private const val KEY_QUIRKS_JSON = "quirks_data"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records a learned app quirk / UI workaround for a package.
     */
    suspend fun recordQuirk(
        packageName: String,
        failedAction: String,
        workaroundDescription: String
    ): Unit = withContext(Dispatchers.IO) {
        val existing = loadAllQuirks().toMutableList()
        val index = existing.indexOfFirst {
            it.packageName.equals(packageName, ignoreCase = true) &&
            it.failedAction.equals(failedAction, ignoreCase = true)
        }

        if (index >= 0) {
            val old = existing[index]
            existing[index] = old.copy(
                workaroundDescription = workaroundDescription,
                occurrenceCount = old.occurrenceCount + 1,
                lastObservedTimestamp = System.currentTimeMillis()
            )
        } else {
            existing.add(
                AppQuirk(
                    packageName = packageName,
                    failedAction = failedAction,
                    workaroundDescription = workaroundDescription,
                    occurrenceCount = 1,
                    lastObservedTimestamp = System.currentTimeMillis()
                )
            )
        }

        saveAllQuirks(existing)
    }

    /**
     * Returns all registered quirks for a specific foreground package.
     */
    suspend fun getQuirksForPackage(packageName: String): List<AppQuirk> = withContext(Dispatchers.IO) {
        loadAllQuirks().filter { it.packageName.equals(packageName, ignoreCase = true) }
    }

    /**
     * Finds a specific workaround for an action in a package if known.
     */
    suspend fun getWorkaround(packageName: String, action: String): String? = withContext(Dispatchers.IO) {
        getQuirksForPackage(packageName)
            .firstOrNull { it.failedAction.equals(action, ignoreCase = true) }
            ?.workaroundDescription
    }

    /**
     * Clears the quirks database.
     */
    suspend fun clearQuirks(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_QUIRKS_JSON).apply()
    }

    private fun loadAllQuirks(): List<AppQuirk> {
        val raw = prefs.getString(KEY_QUIRKS_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AppQuirk>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveAllQuirks(quirks: List<AppQuirk>) {
        val raw = json.encodeToString(quirks)
        prefs.edit().putString(KEY_QUIRKS_JSON, raw).apply()
    }
}
