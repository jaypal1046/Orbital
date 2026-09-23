package com.orbital.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.orbital.R
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.orbital.ACTION_START"
        const val ACTION_STOP = "com.orbital.ACTION_STOP"
        const val ACTION_UPDATE_CONNECTION_STATUS = "com.orbital.ACTION_UPDATE_CONNECTION_STATUS"
        const val ACTION_UPDATE_VOICE_STATUS = "com.orbital.ACTION_UPDATE_VOICE_STATUS"
        const val ACTION_UPDATE_CHARACTER = "com.orbital.ACTION_UPDATE_CHARACTER"
        private const val CHANNEL_ID = "OrbitalOverlayChannel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var connectionStatusText: TextView
    private lateinit var connectionIndicator: View
    private lateinit var voiceStatusIndicator: View
    private lateinit var characterImage: ImageView
    private var currentStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
    private var currentVoiceStatus: String = "idle"
    private var currentCharacter: String = "aether"

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let {
                when (it) {
                    ACTION_UPDATE_CONNECTION_STATUS -> {
                        val status = intent.getStringExtra("status") ?: "disconnected"
                        updateConnectionStatus(status)
                    }
                    ACTION_UPDATE_VOICE_STATUS -> {
                        val status = intent.getStringExtra("status") ?: "idle"
                        updateVoiceStatus(status)
                    }
                    ACTION_UPDATE_CHARACTER -> {
                        val character = intent.getStringExtra("character") ?: "aether"
                        updateCharacter(character)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        registerReceiver(statusReceiver, IntentFilter().apply {
            addAction(ACTION_UPDATE_CONNECTION_STATUS)
            addAction(ACTION_UPDATE_VOICE_STATUS)
            addAction(ACTION_UPDATE_CHARACTER)
        })

        // Load the selected character from storage
        val secureStorage = SecureStorage(this)
        currentCharacter = secureStorage.getSelectedCharacter() ?: "aether"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startOverlay()
            ACTION_STOP -> stopOverlay()
        }
        return START_STICKY
    }

    private fun startOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        connectionStatusText = overlayView.findViewById(R.id.connectionStatusText)
        connectionIndicator = overlayView.findViewById(R.id.connectionIndicator)
        voiceStatusIndicator = overlayView.findViewById(R.id.voiceStatusIndicator)
        characterImage = overlayView.findViewById(R.id.characterImage)

        windowManager.addView(overlayView, params)

        overlayView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                }
                return false
            }
        })

        // Initial status updates
        updateConnectionStatus("disconnected")
        updateVoiceStatus("idle")
        updateCharacter(currentCharacter)
    }

    private fun stopOverlay() {
        try {
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
                unregisterReceiver(statusReceiver)
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun updateConnectionStatus(status: String) {
        when (status.lowercase()) {
            "connected" -> {
                currentStatus = ConnectionStatus.CONNECTED
                connectionStatusText.text = getString(R.string.status_connected)
                connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.connection_connected))
                connectionIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.connection_connected))
            }
            "connecting" -> {
                currentStatus = ConnectionStatus.CONNECTING
                connectionStatusText.text = getString(R.string.status_connecting)
                connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.connection_connecting))
                connectionIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.connection_connecting))
            }
            "error" -> {
                currentStatus = ConnectionStatus.ERROR
                connectionStatusText.text = getString(R.string.status_error)
                connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.connection_error))
                connectionIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.connection_error))
            }
            else -> {
                currentStatus = ConnectionStatus.DISCONNECTED
                connectionStatusText.text = getString(R.string.status_disconnected)
                connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.connection_disconnected))
                connectionIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.connection_disconnected))
            }
        }
    }

    private fun updateVoiceStatus(status: String) {
        currentVoiceStatus = status
        when (status) {
            "listening" -> {
                voiceStatusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.voice_listening))
            }
            "speaking" -> {
                voiceStatusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.voice_speaking))
            }
            "error" -> {
                voiceStatusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.voice_error))
            }
            else -> {
                voiceStatusIndicator.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    private fun updateCharacter(character: String) {
        currentCharacter = character
        val drawableRes = when (character) {
            "aether" -> R.drawable.ic_character_aether
            "lumy" -> R.drawable.ic_character_lumy
            "volo" -> R.drawable.ic_character_volo
            else -> R.drawable.ic_character_aether
        }
        characterImage.setImageResource(drawableRes)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Orbital Companion",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Orbital Companion")
            .setContentText("Floating AI assistant is active")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        try {
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
                unregisterReceiver(statusReceiver)
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}