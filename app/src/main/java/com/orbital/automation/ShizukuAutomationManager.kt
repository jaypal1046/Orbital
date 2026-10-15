package com.orbital.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import rikka.shizuku.Shizuku
import java.io.File
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object ShizukuAutomationManager {

    private const val TAG = "ShizukuAutomation"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_REQUEST_CODE = 4821

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isRunning()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun isReady(): Boolean = isRunning() && hasPermission()

    fun requestPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        try {
            if (isRunning() && !hasPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    fun openShizuku(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            // Open GitHub or Play Store for Shizuku installation
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(storeIntent)
            } catch (_: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        // Backend 1: Shizuku (Rootless ADB Wireless)
        if (isRunning() && hasPermission()) {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                return@withContext ShellResult(exitCode, stdout.trim(), stderr.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku execution error: $command", e)
            }
        }

        // Backend 2: Direct local shell / daemon process
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                return@withContext ShellResult(exitCode, stdout.trim(), stderr.trim())
            }
        } catch (_: Exception) {}

        // Backend 3: Root SU execution
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            return@withContext ShellResult(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Execution error")
        }
    }

    suspend fun executeDynamicScript(context: Context, scriptContent: String): ShellResult = withContext(Dispatchers.IO) {
        val scriptFile = File(context.filesDir, "dynamic_script_${System.currentTimeMillis()}.sh")
        try {
            scriptFile.writeText("#!/system/bin/sh\n$scriptContent\n")
            scriptFile.setExecutable(true, false)
            exec("sh ${scriptFile.absolutePath}")
        } finally {
            try { scriptFile.delete() } catch (_: Exception) {}
        }
    }


    suspend fun tap(x: Int, y: Int): Boolean {
        val result = exec("input tap $x $y")
        return result.isSuccess
    }

    suspend fun typeText(text: String): Boolean {
        if (text.isEmpty()) return true
        // Android input text requires spaces to be %s and special shell chars escaped
        val escaped = text.replace(" ", "%s")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\'", "\\\'")
            .replace("&", "\\&")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace(";", "\\;")
            .replace("|", "\\|")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("$", "\\$")
        val result = exec("input text \"$escaped\"")
        return result.isSuccess
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean {
        val result = exec("input swipe $x1 $y1 $x2 $y2 $durationMs")
        return result.isSuccess
    }

    suspend fun pressKey(key: String): Boolean {
        val cmd = when (key.uppercase().trim()) {
            "BACK" -> "input keyevent 4"
            "HOME" -> "input keyevent 3"
            "RECENTS", "APP_SWITCH" -> "input keyevent 187"
            "NOTIFICATIONS" -> "cmd statusbar expand-notifications"
            "QUICK_SETTINGS" -> "cmd statusbar expand-settings"
            "ENTER" -> "input keyevent 66"
            "TAB" -> "input keyevent 61"
            "DELETE", "BACKSPACE" -> "input keyevent 67"
            "VOLUME_UP" -> "input keyevent 24"
            "VOLUME_DOWN" -> "input keyevent 25"
            else -> null
        } ?: return false
        val result = exec(cmd)
        return result.isSuccess
    }

    suspend fun captureScreenshot(targetFile: File): Boolean {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tempPath = "/data/local/tmp/orbital_screenshot_${System.currentTimeMillis()}.png"
        val capResult = exec("screencap -p $tempPath")
        if (!capResult.isSuccess) return false

        // Pull from tempPath to app storage
        val copyResult = exec("cp $tempPath ${targetFile.absolutePath} && rm $tempPath")
        return copyResult.isSuccess || targetFile.exists() && targetFile.length() > 0
    }

    suspend fun dumpScreenHierarchy(): ScreenHierarchySnapshot? = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext null
        val dumpPath = "/data/local/tmp/orbital_dump.xml"
        val dumpCmd = exec("uiautomator dump $dumpPath && cat $dumpPath && rm $dumpPath")
        if (!dumpCmd.isSuccess || dumpCmd.stdout.isBlank()) {
            Log.w(TAG, "uiautomator dump failed: ${dumpCmd.stderr}")
            return@withContext null
        }

        val xmlContent = dumpCmd.stdout.substringAfter("<?xml")
        val fullXml = if (xmlContent.isNotEmpty()) "<?xml$xmlContent" else dumpCmd.stdout

        parseUiHierarchyXml(fullXml)
    }

    private fun parseUiHierarchyXml(xml: String): ScreenHierarchySnapshot? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            val elements = mutableListOf<UIElement>()
            var primaryPackage = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val pkg = parser.getAttributeValue(null, "package") ?: ""
                    if (pkg.isNotBlank() && primaryPackage.isBlank() && pkg != "com.android.systemui") {
                        primaryPackage = pkg
                    }

                    val text = parser.getAttributeValue(null, "text") ?: ""
                    val desc = parser.getAttributeValue(null, "content-desc")
                    val resId = parser.getAttributeValue(null, "resource-id")
                    val className = parser.getAttributeValue(null, "class") ?: ""
                    val isClickable = parser.getAttributeValue(null, "clickable") == "true"
                    val isScrollable = parser.getAttributeValue(null, "scrollable") == "true"
                    val isPassword = parser.getAttributeValue(null, "password") == "true"
                    val isEditable = className.contains("EditText", ignoreCase = true) ||
                            (parser.getAttributeValue(null, "focusable") == "true" && className.contains("Text", ignoreCase = true))

                    val boundsStr = parser.getAttributeValue(null, "bounds") ?: ""
                    val bounds = parseBounds(boundsStr)

                    if (text.isNotBlank() || !desc.isNullOrBlank() || isClickable || isEditable || isScrollable) {
                        elements.add(
                            UIElement(
                                text = text.trim(),
                                contentDescription = desc?.trim(),
                                viewId = resId?.takeIf { it.isNotBlank() },
                                className = className,
                                isClickable = isClickable,
                                isScrollable = isScrollable,
                                isEditable = isEditable,
                                bounds = bounds,
                                hintText = null,
                                isPassword = isPassword
                            )
                        )
                    }
                }
                eventType = parser.next()
            }

            ScreenHierarchySnapshot(
                packageName = primaryPackage.ifBlank { "unknown" },
                activityTitle = null,
                elements = elements
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse uiautomator XML", e)
            null
        }
    }

    private fun parseBounds(boundsStr: String): Rect {
        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = regex.find(boundsStr)
        return if (match != null) {
            val (l, t, r, b) = match.destructured
            Rect(l.toInt(), t.toInt(), r.toInt(), b.toInt())
        } else {
            Rect(0, 0, 0, 0)
        }
    }
}
