package com.orbital.safety

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class SafetyManager(private val context: Context) {

    companion object {
        private const val TAG = "SafetyManager"
        private val PAYMENT_APPS = listOf(
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "com.paytm",
            "com.bhim.upi",
            "com.hdfcbank",
            "com.sbi",
            "com.icicibank",
            "com.axis.mobile",
            "com.kotak.retail",
            "com.bob.banking",
            "com.indusind",
            "com.unionbank"
        )
    }

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val safetyJob = SupervisorJob()
    private val safetyScope = CoroutineScope(Dispatchers.IO + safetyJob)

    fun isPaymentAppInForeground(): Boolean {
        usageStatsManager ?: run {
            Log.e(TAG, "UsageStatsManager not available")
            return false
        }

        val time = System.currentTimeMillis()
        val usageStats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            usageStatsManager!!.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 60, // 1 hour ago
                time
            )
        } else {
            emptyList()
        }

        val currentApp = usageStats?.maxByOrNull { it.lastTimeUsed }?.packageName

        return currentApp != null && PAYMENT_APPS.contains(currentApp)
    }

    fun isAppInForeground(packageName: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningProcesses = activityManager.runningAppProcesses

        return runningProcesses?.any {
            it.processName == packageName &&
            it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: false
    }

    fun isTampered(): Boolean {
        // Synchronous check - performs quick local checks
        return performLocalTamperChecks()
    }

    suspend fun performIntegrityCheck(): Boolean {
        // Perform local tamper checks asynchronously
        return withContext(Dispatchers.IO) {
            performLocalTamperChecks()
        }
    }

    private fun performLocalTamperChecks(): Boolean {
        var tampered = false

        // Check 1: Debuggable flag
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w(TAG, "App is debuggable")
            tampered = true
        }

        // Check 2: Test-only flag
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY) != 0) {
            Log.w(TAG, "App is test-only")
            tampered = true
        }

        // Check 3: Verify signature matches expected (basic check)
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = pkgInfo.signingInfo
                if (signingInfo == null || (!signingInfo.hasMultipleSigners() && signingInfo.signingCertificateHistory.isNullOrEmpty())) {
                    Log.w(TAG, "No signing certificates found")
                    tampered = true
                }
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = pkgInfo.signatures
                if (signatures.isNullOrEmpty()) {
                    Log.w(TAG, "No signatures found")
                    tampered = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Signature check failed", e)
            tampered = true
        }

        // Check 4: Check if installed from unknown source (side-loaded)
        try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            if (installer.isNullOrBlank()) {
                Log.w(TAG, "No installer package - possibly side-loaded")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Installer check failed", e)
        }

        return tampered
    }

    fun shutdown() {
        safetyJob.cancel()
    }
}
