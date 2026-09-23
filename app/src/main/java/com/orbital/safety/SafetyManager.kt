package com.orbital.safety

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

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
        // Implement tamper detection logic here
        // This will be filled in later with Play Integrity checks
        return false
    }
}
