package com.orbital.updater

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class InAppUpdateStatus {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
    UP_TO_DATE
}

@Singleton
class PlayStoreInAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "PlayStoreUpdateManager"
        const val UPDATE_REQUEST_CODE = 9001
    }

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(context)
    }

    private val _updateStatus = MutableStateFlow(InAppUpdateStatus.IDLE)
    val updateStatus: StateFlow<InAppUpdateStatus> = _updateStatus.asStateFlow()

    private var cachedUpdateInfo: AppUpdateInfo? = null

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                _updateStatus.value = InAppUpdateStatus.DOWNLOADING
                Log.d(TAG, "Update downloading: ${state.bytesDownloaded()}/${state.totalBytesToDownload()}")
            }
            InstallStatus.DOWNLOADED -> {
                _updateStatus.value = InAppUpdateStatus.DOWNLOADED
                Log.i(TAG, "Update downloaded. Ready to complete installation.")
            }
            InstallStatus.FAILED -> {
                _updateStatus.value = InAppUpdateStatus.FAILED
                Log.w(TAG, "Update failed: ${state.installErrorCode()}")
            }
            else -> {}
        }
    }

    init {
        try {
            appUpdateManager.registerListener(installListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Play Store install listener: ${e.message}")
        }
    }

    /**
     * Checks Google Play for in-app update availability.
     */
    fun checkForPlayStoreUpdate(
        onUpdateAvailable: ((AppUpdateInfo, Boolean) -> Unit)? = null,
        onNotAvailable: (() -> Unit)? = null
    ) {
        _updateStatus.value = InAppUpdateStatus.CHECKING
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                cachedUpdateInfo = info
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    val isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    val isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    _updateStatus.value = InAppUpdateStatus.UPDATE_AVAILABLE
                    Log.i(TAG, "Play Store update available (Immediate=$isImmediateAllowed, Flexible=$isFlexibleAllowed)")
                    onUpdateAvailable?.invoke(info, isImmediateAllowed)
                } else {
                    _updateStatus.value = InAppUpdateStatus.UP_TO_DATE
                    onNotAvailable?.invoke()
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "Play Store update check failed: ${e.message}")
                _updateStatus.value = InAppUpdateStatus.IDLE
                onNotAvailable?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Play Store update invocation error: ${e.message}")
            _updateStatus.value = InAppUpdateStatus.IDLE
            onNotAvailable?.invoke()
        }
    }

    /**
     * Starts the official in-app update flow (Flexible or Immediate) without requiring any permissions.
     */
    fun startUpdate(
        activity: Activity,
        immediate: Boolean = false,
        requestCode: Int = UPDATE_REQUEST_CODE
    ): Boolean {
        val info = cachedUpdateInfo ?: return false
        val updateType = if (immediate && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            AppUpdateType.IMMEDIATE
        } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            AppUpdateType.FLEXIBLE
        } else {
            return false
        }

        return try {
            appUpdateManager.startUpdateFlowForResult(info, updateType, activity, requestCode)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Play Store update flow", e)
            false
        }
    }

    /**
     * Completes installation of a flexible in-app update and restarts the app.
     */
    fun completeDownloadedUpdate() {
        if (_updateStatus.value == InAppUpdateStatus.DOWNLOADED) {
            appUpdateManager.completeUpdate()
        }
    }

    fun unregister() {
        try {
            appUpdateManager.unregisterListener(installListener)
        } catch (_: Exception) {}
    }
}
