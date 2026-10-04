package com.orbital.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orbital.action.DeviceAction
import com.orbital.action.DeviceActionExecutor
import com.orbital.session.SessionEventLogger
import com.orbital.session.SessionEventType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class BackgroundAgentWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val actionExecutor: DeviceActionExecutor,
    private val sessionEventLogger: SessionEventLogger
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BackgroundAgentWorker"
        const val KEY_ACTION = "worker_action"
        const val KEY_TARGET = "worker_target"
        const val KEY_SESSION_ID = "worker_session_id"
        const val WORK_NAME = "orbital_background_agent_task"

        /**
         * Enqueues a one-time background agent action.
         */
        fun enqueueAction(
            context: Context,
            action: String,
            target: String? = null,
            sessionId: String = "background_daemon"
        ) {
            val inputData = workDataOf(
                KEY_ACTION to action,
                KEY_TARGET to target,
                KEY_SESSION_ID to sessionId
            )
            val request = OneTimeWorkRequestBuilder<BackgroundAgentWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionName = inputData.getString(KEY_ACTION) ?: "DEVICE_STATUS"
        val target = inputData.getString(KEY_TARGET)
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: "background_daemon"

        Log.i(TAG, "Executing background agent task: $actionName on target: $target")

        sessionEventLogger.logEvent(
            sessionId = sessionId,
            type = SessionEventType.TOOL_DISPATCH,
            source = "BACKGROUND_WORKER",
            summary = "Started background task $actionName",
            payload = mapOf("action" to actionName, "target" to (target ?: ""))
        )

        val deviceAction = DeviceAction(
            action = actionName,
            target = target
        )

        val result = actionExecutor.execute(deviceAction)
        val isSuccess = result is com.orbital.action.ActionResult.Success

        sessionEventLogger.logEvent(
            sessionId = sessionId,
            type = if (isSuccess) SessionEventType.VERIFICATION else SessionEventType.ERROR,
            source = "BACKGROUND_WORKER",
            summary = if (isSuccess) "Background task succeeded" else "Background task failed",
            payload = mapOf(
                "status" to if (isSuccess) "SUCCESS" else "FAILED",
                "action" to actionName
            )
        )

        if (isSuccess) Result.success() else Result.retry()
    }
}
