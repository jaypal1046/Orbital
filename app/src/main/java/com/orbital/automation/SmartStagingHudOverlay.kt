package com.orbital.automation

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating On-Screen Smart Staging HUD Overlay.
 * 
 * Rules:
 * - ZERO AccessibilityService (100% Play Store compliant).
 * - ZERO Notification permissions.
 * - Displays a sleek, non-blocking floating pill at the bottom of the screen
 *   above the active email client (Outlook, Gmail, etc.).
 * - Actions:
 *   - [ 🚀 Send Now ]: Taps send button in active client.
 *   - [ ✏️ Refine Draft ]: Quick edit/refinement with live re-injection.
 *   - [ ✕ Dismiss ]: Closes HUD, leaves staged draft intact.
 */
object SmartStagingHudOverlay {

    private var activeView: View? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = activeView != null

    /**
     * Shows the floating Smart Staging HUD overlay.
     */
    fun show(
        context: Context,
        clientName: String,
        initialDraft: String,
        onSend: suspend () -> Boolean,
        onRefine: suspend (String) -> Boolean,
        onDismiss: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        // Safety check: Needs overlay permission on Android M+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(
                appContext,
                "✉️ Smart Staging: Draft staged in $clientName",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Dismiss previous HUD if active
        dismiss()

        val density = appContext.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        // Root container (Horizontal floating card)
        val rootLayout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))

            // Glassmorphic rounded background
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121526"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#313A5E"))
            }
            elevation = dp(12).toFloat()
        }

        // 1. Header Bar: Title + Status + Dismiss 'X'
        val headerLayout = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(appContext).apply {
            text = "🚀 Orbital Smart Staging ($clientName)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(appContext).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                dismiss()
                onDismiss()
            }
        }

        headerLayout.addView(titleView)
        headerLayout.addView(closeBtn)
        rootLayout.addView(headerLayout)

        // 2. Draft Snippet preview
        val snippetView = TextView(appContext).apply {
            val preview = initialDraft.replace("\n", " ").trim()
            text = if (preview.length > 70) "\"${preview.take(67)}...\"" else "\"$preview\""
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(4), 0, dp(8))
        }
        rootLayout.addView(snippetView)

        // 3. Refinement Inline Input (Initially hidden)
        val refineContainer = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 0, 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val refineInput = EditText(appContext).apply {
            hint = "Tweak text (e.g. Make it shorter, add details)..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#090C16"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#2B3354"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        }

        val applyRefineBtn = Button(appContext).apply {
            text = "Apply"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669")) // Emerald
                cornerRadius = dp(8).toFloat()
            }
        }

        refineContainer.addView(refineInput)
        refineContainer.addView(applyRefineBtn)
        rootLayout.addView(refineContainer)

        // 4. Action Buttons Bar: [ Send Now ] [ Refine Draft ] [ Dismiss ]
        val actionsLayout = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val sendBtn = Button(appContext).apply {
            text = "🚀 Send Now"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6366F1")) // Indigo
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.2f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                isEnabled = false
                text = "Sending..."
                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) { onSend() }
                    if (success) {
                        Toast.makeText(appContext, "✅ Email sent successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(appContext, "Tap Send in $clientName to complete", Toast.LENGTH_SHORT).show()
                    }
                    dismiss()
                }
            }
        }

        val refineBtn = Button(appContext).apply {
            text = "✏️ Refine"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E243D"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#374169"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                if (refineContainer.visibility == View.VISIBLE) {
                    refineContainer.visibility = View.GONE
                } else {
                    refineContainer.visibility = View.VISIBLE
                    refineInput.setText(initialDraft)
                    refineInput.requestFocus()
                }
            }
        }

        applyRefineBtn.setOnClickListener {
            val updated = refineInput.text.toString().trim()
            if (updated.isNotBlank()) {
                applyRefineBtn.isEnabled = false
                applyRefineBtn.text = "..."
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.IO) { onRefine(updated) }
                    snippetView.text = "\"${updated.replace("\n", " ").take(67)}...\""
                    refineContainer.visibility = View.GONE
                    applyRefineBtn.isEnabled = true
                    applyRefineBtn.text = "Apply"
                    Toast.makeText(appContext, "Draft updated in compose window", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val dismissBtn = Button(appContext).apply {
            text = "Keep Draft"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#171B2D"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#262E48"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 0.9f)
            setOnClickListener {
                dismiss()
                onDismiss()
            }
        }

        actionsLayout.addView(sendBtn)
        actionsLayout.addView(refineBtn)
        actionsLayout.addView(dismissBtn)
        rootLayout.addView(actionsLayout)

        // WindowManager Params: Bottom floating pill, non-blocking
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(24) // Float 24dp above screen bottom
        }

        try {
            wm.addView(rootLayout, layoutParams)
            activeView = rootLayout
        } catch (e: Exception) {
            Toast.makeText(appContext, "Smart Staging: Draft staged in $clientName", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Safely dismisses the floating HUD overlay.
     */
    fun dismiss() {
        activeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            activeView = null
        }
    }
}
