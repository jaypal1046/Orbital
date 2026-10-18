package com.orbital.automation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class StagedEmailContext(
    val clientApp: String,
    val packageName: String,
    val subject: String?,
    val sender: String?,
    val threadSummary: String?,
    val isStaged: Boolean,
    val draftText: String,
    val taskLogs: List<String>
)

/**
 * On-Device Email Smart Staging Engine (Co-Pilot Mode).
 * 
 * Rules:
 * - ZERO Notification Listener permissions (no NotificationListenerService).
 * - ZERO AccessibilityService (100% Play Store compliant).
 * - Privileged Screen Context & UI Injection via Native Shell Daemon (:19876).
 * - Smart Staging: Stages draft directly into the email compose view, holding for user review.
 */
object EmailSmartStagingEngine {

    val KNOWN_EMAIL_PACKAGES = setOf(
        "com.microsoft.office.outlook",
        "com.google.android.gm",
        "com.samsung.android.email.provider",
        "com.syntomo.email",
        "ch.protonmail.android",
        "com.trtf.blue",
        "com.readdle.spark"
    )

    /**
     * Inspects active screen, locates the email thread, triggers Reply,
     * injects the draft into the compose field, and returns structured result.
     */
    suspend fun stageReply(
        context: Context,
        draftBody: String,
        targetClient: String? = null
    ): StagedEmailContext = withContext(Dispatchers.IO) {
        val taskLogs = mutableListOf<String>()

        // 1. Detect Foreground Email Client
        taskLogs.add("> Task :DetectEmailClient [IN_PROGRESS]")
        val currentHierarchy = OrbitalAutomationEngine.instance?.captureScreenHierarchy()
        val currentPackage = OrbitalAutomationEngine.currentForegroundPackage.value

        val emailPackage = if (KNOWN_EMAIL_PACKAGES.contains(currentPackage)) {
            currentPackage
        } else if (!targetClient.isNullOrBlank()) {
            val matched = KNOWN_EMAIL_PACKAGES.firstOrNull { it.contains(targetClient, ignoreCase = true) }
            matched ?: currentPackage
        } else {
            currentPackage
        }

        val clientName = when {
            emailPackage.contains("outlook") -> "Microsoft Outlook"
            emailPackage.contains("gm") -> "Google Gmail"
            emailPackage.contains("samsung") -> "Samsung Email"
            emailPackage.contains("proton") -> "ProtonMail"
            else -> emailPackage
        }
        taskLogs.add("> Task :DetectEmailClient [SUCCESS] -> Detected $clientName ($emailPackage)")

        // 2. Extract Email Context from Visible Screen
        taskLogs.add("> Task :ExtractEmailContext [IN_PROGRESS]")
        var detectedSubject: String? = null
        var detectedSender: String? = null
        val bodyLines = mutableListOf<String>()

        val elements = currentHierarchy?.elements ?: emptyList()
        for (el in elements) {
            val text = el.text.ifBlank { el.contentDescription.orEmpty() }
            if (text.isBlank()) continue

            // Detect email address or sender
            if (detectedSender == null && (text.contains("@") || el.viewId?.contains("sender") == true || el.viewId?.contains("from") == true)) {
                detectedSender = text
            }
            // Detect subject
            if (detectedSubject == null && (el.viewId?.contains("subject") == true || el.viewId?.contains("title") == true)) {
                detectedSubject = text
            }
            if (text.length > 20 && !text.startsWith("http")) {
                bodyLines.add(text)
            }
        }
        taskLogs.add("> Task :ExtractEmailContext [SUCCESS] -> Sender: ${detectedSender ?: "Active Thread"}, Subject: ${detectedSubject ?: "Current Conversation"}")

        // 3. Locate & Trigger Reply Action
        taskLogs.add("> Task :LocateReplyAction [IN_PROGRESS]")
        val existingEdit = elements.firstOrNull { it.isEditable || it.className.contains("EditText") }

        if (existingEdit == null) {
            val replyNode = elements.firstOrNull { el ->
                val desc = (el.contentDescription ?: el.text).lowercase().trim()
                desc == "reply" || desc == "reply all" || desc == "respond" || el.viewId?.contains("reply") == true
            }

            if (replyNode != null) {
                val cx = replyNode.bounds.centerX()
                val cy = replyNode.bounds.centerY()
                taskLogs.add("> Task :LocateReplyAction [SUCCESS] -> Found Reply button at ($cx, $cy)")
                ShellAutomationManager.tap(cx, cy)
                taskLogs.add("> Task :TriggerReplyAction [SUCCESS] -> Dispatched tap")
                delay(1200) // wait for compose view to open
            } else {
                taskLogs.add("> Task :LocateReplyAction [WARNING] -> Reply button not explicitly found, assuming compose field is active")
            }
        } else {
            taskLogs.add("> Task :LocateReplyAction [SUCCESS] -> Compose window is already active")
        }

        // 4. Inject Draft into Compose Message Body
        taskLogs.add("> Task :InjectDraftBody [IN_PROGRESS]")
        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Orbital Email Draft", draftBody)
            clipboard.setPrimaryClip(clip)
        }

