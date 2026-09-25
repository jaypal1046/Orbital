package com.orbital.action

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log

enum class AppCategory(val displayName: String) {
    BROWSER("Browser / Web"),
    MESSAGING("Messaging & Chat"),
    SOCIAL("Social Media"),
    MEDIA("Video & Entertainment"),
    MUSIC("Music & Audio"),
    NAVIGATION("Maps & Navigation"),
    EMAIL("Email & Office"),
    UTILITY("Utility & Tools"),
    CAMERA("Camera & Photos"),
    SHOPPING("Shopping"),
    FINANCE("Finance & Banking"),
    SYSTEM("System & Settings"),
    GENERAL("App")
}

data class DynamicAppInfo(
    val name: String,
    val packageName: String,
    val category: AppCategory,
    val capabilities: List<String>,
    val actionHint: String
)

class AppCapabilityManager(private val context: Context) {

    companion object {
        private const val TAG = "AppCapabilityManager"
        @Volatile
        private var cachedApps: List<DynamicAppInfo>? = null
        private var lastCacheTime: Long = 0
    }

    /**
     * Scans and indexes all installed launchable applications on the user's phone,
     * classifying their capabilities and intent triggers.
     */
    fun getInstalledApps(forceRefresh: Boolean = false): List<DynamicAppInfo> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedApps != null && (now - lastCacheTime < 60_000)) {
            return cachedApps!!
        }

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList: List<ResolveInfo> = try {
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query launcher activities", e)
            emptyList()
        }

        val result = mutableListOf<DynamicAppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg in seenPackages) continue
            seenPackages.add(pkg)

            val label = resolveInfo.loadLabel(pm).toString().trim()
            val category = detectCategory(pkg, label, resolveInfo)
            val capabilities = detectCapabilities(pkg, label, category)
            val actionHint = buildActionHint(label, category, pkg)

            result.add(
                DynamicAppInfo(
                    name = label,
                    packageName = pkg,
                    category = category,
                    capabilities = capabilities,
                    actionHint = actionHint
                )
            )
        }

        // Sort alphabetically
        val sorted = result.sortedBy { it.name.lowercase() }
        cachedApps = sorted
        lastCacheTime = now
        return sorted
    }

    /**
     * Builds a rich, structured context block for the LLM system prompt
     * so the AI knows every app on the user's phone and what it can do.
     */
    fun buildDeviceCapabilitiesPrompt(): String {
        val apps = getInstalledApps()
        if (apps.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n=== DYNAMIC DEVICE APPS & CAPABILITIES (Real Installed Apps on User's Phone) ===\n")
        
        // Group by category
        val grouped = apps.groupBy { it.category }
        grouped.forEach { (cat, appList) ->
            sb.append("\n[${cat.displayName}]:\n")
            appList.take(10).forEach { app ->
                sb.append("  • ${app.name} (${app.packageName}) -> ${app.actionHint}\n")
            }
        }
        sb.append("===============================================================================\n")
        return sb.toString()
    }

    /**
     * Finds the best matching installed app given a query string.
     */
    fun findBestAppMatch(query: String): DynamicAppInfo? {
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("\\b(app|application|the|please|open|launch)\\b"), "")
            .trim()
            .ifEmpty { query.trim().lowercase() }

        val apps = getInstalledApps()

        // 1. Direct package match
        apps.firstOrNull { it.packageName.equals(query.trim(), ignoreCase = true) }?.let { return it }

        // 2. Exact label match
        apps.firstOrNull { it.name.equals(cleanQuery, ignoreCase = true) || it.name.equals(query.trim(), ignoreCase = true) }?.let { return it }

        // 3. Normalized label contains query
        apps.firstOrNull { it.name.lowercase().contains(cleanQuery) || cleanQuery.contains(it.name.lowercase()) }?.let { return it }

        // 4. Category matches
        when (cleanQuery) {
            "browser", "internet", "web" -> apps.firstOrNull { it.category == AppCategory.BROWSER }?.let { return it }
            "music", "song", "audio" -> apps.firstOrNull { it.category == AppCategory.MUSIC }?.let { return it }
            "video", "videos", "watch" -> apps.firstOrNull { it.category == AppCategory.MEDIA }?.let { return it }
            "chat", "message", "messaging" -> apps.firstOrNull { it.category == AppCategory.MESSAGING }?.let { return it }
            "maps", "map", "navigation", "gps" -> apps.firstOrNull { it.category == AppCategory.NAVIGATION }?.let { return it }
            "mail", "email" -> apps.firstOrNull { it.category == AppCategory.EMAIL }?.let { return it }
            "camera", "cam", "photo" -> apps.firstOrNull { it.category == AppCategory.CAMERA }?.let { return it }
            "calculator", "calc" -> apps.firstOrNull { it.category == AppCategory.UTILITY && it.name.contains("calc", ignoreCase = true) }?.let { return it }
            "settings", "setting" -> apps.firstOrNull { it.category == AppCategory.SYSTEM || it.packageName.contains("settings") }?.let { return it }
        }

        // 5. Keyword in package name
        apps.firstOrNull { it.packageName.lowercase().contains(cleanQuery) }?.let { return it }

        return null
    }

    private fun detectCategory(pkg: String, label: String, resolveInfo: ResolveInfo): AppCategory {
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()

        // Check Android ApplicationInfo category if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appCategory = resolveInfo.activityInfo.applicationInfo?.category
            when (appCategory) {
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.MEDIA
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.CAMERA
                ApplicationInfo.CATEGORY_MAPS -> return AppCategory.NAVIGATION
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.EMAIL
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            }
        }

        return when {
            lowerPkg.contains("chrome") || lowerPkg.contains("firefox") || lowerPkg.contains("browser") || lowerPkg.contains("opera") || lowerLabel.contains("browser") -> AppCategory.BROWSER
            lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") || lowerPkg.contains("messenger") || lowerPkg.contains("messaging") || lowerPkg.contains("signal") || lowerLabel.contains("messages") -> AppCategory.MESSAGING
            lowerPkg.contains("instagram") || lowerPkg.contains("twitter") || lowerPkg.contains("tiktok") || lowerPkg.contains("facebook") || lowerPkg.contains("reddit") || lowerPkg.contains("linkedin") -> AppCategory.SOCIAL
            lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("primevideo") || lowerPkg.contains("hotstar") || lowerPkg.contains("disney") || lowerPkg.contains("twitch") -> AppCategory.MEDIA
            lowerPkg.contains("spotify") || lowerPkg.contains("music") || lowerPkg.contains("sound") || lowerPkg.contains("podcast") || lowerPkg.contains("deezer") -> AppCategory.MUSIC
            lowerPkg.contains("maps") || lowerPkg.contains("waze") || lowerPkg.contains("uber") || lowerPkg.contains("ola") || lowerPkg.contains("transit") -> AppCategory.NAVIGATION
            lowerPkg.contains("gmail") || lowerPkg.contains("mail") || lowerPkg.contains("outlook") || lowerPkg.contains("docs") || lowerPkg.contains("sheets") || lowerPkg.contains("notion") || lowerPkg.contains("notes") || lowerPkg.contains("drive") -> AppCategory.EMAIL
            lowerPkg.contains("camera") || lowerPkg.contains("gallery") || lowerPkg.contains("photos") -> AppCategory.CAMERA
            lowerPkg.contains("amazon") || lowerPkg.contains("flipkart") || lowerPkg.contains("ebay") || lowerPkg.contains("shop") -> AppCategory.SHOPPING
            lowerPkg.contains("paytm") || lowerPkg.contains("gpay") || lowerPkg.contains("phonepe") || lowerPkg.contains("bank") || lowerPkg.contains("wallet") -> AppCategory.FINANCE
            lowerPkg.contains("settings") || lowerPkg.contains("systemui") || lowerPkg.contains("launcher") || lowerPkg.contains("dialer") -> AppCategory.SYSTEM
            lowerPkg.contains("calculator") || lowerPkg.contains("clock") || lowerPkg.contains("files") || lowerPkg.contains("calendar") || lowerPkg.contains("recorder") -> AppCategory.UTILITY
            else -> AppCategory.GENERAL
        }
    }

    private fun detectCapabilities(pkg: String, label: String, category: AppCategory): List<String> {
        val caps = mutableListOf("OPEN")
        when (category) {
            AppCategory.BROWSER -> caps.addAll(listOf("OPEN_URL", "SEARCH_WEB"))
            AppCategory.MEDIA -> caps.addAll(listOf("SEARCH_VIDEO", "PLAY_MEDIA"))
            AppCategory.MUSIC -> caps.addAll(listOf("PLAY_MUSIC", "SEARCH_SONG"))
            AppCategory.MESSAGING -> caps.addAll(listOf("SEND_MESSAGE", "SHARE_TEXT"))
            AppCategory.NAVIGATION -> caps.addAll(listOf("SEARCH_LOCATION", "NAVIGATE_TO"))
            AppCategory.EMAIL -> caps.addAll(listOf("COMPOSE_EMAIL", "SHARE_TEXT"))
            AppCategory.CAMERA -> caps.addAll(listOf("TAKE_PHOTO", "RECORD_VIDEO"))
            AppCategory.UTILITY -> caps.addAll(listOf("CALCULATE", "SET_TIMER", "VIEW_FILES"))
            AppCategory.SYSTEM -> caps.addAll(listOf("CONFIGURE_SETTINGS", "DEVICE_STATUS"))
            AppCategory.SOCIAL -> caps.addAll(listOf("SEARCH_SOCIAL", "SHARE_POST"))
            AppCategory.SHOPPING -> caps.addAll(listOf("SEARCH_PRODUCTS"))
            else -> {}
        }
        return caps
    }

    private fun buildActionHint(label: String, category: AppCategory, pkg: String): String {
        return when (category) {
            AppCategory.BROWSER -> "Search web or open URLs directly"
            AppCategory.MEDIA -> "Search & play videos, streams"
            AppCategory.MUSIC -> "Search & stream songs, playlists"
            AppCategory.MESSAGING -> "Send direct chat messages, open contacts"
            AppCategory.NAVIGATION -> "Directions, GPS navigation, place searches"
            AppCategory.EMAIL -> "Compose emails, open drafts"
            AppCategory.CAMERA -> "Capture photos, scan documents"
            AppCategory.UTILITY -> "Compute, manage files, set alarms"
            AppCategory.SYSTEM -> "Device hardware toggles, system settings"
            AppCategory.SOCIAL -> "Browse feeds, search accounts"
            AppCategory.SHOPPING -> "Search items and prices"
            else -> "Launch and interact with $label"
        }
    }
}
