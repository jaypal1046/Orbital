package com.orbital.cron

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orbital.R
import com.orbital.action.DeepLinkLedger

class ScheduledMonitoringWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ScheduledWorker"
        const val KEY_TASK_ID = "cron_task_id"
        const val KEY_TASK_TYPE = "cron_task_type"
        const val KEY_QUERY = "cron_query"
        const val KEY_TITLE = "cron_title"
        const val KEY_TARGET_APP = "cron_target_app"
        private const val CHANNEL_ID = "orbital_automations"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val taskType = inputData.getString(KEY_TASK_TYPE) ?: "GENERAL_REMINDER"
        val query = inputData.getString(KEY_QUERY) ?: "Scheduled Alert"
        val title = inputData.getString(KEY_TITLE) ?: "Orbital Monitor"
        val targetApp = inputData.getString(KEY_TARGET_APP)

        Log.i(TAG, "Executing background cron monitoring task: $taskId ($taskType) for '$query'")

        try {
            // Build 1-Tap Deep Link PendingIntent
            val pendingIntent = buildActionPendingIntent(targetApp, query)

            // Post rich notification to user
            val notificationTitle = when (taskType) {
                "TICKET_ALERT" -> "🎟️ Ticket Alert: $title"
                "TRAIN_MONITOR" -> "🚆 Live Train Status: $title"
                "MOVIE_TICKET_ALERT" -> "🎬 Movie Booking Open: $title"
                "DAILY_BRIEFING" -> "☀️ Orbital Morning Briefing"
                else -> "⏰ Reminder: $title"
            }

            val notificationBody = when (taskType) {
                "TICKET_ALERT" -> "Seats are now available for '$query'. Tap to book immediately."
                "TRAIN_MONITOR" -> "Live status updated for '$query'. Tap to view real-time platform & delay."
                "MOVIE_TICKET_ALERT" -> "Ticket booking is now live for '$query'. Tap to select seats."
                "DAILY_BRIEFING" -> "Here is your scheduled daily commute & agenda for today."
                else -> query
            }

            postNotification(
                id = taskId.hashCode(),
                title = notificationTitle,
                text = notificationBody,
                pendingIntent = pendingIntent
            )

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing cron worker for task $taskId", e)
            return Result.retry()
        }
    }

    private fun buildActionPendingIntent(targetApp: String?, query: String): PendingIntent {
        val intent = if (!targetApp.isNullOrBlank()) {
            DeepLinkLedger.buildDeepLinkIntent(context, targetApp, mapOf("query" to query))
        } else {
            DeepLinkLedger.buildDeepLinkIntent(context, "WHERE_IS_MY_TRAIN", mapOf("query" to query))
        } ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        return PendingIntent.getActivity(
            context,
            query.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postNotification(id: Int, title: String, text: String, pendingIntent: PendingIntent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Orbital Automations & Cron Monitors",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Automated train status, ticket availability alerts, and background reminders"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher, "Open / Book Now", pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            Log.w(TAG, "Notification permission not granted.")
        }
    }
}
