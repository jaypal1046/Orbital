package com.orbital.power

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.wifi.WifiManager
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.Calendar

/**
 * Power-aware task scheduler for automated actions when device conditions are favorable
 */
class PowerAwareScheduler(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "orbital_automations"
        private const val DAILY_BRIEFING_WORK = "daily_briefing"
        private const val REMINDER_WORK_PREFIX = "reminder_"
        private const val PREFS = "automations"
        private const val WORK_WIFI = "work_wifi"
        private const val HOME_WIFI = "home_wifi"

        fun notify(context: Context, id: Int, title: String, text: String, optimizeBattery: Boolean = false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(android.app.NotificationChannel(CHANNEL_ID, "Orbital automations", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            }
            val intent = if (optimizeBattery) {
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            } else {
                Intent(context, com.orbital.ui.MainActivity::class.java)
            }
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.orbital.R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build())
        }
    }

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

    fun scheduleDailyBriefing(hour: Int) {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequest.Builder(DailyBriefingWorker::class.java, 24, TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_BRIEFING_WORK, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    fun scheduleReminder(label: String, repeatMinutes: Long?, hour: Int?, minutes: Int?): com.orbital.action.ActionResult {
        val safeLabel = label.take(120)
        val request = when {
            repeatMinutes != null -> PeriodicWorkRequest.Builder(ReminderWorker::class.java, repeatMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf("label" to safeLabel)).build()
            hour != null && minutes != null -> {
                val now = Calendar.getInstance()
                val next = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minutes); set(Calendar.SECOND, 0)
                    if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
                }
                PeriodicWorkRequest.Builder(ReminderWorker::class.java, 24, TimeUnit.HOURS)
                    .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("label" to safeLabel)).build()
            }
            else -> return com.orbital.action.ActionResult.Error("Specify repeat_minutes or hour and minutes")
        }
        workManager.enqueueUniquePeriodicWork(REMINDER_WORK_PREFIX + safeLabel.hashCode(), ExistingPeriodicWorkPolicy.REPLACE, request)
        return com.orbital.action.ActionResult.Success("Scheduled reminder: $safeLabel")
    }

    fun saveWifiAutomations(workWifi: String, homeWifi: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(WORK_WIFI, workWifi.trim()).putString(HOME_WIFI, homeWifi.trim()).apply()
    }

    fun registerWifiAutomation() {
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return
                val wifi = receiverContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssid = try { wifi.connectionInfo.ssid.trim('"') } catch (_: SecurityException) { return }
                val prefs = receiverContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                when (ssid) {
                    prefs.getString(WORK_WIFI, "") -> if (ssid.isNotBlank()) (receiverContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    prefs.getString(HOME_WIFI, "") -> if (ssid.isNotBlank()) notify(receiverContext, 220, "Welcome home", "Wrap up focus work when you are ready.")
                }
            }
        }, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
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

    class DailyBriefingWorker(appContext: Context, workerParams: WorkerParameters) : androidx.work.CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val battery = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            notify(applicationContext, 200, "Daily briefing", "Battery $percent%. Focus goal: choose one priority. Keep moving forward.")
            return Result.success()
        }
    }

    class ReminderWorker(appContext: Context, workerParams: WorkerParameters) : androidx.work.CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            notify(applicationContext, 201 + inputData.getString("label").orEmpty().hashCode(), "Reminder", inputData.getString("label") ?: "Time for your task")
            return Result.success()
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
