package com.orbital.cron

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class CronTaskType {
    TRAIN_MONITOR,          // Live train delay / running status monitor
    TICKET_ALERT,           // Seat availability / Tatkal opening alert
    MOVIE_TICKET_ALERT,     // BookMyShow ticket opening alert
    DAILY_BRIEFING,         // Morning routine / commute briefing
    PRICE_DROP_ALERT,       // Product price monitor
    GENERAL_REMINDER        // Standard timed reminder
}

@Serializable
data class CronTask(
    val id: String,
    val taskType: CronTaskType,
    val title: String,
    val query: String,
    val intervalMinutes: Long = 60,
    val scheduledHour: Int? = null,
    val scheduledMinute: Int? = null,
    val targetApp: String? = null,
    val targetCondition: String? = null, // e.g. "SEATS_AVAILABLE", "DELAY_GT_10M"
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

class CronTaskLedger(context: Context) {

    companion object {
        private const val PREFS_NAME = "orbital_cron_tasks"
        private const val KEY_TASKS = "registered_tasks"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllTasks(): List<CronTask> {
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<CronTask>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTask(task: CronTask) {
        val current = getAllTasks().filter { it.id != task.id }.toMutableList()
        current.add(task)
        prefs.edit().putString(KEY_TASKS, json.encodeToString(current)).apply()
    }

    fun removeTask(taskId: String) {
        val current = getAllTasks().filter { it.id != taskId }
        prefs.edit().putString(KEY_TASKS, json.encodeToString(current)).apply()
    }

    fun findTask(taskId: String): CronTask? {
        return getAllTasks().firstOrNull { it.id == taskId }
    }
}
