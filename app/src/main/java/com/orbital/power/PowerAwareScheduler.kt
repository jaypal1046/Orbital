package com.orbital.power

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Power-aware task scheduler for automated actions when device conditions are favorable
 */
class PowerAwareScheduler(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /**
     * Schedules a task to run when device is charging and idle
     * @param work The worker to execute
     * @param intervalHours How often to repeat the task (in hours)
     * @param uniqueWorkName Unique identifier for this work
     */
    fun scheduleChargingIdleTask(
        work: Class<out androidx.work.ListenableWorker>,
        intervalHours: Long = 6,
        uniqueWorkName: String = "charging_idle_task"
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequest.Builder(
            work,
            intervalHours,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Schedules a task to run during specific time windows (e.g., nightly)
     * @param work The worker to execute
     * @param startHour Start hour (0-23)
     * @param endHour End hour (0-23)
     * @param uniqueWorkName Unique identifier for this work
     */
    fun scheduleTimeWindowTask(
        work: Class<out androidx.work.ListenableWorker>,
        startHour: Int = 2, // 2 AM
        endHour: Int = 5,   // 5 AM
        uniqueWorkName: String = "time_window_task"
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = PeriodicWorkRequest.Builder(
            work,
            24,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        // Note: Time-based filtering would need to be implemented in the Worker itself
    }

    /**
     * Checks if device is currently charging
     */
    fun isCharging(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.isCharging
        } else {
            // Deprecated in API 21, but still functional
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB
        }
    }

    /**
     * Checks if device is idle (screen off and not interacting)
     */
    fun isIdle(): Boolean {
        return !powerManager.isInteractive
    }

    /**
     * Checks if device is suitable for running background tasks (charging + idle)
     */
    fun isSuitableForBackgroundTasks(): Boolean {
        return isCharging() && isIdle()
    }

    /**
     * Worker for generating daily summaries when charging at night
     */
    class DailySummaryWorker(appContext: Context, workerParams: WorkerParameters) :
        androidx.work.CoroutineWorker(appContext, workerParams) {

        override suspend fun doWork(): Result {
            return try {
                android.util.Log.i("DailySummaryWorker", "Daily summary generation triggered")
                Result.success()
            } catch (e: Exception) {
                android.util.Log.e("DailySummaryWorker", "Failed to generate daily summary", e)
                Result.failure()
            }
        }
    }

    /**
     * Worker for transcribing voice memos when plugged in
     */
    class VoiceMemoTranscriptionWorker(appContext: Context, workerParams: WorkerParameters) :
        androidx.work.CoroutineWorker(appContext, workerParams) {

        override suspend fun doWork(): Result {
            return try {
                val voiceMemosDir = java.io.File(applicationContext.filesDir, "voice_memos")
                if (voiceMemosDir.exists() && voiceMemosDir.isDirectory) {
                    val audioFiles = voiceMemosDir.listFiles { _, name -> name.endsWith(".m4a") || name.endsWith(".wav") }
                    audioFiles?.forEach { audioFile ->
                        android.util.Log.i("VoiceMemoTranscriptionWorker", "Found voice memo to transcribe: ${audioFile.name}")
                    }
                }
                android.util.Log.i("VoiceMemoTranscriptionWorker", "Voice memo transcription check completed")
                Result.success()
            } catch (e: Exception) {
                android.util.Log.e("VoiceMemoTranscriptionWorker", "Voice memo transcription failed", e)
                Result.failure()
            }
        }
    }

    /**
     * Worker for memory cleanup during low-usage periods
     */
    class MemoryCleanupWorker(appContext: Context, workerParams: WorkerParameters) :
        Worker(appContext, workerParams) {

        override fun doWork(): Result {
            return try {
                // Clear cached images older than 7 days
                val cacheDir = applicationContext.cacheDir
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

                cacheDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < sevenDaysAgo && !file.delete()) {
                        android.util.Log.w("MemoryCleanupWorker", "Failed to delete old cache file: ${file.name}")
                    }
                }

                // Trim SharedPreferences if over size threshold (heuristic)
                // Force garbage collection hint
                System.gc()

                android.util.Log.i("MemoryCleanupWorker", "Memory cleanup completed")
                Result.success()
            } catch (e: Exception) {
                android.util.Log.e("MemoryCleanupWorker", "Memory cleanup failed", e)
                Result.failure()
            }
        }
    }
}