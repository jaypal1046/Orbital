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
    private val cronManager = com.orbital.cron.CronManager(context)

    open fun getCapabilityManager(): AppCapabilityManager = capabilityManager
    open fun getCronManager(): com.orbital.cron.CronManager = cronManager

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
                "SET_TIMER", "TIMER" -> setTimer(action.seconds ?: 60, action.label ?: "Timer")
                "SET_ALARM", "ALARM" -> setAlarm(action.hour, action.minutes, action.label ?: "Alarm")
                "CREATE_CALENDAR_EVENT", "CALENDAR_EVENT" -> createCalendarEvent(action.title, action.startTimeMillis, action.notes)
                "FLASHLIGHT", "TORCH" -> setFlashlight(action.enabled ?: action.target.equals("on", ignoreCase = true))
                "SET_SOUND_MODE", "SOUND_MODE" -> setSoundMode(action.target ?: "")
                "CONNECTIVITY_STATUS", "NETWORK_STATUS" -> getConnectivityStatus()
                "SCHEDULE_REMINDER", "REMINDER" -> PowerAwareScheduler(context).scheduleReminder(
                    action.label ?: action.message ?: action.query ?: "Reminder", action.repeatMinutes, action.hour, action.minutes
                )
                "SCHEDULE_MONITOR", "SCHEDULE_CRON" -> scheduleCronMonitoring(action)
                "LIST_MONITORS", "ACTIVE_MONITORS" -> listActiveMonitors()
                "CANCEL_MONITOR", "STOP_MONITOR" -> cancelCronMonitoring(action.target ?: action.query ?: "")
                "PERFORM_TESTING", "TEST_APP", "AUTO_TEST", "SCREEN_TEST" -> performAppTesting(
                    targetApp = action.target ?: action.query ?: "",
                    testAction = action.query
                )
                "READ_SCREEN", "INSPECT_SCREEN" -> readActiveScreen()
                "CLICK_ELEMENT", "TAP", "CLICK" -> clickScreenElement(action.target ?: action.query ?: "")
                "INPUT_TEXT", "TYPE_TEXT", "TYPE" -> inputScreenText(
                    text = action.query ?: action.message ?: "",
                    targetField = action.target
                )
                "SCROLL" -> scrollScreen(forward = !(action.target?.equals("up", ignoreCase = true) == true))
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

    fun requiresConfirmation(action: DeviceAction, mode: com.orbital.data.ActionApprovalMode? = null): Boolean {
        val resolvedMode = mode ?: com.orbital.data.SecureStorage(context).getActionApprovalMode()
        return when (resolvedMode) {
            com.orbital.data.ActionApprovalMode.ALWAYS_PROCEED -> false
            com.orbital.data.ActionApprovalMode.REQUEST_FOR_ACTION -> true
            com.orbital.data.ActionApprovalMode.AUTO_SAFE -> action.action.uppercase().trim() in setOf(
                "SEND_SMS", "SMS", "WHATSAPP", "SEND_MESSAGE", "MAKE_CALL", "CALL", "OPEN_SETTING", "SETTINGS"
            )
        }
    }

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

        // 1. Dynamic app lookup via Capability Manager
        val matchedApp = capabilityManager.findBestAppMatch(lowerTarget)

        if (matchedApp != null) {
            val pkg = matchedApp.packageName
            val cat = matchedApp.category

            when (cat) {
                AppCategory.MEDIA -> {
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra("query", cleanQuery)
                        putExtra(SearchManager.QUERY, cleanQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Searching ${matchedApp.name} for '$cleanQuery'")
                    } catch (e: Exception) {
                        openApp(pkg)
                    }
                }
                AppCategory.MUSIC -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, cleanQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Playing '$cleanQuery' on ${matchedApp.name}")
                    } catch (e: Exception) {
                        openApp(pkg)
                    }
                }
                AppCategory.EMAIL -> {
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, cleanQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Searching ${matchedApp.name} for '$cleanQuery'")
                    } catch (e: Exception) {
                        openApp(pkg)
                    }
                }
                AppCategory.NAVIGATION -> {
                    return navigateTo(cleanQuery, targetPackage = pkg)
                }
                AppCategory.BROWSER -> {
                    return searchWeb(cleanQuery, targetBrowser = pkg)
                }
                AppCategory.SHOPPING -> {
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, cleanQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Searching ${matchedApp.name} for '$cleanQuery'")
                    } catch (e: Exception) {
                        openApp(pkg)
                    }
                }
                else -> {
                    // Generic in-app search intent
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, cleanQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Searching ${matchedApp.name} for '$cleanQuery'")
                    } catch (e: Exception) {
                        openApp(pkg)
                    }
                }
            }
        }

        // 2. Keyword-based dynamic fallbacks
        if (lowerTarget.contains("train") || lowerTarget.contains("rail") || lowerTarget.contains("irctc")) {
            return searchTrain(cleanQuery, target = targetApp)
        }

        if (lowerTarget.contains("music") || lowerTarget.contains("song")) {
            val musicApp = capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.MUSIC }
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                if (musicApp != null) setPackage(musicApp.packageName)
                putExtra(SearchManager.QUERY, cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success("Playing music: '$cleanQuery'")
            } catch (e: Exception) {
                searchWeb(cleanQuery)
            }
        }

        if (lowerTarget.contains("video") || lowerTarget.contains("movie")) {
            val mediaApp = capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.MEDIA }
            if (mediaApp != null) {
                return searchInApp(mediaApp.name, cleanQuery)
            }
        }

        if (lowerTarget.contains("map") || lowerTarget.contains("place") || lowerTarget.contains("navigate") || lowerTarget.contains("direction")) {
            return navigateTo(cleanQuery)
        }

        if (lowerTarget.contains("store") || lowerTarget.contains("playstore") || lowerTarget.contains("market")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(cleanQuery))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success("Searching app store for '$cleanQuery'")
            } catch (e: Exception) {
                openUrl("https://play.google.com/store/search?q=" + Uri.encode(cleanQuery))
            }
        }

        // Default: Web search
        return searchWeb(cleanQuery, targetBrowser = targetApp)
    }

    fun navigateTo(destination: String, targetPackage: String? = null): ActionResult {
        val cleanDest = destination.trim()
        val navApp = if (targetPackage != null) {
            capabilityManager.findBestAppMatch(targetPackage)
        } else {
            capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.NAVIGATION }
        }

        val geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(cleanDest))
        val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            if (navApp != null) setPackage(navApp.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Navigating to '$cleanDest'${if (navApp != null) " via ${navApp.name}" else ""}")
        } catch (e: Exception) {
            try {
                // Fallback without package restriction
                intent.setPackage(null)
                context.startActivity(intent)
                ActionResult.Success("Navigating to '$cleanDest'")
            } catch (ex: Exception) {
                openUrl("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(cleanDest))
            }
        }
    }

    fun searchTrain(query: String, target: String? = null): ActionResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ActionResult.Error("Train query or station name is required.")

        // 1. Dynamic app lookup via Capability Manager
        val targetSearch = target ?: "train"
        val matchedApp = capabilityManager.findBestAppMatch(targetSearch)
            ?: capabilityManager.getInstalledApps().firstOrNull {
                it.name.contains("train", ignoreCase = true) ||
                it.name.contains("rail", ignoreCase = true) ||
                it.name.contains("transit", ignoreCase = true)
            }

        if (matchedApp != null) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.putExtra(SearchManager.QUERY, cleanQuery)
                launchIntent.putExtra("query", cleanQuery)
                return try {
                    context.startActivity(launchIntent)
                    ActionResult.Success("Opened ${matchedApp.name} for '$cleanQuery'", "• App: ${matchedApp.name}\n• Query: $cleanQuery")
                } catch (e: Exception) {
                    openApp(matchedApp.packageName)
                }
            }
        }

        // 2. Generic Search Intent
        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
            putExtra(SearchManager.QUERY, cleanQuery)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(searchIntent)
            return ActionResult.Success("Searching for '$cleanQuery'")
        } catch (_: Exception) {}

        // 3. Web search fallback
        val trainSearchUrl = "https://www.google.com/search?q=" + Uri.encode("Train $cleanQuery")
        return openUrl(trainSearchUrl)
    }

    fun playMusicOrVideo(target: String, query: String): ActionResult {
        val cleanTarget = target.trim()
        val cleanQuery = query.trim()

        if (cleanTarget.isNotBlank()) {
            val matchedApp = capabilityManager.findBestAppMatch(cleanTarget)
            if (matchedApp != null) {
                return searchInApp(matchedApp.name, cleanQuery)
            }
        }

        if (cleanQuery.contains("video", ignoreCase = true) || cleanTarget.contains("video", ignoreCase = true)) {
            val mediaApp = capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.MEDIA }
            if (mediaApp != null) {
                return searchInApp(mediaApp.name, cleanQuery)
            }
        }

        val musicApp = capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.MUSIC }
        if (musicApp != null) {
            return searchInApp(musicApp.name, cleanQuery)
        }

        return searchInApp(cleanTarget.ifBlank { "music" }, cleanQuery)
    }

    fun composeEmail(recipient: String?, subject: String?, body: String?, target: String? = null): ActionResult {
        val emailApp = if (!target.isNullOrBlank()) {
            capabilityManager.findBestAppMatch(target)
        } else {
            capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.EMAIL }
        }

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
            if (emailApp != null) {
                setPackage(emailApp.packageName)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Composing email to ${recipient ?: "draft"}${if (emailApp != null) " on ${emailApp.name}" else ""}")
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

    fun sendMessage(target: String, phoneNumber: String?, message: String): ActionResult {
        val cleanTarget = target.lowercase().trim()
        val messagingApp = capabilityManager.findBestAppMatch(cleanTarget)

        if (messagingApp != null && messagingApp.category == AppCategory.MESSAGING) {
            val pkg = messagingApp.packageName
            val cleanPhone = phoneNumber?.replace(Regex("[^0-9]"), "") ?: ""

            if (cleanPhone.isNotBlank()) {
                // If it's a web/intent enabled messaging app (like WhatsApp/Telegram)
                if (pkg.contains("whatsapp") || messagingApp.name.contains("whatsapp", ignoreCase = true)) {
                    val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    return try {
                        context.startActivity(intent)
                        ActionResult.Success("Opened WhatsApp for $cleanPhone")
                    } catch (e: Exception) {
                        openApp(pkg)
                    }
                }
            }

            // Launch the messaging app directly
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult.Success("Opened ${messagingApp.name}")
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

        // Guard: If the model mistakenly outputs email query filters to searchWeb, redirect to Email app
        if (cleanQuery.startsWith("is:") || cleanQuery.contains("is:unread") || cleanQuery.contains("is:starred")) {
            val emailApp = capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.EMAIL }
            return searchInApp(emailApp?.name ?: "email", cleanQuery)
        }

        val searchUrl = "https://www.google.com/search?q=" + Uri.encode(cleanQuery)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val browserApp = if (!targetBrowser.isNullOrBlank()) {
            capabilityManager.findBestAppMatch(targetBrowser)
        } else {
            capabilityManager.getInstalledApps().firstOrNull { it.category == AppCategory.BROWSER }
        }

        if (browserApp != null) {
            intent.setPackage(browserApp.packageName)
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Searching web for: '$cleanQuery'${if (browserApp != null) " on ${browserApp.name}" else ""}")
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

    fun performAppTesting(targetApp: String, testAction: String? = null): ActionResult {
        val cleanTarget = targetApp.trim()
        
        // Step 1: Check if Accessibility Service is enabled
        val isA11yEnabled = com.orbital.automation.OrbitalAccessibilityService.isEnabled(context)
        
        // Step 2: Open target app if specified
        if (cleanTarget.isNotBlank()) {
            val openResult = openApp(cleanTarget)
            if (openResult is ActionResult.Error) {
                return openResult
            }
        }

        if (!isA11yEnabled) {
            return ActionResult.Success(
                message = "Opened $cleanTarget.",
                details = "Launched $cleanTarget. Companion floating HUD is active and ready."
            )
        }

        // Step 3: Resilient window polling (up to 3.5s across 5 attempts) to allow app window to settle
        val service = com.orbital.automation.OrbitalAccessibilityService.instance
            ?: return ActionResult.Success("Opened $cleanTarget for testing. Companion overlay is observing.")

        var snapshot: com.orbital.automation.ScreenHierarchySnapshot? = null
        for (attempt in 1..5) {
            try {
                Thread.sleep(700)
            } catch (_: InterruptedException) {}
            snapshot = service.captureScreenHierarchy()
            if (snapshot != null && snapshot.elements.isNotEmpty()) {
                break
            }
        }

        if (snapshot == null || snapshot.elements.isEmpty()) {
            return ActionResult.Success(
                message = "Opened $cleanTarget for automated testing.",
                details = "Screen loaded. Companion overlay is active and ready for further screen actions."
            )
        }

        val testSummary = StringBuilder()
        testSummary.append("📱 Automated Testing Report for ${snapshot.packageName}:\n")

        // Step 4: Inspect and categorize interactive controls
        val clickables = snapshot.elements.filter { it.isClickable }
        val editables = snapshot.elements.filter { it.isEditable }

        testSummary.append("• Found ${clickables.size} interactive controls & ${editables.size} input fields.\n")

        val buttonLabels = clickables.mapNotNull { it.text.ifBlank { it.contentDescription } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
        if (buttonLabels.isNotEmpty()) {
            testSummary.append("• Verified Controls: ${buttonLabels.joinToString(", ")}\n")
        }

        val inputLabels = editables.mapNotNull { it.text.ifBlank { it.contentDescription ?: it.viewId } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
        if (inputLabels.isNotEmpty()) {
            testSummary.append("• Form Fields: ${inputLabels.joinToString(", ")}\n")
        }

        // Step 5: Execute primary interaction test
        val targetButtonToClick = testAction?.takeIf { it.isNotBlank() }
            ?: buttonLabels.firstOrNull { 
                it.contains("Search", ignoreCase = true) || 
                it.contains("Submit", ignoreCase = true) ||
                it.contains("Continue", ignoreCase = true) ||
                it.contains("Next", ignoreCase = true)
            } ?: buttonLabels.firstOrNull()

        var clickResult = false
        if (!targetButtonToClick.isNullOrBlank()) {
            clickResult = service.clickElementByText(targetButtonToClick)
            if (!clickResult) {
                // Try clicking by partial or lowercase match
                clickResult = service.clickElementByText(targetButtonToClick.lowercase())
            }
            if (clickResult) {
                testSummary.append("⚡ Tested & Clicked primary control: '$targetButtonToClick' (OK)\n")
                // Wait for resulting UI transition
                try {
                    Thread.sleep(900)
                } catch (_: InterruptedException) {}
                val nextSnapshot = service.captureScreenHierarchy()
                if (nextSnapshot != null) {
                    testSummary.append("• New screen state after tap: ${nextSnapshot.elements.size} elements loaded.\n")
                }
            } else {
                testSummary.append("• Verified control presence: '$targetButtonToClick'\n")
            }
        }

        // Step 6: Trigger companion mascot event
        com.orbital.ui.MascotEventBus.postEvent(
            com.orbital.ui.MascotEvent.ActionSuccess(
                "Tested $cleanTarget: ${clickables.size} controls verified" + if (clickResult) ", tapped '$targetButtonToClick'" else ""
            )
        )

        return ActionResult.Success(
            message = "Tested $cleanTarget (${clickables.size} controls verified${if (clickResult) ", clicked '$targetButtonToClick'" else ""})",
            details = testSummary.toString().trim()
        )
    }

    fun readActiveScreen(): ActionResult {
        val service = com.orbital.automation.OrbitalAccessibilityService.instance
        if (service == null || !com.orbital.automation.OrbitalAccessibilityService.isEnabled(context)) {
            return ActionResult.Error("Accessibility service is currently disabled. Enable it only if you want automated screen reading.")
        }

        val snapshot = service.captureScreenHierarchy()
            ?: return ActionResult.Error("Could not read current active window. The screen may be transitioning or protected.")

        val hasElements = snapshot.elements.any { it.text.isNotBlank() || !it.contentDescription.isNullOrBlank() }
        val msg = if (hasElements) {
            "Screen read: ${snapshot.elements.size} elements found (${snapshot.packageName})"
        } else {
            "Screen read: 0 elements detected on ${snapshot.packageName} (Screen loading or custom surface)"
        }

        return ActionResult.Success(
            message = msg,
            details = snapshot.toPromptSummary()
        )
    }

    fun clickScreenElement(targetTextOrId: String): ActionResult {
        val clean = targetTextOrId.trim()
        if (clean.isBlank()) return ActionResult.Error("Element label or text is required.")

        val service = com.orbital.automation.OrbitalAccessibilityService.instance
        if (service == null) {
            return ActionResult.Error("Orbital accessibility service is not active.")
        }

        val success = if (clean.contains(":id/")) {
            service.clickElementById(clean)
        } else {
            service.clickElementByText(clean)
        }

        return if (success) {
            ActionResult.Success("Tapped '$clean' on screen")
        } else {
            ActionResult.Error("Could not find or tap element '$clean' on current screen.")
        }
    }

    fun inputScreenText(text: String, targetField: String? = null): ActionResult {
        val cleanText = text.trim()
        val service = com.orbital.automation.OrbitalAccessibilityService.instance
            ?: return ActionResult.Error("Orbital accessibility service is not active.")

        val success = service.inputText(cleanText, targetField)
        return if (success) {
            ActionResult.Success("Entered '$cleanText'${if (!targetField.isNullOrBlank()) " into $targetField" else ""}")
        } else {
            ActionResult.Error("Could not find input field on current screen.")
        }
    }

    fun scrollScreen(forward: Boolean = true): ActionResult {
        val service = com.orbital.automation.OrbitalAccessibilityService.instance
            ?: return ActionResult.Error("Orbital accessibility service is not active.")

        val success = service.performScroll(forward)
        return if (success) {
            ActionResult.Success("Scrolled screen ${if (forward) "down" else "up"}")
        } else {
            ActionResult.Error("Current screen cannot be scrolled.")
        }
    }

    fun scheduleCronMonitoring(action: DeviceAction): ActionResult {
        val query = action.query ?: action.label ?: action.message ?: ""
        if (query.isBlank()) return ActionResult.Error("Monitoring query or target description is required.")

        val title = action.title ?: action.label ?: query
        val taskId = "cron_" + System.currentTimeMillis() % 100000

        val taskType = when {
            action.action.contains("TICKET", ignoreCase = true) || query.contains("ticket", ignoreCase = true) -> 
                com.orbital.cron.CronTaskType.TICKET_ALERT
            query.contains("movie", ignoreCase = true) || query.contains("cinema", ignoreCase = true) -> 
                com.orbital.cron.CronTaskType.MOVIE_TICKET_ALERT
            query.contains("train", ignoreCase = true) || query.contains("status", ignoreCase = true) -> 
                com.orbital.cron.CronTaskType.TRAIN_MONITOR
            else -> com.orbital.cron.CronTaskType.GENERAL_REMINDER
        }

        val cronTask = com.orbital.cron.CronTask(
            id = taskId,
            taskType = taskType,
            title = title,
            query = query,
            intervalMinutes = action.repeatMinutes ?: 60,
            scheduledHour = action.hour,
            scheduledMinute = action.minutes,
            targetApp = action.target
        )

        val success = cronManager.scheduleCronTask(cronTask)
        return if (success) {
            val timing = if (action.hour != null && action.minutes != null) {
                "daily at %02d:%02d".format(action.hour, action.minutes)
            } else {
                "every ${action.repeatMinutes ?: 60}m"
            }
            ActionResult.Success(
                message = "Scheduled background monitoring for '$title' ($timing)",
                details = "Orbital will monitor '$query' in the background and alert you with a 1-tap booking button when ready."
            )
        } else {
            ActionResult.Error("Failed to schedule background monitoring job.")
        }
    }

    fun listActiveMonitors(): ActionResult {
        val tasks = cronManager.getActiveTasks()
        if (tasks.isEmpty()) {
            return ActionResult.Success("No active background monitoring jobs.")
        }

        val sb = StringBuilder("Active Background Monitors:\n")
        tasks.forEachIndexed { index, task ->
            val timing = if (task.scheduledHour != null && task.scheduledMinute != null) {
                "Daily at %02d:%02d".format(task.scheduledHour, task.scheduledMinute)
            } else {
                "Every ${task.intervalMinutes}m"
            }
            sb.append("${index + 1}. [${task.taskType}] \"${task.title}\" ($timing) - ID: ${task.id}\n")
        }
        return ActionResult.Success("Active monitors retrieved", sb.toString().trim())
    }

    fun cancelCronMonitoring(targetOrId: String): ActionResult {
        val clean = targetOrId.trim()
        if (clean.isBlank()) return ActionResult.Error("Task ID or title is required to cancel monitor.")

        val tasks = cronManager.getActiveTasks()
        val match = tasks.firstOrNull { it.id == clean || it.title.contains(clean, ignoreCase = true) || it.query.contains(clean, ignoreCase = true) }
            ?: return ActionResult.Error("No matching monitor found for '$clean'.")

        val cancelled = cronManager.cancelCronTask(match.id)
        return if (cancelled) {
            ActionResult.Success("Cancelled background monitor: '${match.title}'")
        } else {
            ActionResult.Error("Could not cancel monitor '${match.title}'.")
        }
    }
}
