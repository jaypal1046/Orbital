package com.orbital.cron

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

class CronManager(private val context: Context) {

    companion object {
        private const val TAG = "CronManager"
        private const val UNIQUE_WORK_PREFIX = "orbital_cron_"
    }

    private val workManager = WorkManager.getInstance(context)
    private val ledger = CronTaskLedger(context)

    fun getLedger(): CronTaskLedger = ledger

    fun scheduleCronTask(task: CronTask): Boolean {
        return try {
            ledger.saveTask(task)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val data = workDataOf(
                ScheduledMonitoringWorker.KEY_TASK_ID to task.id,
                ScheduledMonitoringWorker.KEY_TASK_TYPE to task.taskType.name,
                ScheduledMonitoringWorker.KEY_QUERY to task.query,
                ScheduledMonitoringWorker.KEY_TITLE to task.title,
                ScheduledMonitoringWorker.KEY_TARGET_APP to task.targetApp
            )

            val uniqueWorkName = "$UNIQUE_WORK_PREFIX${task.id}"

            // If scheduled for a specific hour/minute daily
            if (task.scheduledHour != null && task.scheduledMinute != null) {
                val initialDelayMillis = calculateInitialDelayMillis(task.scheduledHour, task.scheduledMinute)
                val dailyWorkRequest = PeriodicWorkRequestBuilder<ScheduledMonitoringWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    uniqueWorkName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    dailyWorkRequest
                )
            } else {
                // Repeating interval (e.g. every 15m, 30m, 60m)
                val interval = task.intervalMinutes.coerceAtLeast(15) // WorkManager min interval is 15 mins
                val periodicRequest = PeriodicWorkRequestBuilder<ScheduledMonitoringWorker>(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    uniqueWorkName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicRequest
                )
            }

            Log.i(TAG, "Scheduled background cron task: ${task.id} (${task.title})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule cron task: ${task.id}", e)
            false
        }
    }

    fun cancelCronTask(taskId: String): Boolean {
        return try {
            ledger.removeTask(taskId)
            workManager.cancelUniqueWork("$UNIQUE_WORK_PREFIX$taskId")
            Log.i(TAG, "Cancelled cron task: $taskId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel cron task: $taskId", e)
            false
        }
    }

    fun getActiveTasks(): List<CronTask> {
        return ledger.getAllTasks()
    }

    private fun calculateInitialDelayMillis(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}
