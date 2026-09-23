package com.orbital.action

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class ActionResult {
    data class Success(val message: String, val details: String? = null) : ActionResult()
    data class Error(val errorMessage: String) : ActionResult()
}

@Serializable
data class DeviceAction(
    val action: String, // OPEN_APP, SEARCH_WEB, OPEN_URL, SET_TIMER, OPEN_SETTING, DEVICE_STATUS, MAKE_CALL, SEND_SMS
    val target: String? = null,
    val query: String? = null,
    val url: String? = null,
    val seconds: Int? = null,
    val label: String? = null,
    val phoneNumber: String? = null,
    val message: String? = null
)

class DeviceActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DeviceActionExecutor"

        val COMMON_APP_PACKAGES = mapOf(
            "gmail" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "calendar" to "com.google.android.calendar",
            "play store" to "com.android.vending",
            "playstore" to "com.android.vending",
            "messages" to "com.google.android.apps.messaging",
            "dialer" to "com.google.android.dialer",
            "phone" to "com.google.android.dialer"
        )
    }

    fun execute(action: DeviceAction): ActionResult {
        Log.i(TAG, "Executing device action: ${action.action} on target: ${action.target ?: action.query ?: action.url}")
        return try {
            when (action.action.uppercase().trim()) {
                "OPEN_APP", "LAUNCH_APP" -> openApp(action.target ?: action.query ?: "")
                "SEARCH_WEB", "SEARCH" -> searchWeb(action.query ?: action.target ?: "")
                "OPEN_URL", "LAUNCH_URL" -> openUrl(action.url ?: action.target ?: "")
                "SET_TIMER", "TIMER" -> setTimer(action.seconds ?: 60, action.label ?: "AI Companion Timer")
                "OPEN_SETTING", "SETTINGS" -> openSetting(action.target ?: "")
                "DEVICE_STATUS", "BATTERY" -> getDeviceStatus()
                "MAKE_CALL", "CALL" -> makeCall(action.phoneNumber ?: action.target ?: "")
                "SEND_SMS", "SMS" -> sendSms(action.phoneNumber ?: action.target, action.message ?: "")
                else -> ActionResult.Error("Unknown action type: ${action.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute action ${action.action}", e)
            ActionResult.Error("Failed to execute ${action.action}: ${e.message}")
        }
    }

    fun openApp(nameOrPackage: String): ActionResult {
        val cleanName = nameOrPackage.trim().lowercase()
        val pm = context.packageManager

        // 1. Check if direct package name exists
        val directIntent = pm.getLaunchIntentForPackage(cleanName)
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return ActionResult.Success("Opened app $cleanName", cleanName)
        }

        // 2. Check predefined common packages map
        val mappedPackage = COMMON_APP_PACKAGES[cleanName]
        if (mappedPackage != null) {
            val mappedIntent = pm.getLaunchIntentForPackage(mappedPackage)
            if (mappedIntent != null) {
                mappedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(mappedIntent)
                return ActionResult.Success("Opened $nameOrPackage ($mappedPackage)", mappedPackage)
            }
        }

        // 3. Search installed applications by label
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matchedApp = installedApps.firstOrNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label.contains(cleanName) || cleanName.contains(label)
        }

        if (matchedApp != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val appLabel = pm.getApplicationLabel(matchedApp).toString()
                return ActionResult.Success("Opened $appLabel", matchedApp.packageName)
            }
        }

        // 4. Fallback: Search on Google Play Store
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$cleanName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(marketIntent)
            ActionResult.Success("Opened Play Store search for $nameOrPackage")
        } catch (e: Exception) {
            ActionResult.Error("App '$nameOrPackage' not found on this device.")
        }
    }

    fun searchWeb(query: String): ActionResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ActionResult.Error("Search query is empty")

        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, cleanQuery)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Searching web for: '$cleanQuery'")
    }

    fun openUrl(url: String): ActionResult {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Opened $cleanUrl")
    }

    fun setTimer(seconds: Int, label: String): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Set timer for ${seconds}s ($label)")
        } catch (e: Exception) {
            ActionResult.Error("Could not launch clock/timer app: ${e.message}")
        }
    }

    fun openSetting(target: String): ActionResult {
        val intent = when (target.lowercase().trim()) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "battery", "power" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "applications", "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Opened ${target.ifBlank { "Device" }} Settings")
    }

    fun getDeviceStatus(): ActionResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        val statusInfo = "Battery: $batteryPct% ${if (isCharging) "(Charging ⚡)" else ""}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        return ActionResult.Success("Device status checked", statusInfo)
    }

    fun makeCall(phoneNumber: String): ActionResult {
        val cleanPhone = phoneNumber.trim()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Opened dialer for $cleanPhone")
    }

    fun sendSms(phoneNumber: String?, message: String): ActionResult {
        val uri = if (!phoneNumber.isNullOrBlank()) Uri.parse("smsto:$phoneNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Composing SMS message")
    }
}
