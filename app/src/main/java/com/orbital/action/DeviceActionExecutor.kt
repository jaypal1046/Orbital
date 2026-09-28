package com.orbital.action

import android.app.SearchManager
import android.bluetooth.BluetoothAdapter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.net.wifi.WifiManager
import android.util.Log
import com.orbital.power.PowerAwareScheduler
import kotlinx.serialization.Serializable

sealed class ActionResult {
    data class Success(val message: String, val details: String? = null) : ActionResult()
    data class Error(val errorMessage: String) : ActionResult()
}

@Serializable
data class DeviceAction(
    val action: String, // OPEN_APP, SEARCH_APP, SEARCH_WEB, OPEN_URL, SET_TIMER, OPEN_SETTING, DEVICE_STATUS, MAKE_CALL, SEND_SMS, NAVIGATE, PLAY_MUSIC, COMPOSE_EMAIL
    val target: String? = null,
    val query: String? = null,
    val url: String? = null,
    val seconds: Int? = null,
    val label: String? = null,
    val phoneNumber: String? = null,
    val recipient: String? = null,
    val subject: String? = null,
    val message: String? = null,
    val title: String? = null,
    val startTimeMillis: Long? = null,
    val notes: String? = null,
    val hour: Int? = null,
    val minutes: Int? = null,
    val enabled: Boolean? = null,
    val ifBatteryBelow: Int? = null,
    val repeatMinutes: Long? = null
)