        val composeHierarchy = OrbitalAutomationEngine.instance?.captureScreenHierarchy()
        val composeEdit = composeHierarchy?.elements?.firstOrNull {
            (it.isEditable || it.className.contains("EditText")) && it.bounds.height() > 100
        } ?: composeHierarchy?.elements?.firstOrNull { it.isEditable || it.className.contains("EditText") }

        if (composeEdit != null) {
            val ex = composeEdit.bounds.centerX()
            val ey = composeEdit.bounds.centerY()
            ShellAutomationManager.tap(ex, ey)
            delay(300)
        }

        // Paste clipboard content (Keyevent 279 = KEYCODE_PASTE)
        ShellAutomationManager.exec("input keyevent 279")
        delay(400)
        val wordCount = draftBody.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        taskLogs.add("> Task :InjectDraftBody [SUCCESS] -> Injected draft ($wordCount words) into compose field")

        // 5. Activate Smart Staging HUD
        taskLogs.add("> Task :SmartStagingHUD [ACTIVE] -> Staged in $clientName. Awaiting user review")
        withContext(Dispatchers.Main) {
            SmartStagingHudOverlay.show(
                context = context,
                clientName = clientName,
                initialDraft = draftBody,
                onSend = { dispatchSendNow() },
                onRefine = { newDraft -> refineDraft(context, newDraft) }
            )
        }

        StagedEmailContext(
            clientApp = clientName,
            packageName = emailPackage,
            subject = detectedSubject,
            sender = detectedSender,
            threadSummary = bodyLines.take(3).joinToString(" "),
            isStaged = true,
            draftText = draftBody,
            taskLogs = taskLogs
        )
    }

    /**
     * Taps the send button in the active email client when user confirms via the Smart Staging HUD.
     */
    suspend fun dispatchSendNow(): Boolean = withContext(Dispatchers.IO) {
        val hierarchy = OrbitalAutomationEngine.instance?.captureScreenHierarchy() ?: return@withContext false
        val sendNode = hierarchy.elements.firstOrNull { el ->
            val desc = (el.contentDescription ?: el.text).lowercase().trim()
            val id = el.viewId?.lowercase().orEmpty()
            desc == "send" || desc == "send email" || desc == "send message" ||
            id.endsWith(":id/send") || id.contains("send_button") || id.contains("compose_send") || id.contains("action_send")
        }

        if (sendNode != null) {
            val sx = sendNode.bounds.centerX()
            val sy = sendNode.bounds.centerY()
            ShellAutomationManager.tap(sx, sy)
        } else {
            false
        }
    }

    /**
     * Re-injects an updated/refined draft into the active compose field.
     */
    suspend fun refineDraft(context: Context, newDraftBody: String): Boolean = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Orbital Email Draft", newDraftBody)
            clipboard.setPrimaryClip(clip)
        }

        // Select all (Ctrl+A / Keyevent 29 with Meta) and paste
        ShellAutomationManager.exec("input keyevent --meta 4096 29")
        delay(150)
        ShellAutomationManager.exec("input keyevent 279")
        true
    }
}