open class DeviceActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DeviceActionExecutor"
    }

    private val capabilityManager = AppCapabilityManager(context)

    open fun getCapabilityManager(): AppCapabilityManager = capabilityManager

    open fun getInstalledAppNames(): List<String> {
        return capabilityManager.getInstalledApps().map { it.name }
    }

    open fun execute(action: DeviceAction): ActionResult {
        Log.i(TAG, "Executing dynamic device action: ${action.action} on target: ${action.target ?: action.query ?: action.url}")
        return try {
            action.ifBatteryBelow?.let { threshold ->
                val battery = batteryPercent()
                if (battery < 0) return ActionResult.Error("Could not read battery level")
                if (battery >= threshold) return ActionResult.Success("Skipped ${action.action}: battery is $battery%", "Battery Saver opens below $threshold%")
            }
            when (action.action.uppercase().trim()) {
                "OPEN_APP", "LAUNCH_APP" -> openApp(action.target ?: action.query ?: "")
                "SEARCH_APP", "SEARCH_IN_APP" -> searchInApp(action.target ?: "", action.query ?: "")
                "SEARCH_WEB", "SEARCH" -> searchWeb(action.query ?: action.target ?: "", targetBrowser = action.target)
                "OPEN_URL", "LAUNCH_URL" -> openUrl(action.url ?: action.target ?: "")
                "NAVIGATE", "DIRECTIONS", "MAPS" -> navigateTo(action.query ?: action.target ?: "")
                "PLAY_MUSIC", "PLAY_MEDIA", "PLAY" -> playMusicOrVideo(action.target ?: "", action.query ?: action.label ?: "")
                "COMPOSE_EMAIL", "EMAIL", "SEND_EMAIL" -> composeEmail(
                    recipient = action.recipient ?: (if (action.target?.contains("@") == true) action.target else null),
                    subject = action.subject,
                    body = action.message ?: action.query,
                    target = action.target
                )
                "SET_TIMER", "TIMER" -> setTimer(action.seconds ?: 60, action.label ?: "Focus Timer")
                "SET_ALARM", "ALARM" -> setAlarm(action.hour, action.minutes, action.label ?: "Alarm")
                "CREATE_CALENDAR_EVENT", "CALENDAR_EVENT" -> createCalendarEvent(action.title, action.startTimeMillis, action.notes)
                "FLASHLIGHT", "TORCH" -> setFlashlight(action.enabled ?: action.target.equals("on", ignoreCase = true))
                "SET_SOUND_MODE", "SOUND_MODE" -> setSoundMode(action.target ?: "")
                "CONNECTIVITY_STATUS", "NETWORK_STATUS" -> getConnectivityStatus()
                "SCHEDULE_REMINDER", "REMINDER" -> PowerAwareScheduler(context).scheduleReminder(
                    action.label ?: action.message ?: action.query ?: "Reminder", action.repeatMinutes, action.hour, action.minutes
                )
                "OPEN_SETTING", "SETTINGS" -> openSetting(action.target ?: "")
                "DEVICE_STATUS", "BATTERY" -> getDeviceStatus()
                "MAKE_CALL", "CALL" -> makeCall(action.phoneNumber ?: action.target ?: "")
                "SEND_SMS", "SMS", "WHATSAPP", "SEND_MESSAGE" -> sendMessage(
                    action.target ?: if (action.action.equals("WHATSAPP", ignoreCase = true)) "whatsapp" else "sms",
                    action.phoneNumber ?: action.recipient,
                    action.message ?: action.query ?: ""
                )
                else -> ActionResult.Error("Unknown action type: ${action.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute action ${action.action}", e)
            ActionResult.Error("Could not perform ${action.action}: ${e.message?.take(80) ?: "Action failed"}")
        }
    }

    fun requiresConfirmation(action: DeviceAction): Boolean = action.action.uppercase().trim() in setOf(
        "SEND_SMS", "SMS", "WHATSAPP", "SEND_MESSAGE", "MAKE_CALL", "CALL"
    )

    fun openApp(nameOrPackage: String): ActionResult {
        val rawName = nameOrPackage.trim()
        if (rawName.isBlank()) {
            return ActionResult.Error("App name is required.")
        }

        val pm = context.packageManager

        // Step 1: Direct package lookup
        if (rawName.contains(".") && !rawName.contains(" ")) {
            val directIntent = pm.getLaunchIntentForPackage(rawName)
            if (directIntent != null) {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                return ActionResult.Success("Opened $rawName", rawName)
            }
        }

        // Step 2: Query dynamic capability registry for installed apps on device
        val matchedApp = capabilityManager.findBestAppMatch(rawName)
        if (matchedApp != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult.Success("Opened ${matchedApp.name}", matchedApp.packageName)
            }
        }

        // Step 3: Generic Android Category Intent Fallbacks
        val categoryResult = launchCategoryFallback(rawName.lowercase())
        if (categoryResult != null) {
            return categoryResult
        }

        return ActionResult.Error("App '$nameOrPackage' is not installed.")
    }

    fun searchInApp(targetApp: String, query: String): ActionResult {
        val cleanQuery = query.trim()
        val lowerTarget = targetApp.lowercase().trim()

        if (lowerTarget.contains("youtube") || lowerTarget.contains("video")) {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success("Searching YouTube for '$cleanQuery'")
            } catch (e: Exception) {
                openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery))
            }
        }

        if (lowerTarget.contains("spotify") || lowerTarget.contains("music")) {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(SearchManager.QUERY, cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success("Playing music: '$cleanQuery'")
            } catch (e: Exception) {
                openUrl("https://open.spotify.com/search/" + Uri.encode(cleanQuery))
            }
        }

        if (lowerTarget.contains("gmail") || lowerTarget.contains("mail") || lowerTarget.contains("email")) {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.gm")
                putExtra(SearchManager.QUERY, cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success("Opening Gmail for '$cleanQuery'")
            } catch (e: Exception) {
                openApp("Gmail")
            }
        }

        if (lowerTarget.contains("map") || lowerTarget.contains("place") || lowerTarget.contains("navigate")) {
            return navigateTo(cleanQuery)
        }

        if (lowerTarget.contains("chrome") || lowerTarget.contains("browser") || lowerTarget.contains("edge") || lowerTarget.contains("firefox") || lowerTarget.contains("google")) {
            return searchWeb(cleanQuery, targetBrowser = targetApp)
        }

        if (lowerTarget.contains("playstore") || lowerTarget.contains("play store") || lowerTarget.contains("store")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(cleanQuery))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success("Searching Play Store for '$cleanQuery'")
            } catch (e: Exception) {
                openUrl("https://play.google.com/store/search?q=" + Uri.encode(cleanQuery))
            }
        }

        // Default: Web search
        return searchWeb(cleanQuery, targetBrowser = targetApp)
    }

    fun navigateTo(destination: String): ActionResult {
        val cleanDest = destination.trim()
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(cleanDest))
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(mapIntent)
            ActionResult.Success("Navigating to '$cleanDest' via Google Maps")
        } catch (e: Exception) {
            val genericUri = Uri.parse("geo:0,0?q=" + Uri.encode(cleanDest))
            val genericIntent = Intent(Intent.ACTION_VIEW, genericUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericIntent)
            ActionResult.Success("Searching location '$cleanDest'")
        }
    }

    fun playMusicOrVideo(target: String, query: String): ActionResult {
        if (target.contains("youtube", ignoreCase = true) || query.contains("video", ignoreCase = true)) {
            return searchInApp("youtube", query)
        }
        return searchInApp("spotify", query)
    }

    fun composeEmail(recipient: String?, subject: String?, body: String?, target: String? = null): ActionResult {
        val cleanTarget = target?.lowercase()?.trim() ?: ""
        val isGmailExplicit = cleanTarget.contains("gmail") || cleanTarget.contains("com.google.android.gm")
        val isOutlookExplicit = cleanTarget.contains("outlook")

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (!recipient.isNullOrBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            }
            if (!subject.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            if (!body.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, body)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val targetPackage = when {
            isGmailExplicit -> "com.google.android.gm"
            isOutlookExplicit -> "com.microsoft.office.outlook"
            isPackageInstalled("com.google.android.gm") -> "com.google.android.gm"
            else -> null
        }

        if (targetPackage != null && isPackageInstalled(targetPackage)) {
            intent.setPackage(targetPackage)
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Composing email to ${recipient ?: "draft"}")
        } catch (e: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
                ActionResult.Success("Composing email to ${recipient ?: "draft"}")
            } catch (ex: Exception) {
                ActionResult.Error("No email app found on device.")
            }
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendMessage(target: String, phoneNumber: String?, message: String): ActionResult {
        if (target.contains("whatsapp", ignoreCase = true) || target.contains("wa", ignoreCase = true)) {
            val cleanPhone = phoneNumber?.replace(Regex("[^0-9]"), "") ?: ""
            if (cleanPhone.isNotBlank()) {
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                return try {
                    context.startActivity(intent)
                    ActionResult.Success("Opened WhatsApp for $cleanPhone")
                } catch (e: Exception) {
                    openApp("com.whatsapp")
                }
            } else {
                // Launch WhatsApp app directly
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ActionResult.Success("Opened WhatsApp")
                } else {
                    val url = "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Opening WhatsApp")
                    } catch (e: Exception) {
                        ActionResult.Error("WhatsApp is not installed on this device.")
                    }
                }
            }
        }

        return sendSms(phoneNumber, message)
    }

    private fun launchCategoryFallback(cleanName: String): ActionResult? {
        return when {
            cleanName in listOf("browser", "internet", "web") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult.Success("Opened Browser")
            }
            cleanName in listOf("clock", "alarm", "timer") -> {
                val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    ActionResult.Success("Opened Clock app")
                } catch (e: Exception) {
                    val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(clockIntent)
                        ActionResult.Success("Opened Clock app")
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            cleanName in listOf("camera", "cam") -> {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    ActionResult.Success("Opened Camera")
                } catch (e: Exception) {
                    null
                }
            }
            cleanName in listOf("dialer", "phone", "call") -> {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult.Success("Opened Phone Dialer")
            }
            cleanName in listOf("message", "messages", "sms") -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    ActionResult.Success("Opened Messaging App")
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    fun searchWeb(query: String, targetBrowser: String? = null): ActionResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ActionResult.Error("Search query is empty")

        // Guard: If the model mistakenly outputs email query filters to searchWeb, redirect to Gmail app
        if (cleanQuery.startsWith("is:") || cleanQuery.contains("is:unread") || cleanQuery.contains("is:starred")) {
            return searchInApp("Gmail", cleanQuery)
        }

        val searchUrl = "https://www.google.com/search?q=" + Uri.encode(cleanQuery)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val cleanTarget = targetBrowser?.lowercase()?.trim() ?: ""
        val targetPackage = when {
            cleanTarget.contains("chrome") -> "com.android.chrome"
            cleanTarget.contains("edge") -> "com.microsoft.emmx"
            cleanTarget.contains("firefox") -> "org.mozilla.firefox"
            cleanTarget.contains("brave") -> "com.brave.browser"
            cleanTarget.contains("opera") -> "com.opera.browser"
            isPackageInstalled("com.android.chrome") -> "com.android.chrome"
            else -> null
        }

        if (targetPackage != null && isPackageInstalled(targetPackage)) {
            intent.setPackage(targetPackage)
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Searching web for: '$cleanQuery'")
        } catch (e: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
                ActionResult.Success("Searching web for: '$cleanQuery'")
            } catch (ex: Exception) {
                ActionResult.Error("No browser available on device.")
            }
        }
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
        } catch (e: SecurityException) {
            // OEM permission fallback: Launch clock app directly
            val fallback = launchCategoryFallback("clock") ?: openApp("clock")
            if (fallback is ActionResult.Success) {
                ActionResult.Success("Opened Clock app (${seconds / 60}m timer)")
            } else {
                ActionResult.Error("Timer permission required. Please grant Alarm/Timer permission in App Settings.")
            }
        } catch (e: Exception) {
            val fallback = launchCategoryFallback("clock") ?: openApp("clock")
            if (fallback is ActionResult.Success) {
                ActionResult.Success("Opened Clock app for timer")
            } else {
                ActionResult.Error("Could not launch timer app: ${e.message?.take(60) ?: "Error"}")
            }
        }
    }

    fun setAlarm(hour: Int?, minutes: Int?, label: String): ActionResult {
        if (hour == null || minutes == null) return ActionResult.Error("Alarm hour and minutes are required")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Set alarm for %02d:%02d".format(hour, minutes))
    }

    fun createCalendarEvent(title: String?, startTimeMillis: Long?, notes: String?): ActionResult {
        if (title.isNullOrBlank() || startTimeMillis == null) return ActionResult.Error("Calendar event title and start time are required")
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, notes)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("Opening Calendar for $title")
    }

    fun setFlashlight(enabled: Boolean): ActionResult {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionResult.Error("No flashlight is available on this device")
        cameraManager.setTorchMode(cameraId, enabled)
        return ActionResult.Success("Flashlight ${if (enabled) "on" else "off"}")
    }

    fun setSoundMode(mode: String): ActionResult {
        val ringerMode = when (mode.lowercase().trim()) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate", "vibration" -> AudioManager.RINGER_MODE_VIBRATE
            "normal", "ring" -> AudioManager.RINGER_MODE_NORMAL
            else -> return ActionResult.Error("Sound mode must be silent, vibrate, or normal")
        }
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = ringerMode
        return ActionResult.Success("Sound mode set to ${mode.lowercase()}")
    }

    fun getConnectivityStatus(): ActionResult {
        val wifiEnabled = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        val bluetooth = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled?.toString() ?: "Unavailable" } catch (_: SecurityException) { "Permission required" }
        return ActionResult.Success("Connectivity checked", "Wi-Fi: ${if (wifiEnabled) "On" else "Off"}\nBluetooth: $bluetooth")
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
            "sound", "volume" -> Intent(Settings.ACTION_SOUND_SETTINGS)
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

    private fun batteryPercent(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
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
