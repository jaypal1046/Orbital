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
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.app.usage.UsageStatsManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.orbital.R
import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceActionExecutor
import com.orbital.action.NextStepSuggester
import com.orbital.chat.ChatEngine
import com.orbital.data.ChatChoice
import com.orbital.data.ChatMessage
import com.orbital.data.ChatRequest
import com.orbital.data.ChatResponse
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import com.orbital.data.ServerConfig
import com.orbital.power.PowerAwareScheduler
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.orbital.ACTION_START"
        const val ACTION_STOP = "com.orbital.ACTION_STOP"
        const val ACTION_UPDATE_CONNECTION_STATUS = "com.orbital.ACTION_UPDATE_CONNECTION_STATUS"
        const val ACTION_UPDATE_VOICE_STATUS = "com.orbital.ACTION_UPDATE_VOICE_STATUS"
        const val ACTION_UPDATE_CHARACTER = "com.orbital.ACTION_UPDATE_CHARACTER"
        const val ACTION_PERMISSION_REVOKED = "com.orbital.ACTION_PERMISSION_REVOKED"
        private const val CHANNEL_ID = "OrbitalOverlayChannel"
        private const val NOTIFICATION_ID = 1
        private const val FOREGROUND_SERVICE_TYPE = android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    // Injected via Hilt
    @Inject
    lateinit var chatEngine: ChatEngine

    @Inject
    lateinit var llmRepository: LlmRepository

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var bubbleAvatarContainer: FrameLayout
    private lateinit var chatPanel: View
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var chatSuggestionsContainer: LinearLayout
    private lateinit var chatScrollView: android.widget.ScrollView
    private lateinit var chatInputEditText: android.widget.EditText
    private lateinit var chatSendButton: View
    private lateinit var chatMicButton: View
    private lateinit var chatExpandButton: View
    private lateinit var chatCloseButton: View
    private lateinit var chatCompanionName: TextView
    private lateinit var connectionIndicator: View
    private lateinit var voiceStatusIndicator: View
    private lateinit var characterImage: ImageView
    private var voiceHudAnimator: android.animation.ObjectAnimator? = null

    private var isOverlayAttached = false
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var voiceManager: com.orbital.voice.VoiceManager
    private var avatarBrain: AvatarBrain? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var actionDebounceJob: Job? = null
    private var lastActionTime = 0L
    private val ACTION_DEBOUNCE_MS = 1500L

    private var currentVoiceStatus: String = "idle"
    private var currentCharacter: String = "aether"
    private var lowBatteryNotified = false

    // Server-related fields
    private var server: ApplicationEngine? = null
    private val isServerRunning = AtomicBoolean(false)
    private lateinit var serverConfig: ServerConfig

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let {
                when (it) {
                    ACTION_UPDATE_CONNECTION_STATUS -> {
                        val status = intent.getStringExtra("status") ?: "connected"
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
                    ACTION_PERMISSION_REVOKED -> {
                        stopOverlay()
                    }
                }
            }
        }
    }

    private val configChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                handleConfigurationChanged()
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val charging = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else return
            if (percent < 20 && !charging && !lowBatteryNotified) {
                lowBatteryNotified = true
                setMascotState(com.orbital.ui.MascotState.SAD)
                PowerAwareScheduler.notify(this@OverlayService, 202, "Battery low", "Battery is $percent%. Tap to optimize settings.", optimizeBattery = true)
            } else if (percent >= 20 || charging) {
                lowBatteryNotified = false
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
            addAction(ACTION_PERMISSION_REVOKED)
        })
        registerReceiver(configChangeReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Load the selected character from storage
        val secureStorage = SecureStorage(this)
        currentCharacter = secureStorage.getSelectedCharacter() ?: "aether"

        // Initialize server config for embedded server
        serverConfig = ServerConfig(enableEmbeddedServer = true)

        initVoiceManager()
        initChatEngineObservation()

        // Observe global MascotEventBus state changes
        serviceScope.launch {
            com.orbital.ui.MascotEventBus.currentState.collect { state ->
                setMascotState(state)
            }
        }

        // Observe global MascotEventBus action events (e.g. celebratory jump on ActionSuccess)
        serviceScope.launch {
            com.orbital.ui.MascotEventBus.events.collect { event ->
                when (event) {
                    is com.orbital.ui.MascotEvent.ActionSuccess -> {
                        playMascotJumpAnimation {
                            // Visual jump reaction on action completion
                        }
                    }
                    else -> {}
                }
            }
        }

        // Start embedded server on port 3001 if enabled
        if (serverConfig.enableEmbeddedServer) {
            startEmbeddedServer()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        handleConfigurationChanged()
    }

    private fun handleConfigurationChanged() {
        if (!isOverlayAttached || !::overlayView.isInitialized || !overlayView.isAttachedToWindow) return

        // Recalculate position and dimensions after config change (rotation, density, etc.)
        val (screenWidth, screenHeight) = getScreenDimensions()
        val bubbleSize = bubbleAvatarContainer.width.coerceAtLeast((52 * resources.displayMetrics.density).toInt())

        // Ensure overlay stays on screen
        windowParams.x = windowParams.x.coerceIn(0, (screenWidth - bubbleSize).coerceAtLeast(0))
        windowParams.y = windowParams.y.coerceIn(40, (screenHeight - bubbleSize - 60).coerceAtLeast(40))

        try {
            windowManager.updateViewLayout(overlayView, windowParams)
        } catch (e: Exception) {
            Log.w("OverlayService", "Failed to update layout after config change", e)
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this)
        }
        return true
    }

    private fun verifyAndRequestPermissionIfNeeded(): Boolean {
        if (!checkOverlayPermission()) {
            Log.w("OverlayService", "Overlay permission revoked, stopping service")
            stopOverlay()
            return false
        }
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check permission on every start command
        if (!verifyAndRequestPermissionIfNeeded()) {
            return START_NOT_STICKY
        }

        val charExtra = intent?.getStringExtra("character_id") ?: intent?.getStringExtra("character")
        if (!charExtra.isNullOrBlank()) {
            currentCharacter = charExtra
            updateCharacter(charExtra)
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlay()
                stopSelf()
            }
            ACTION_UPDATE_CHARACTER -> {
                if (isOverlayAttached) {
                    updateCharacter(currentCharacter)
                } else {
                    startOverlay()
                }
            }
            else -> {
                startOverlay()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Don't stop the service when task is removed - keep overlay alive
        // Only stop on explicit ACTION_STOP or permission revocation
    }

    private fun initVoiceManager() {
        voiceManager = com.orbital.voice.VoiceManager(this)
        voiceManager.setVoiceCallback(object : com.orbital.voice.VoiceManager.VoiceCallback {
            override fun onSpeechRecognized(text: String) {
                if (text.isNotBlank()) {
                    chatInputEditText.setText(text)
                    sendPromptToCompanion(text, speakResult = true)
                }
            }

            override fun onSpeechError(error: String) {
                updateVoiceStatus("idle")
            }

            override fun onSpeechStart() {
                updateVoiceStatus("listening")
            }

            override fun onSpeechEnd() {
                updateVoiceStatus("idle")
            }

            override fun onSpeechPartialResult(text: String) {
                if (chatPanel.visibility == View.VISIBLE) {
                    chatInputEditText.setText(text)
                }
            }

            override fun onTtsStart() {
                updateVoiceStatus("speaking")
            }

            override fun onTtsEnd() {
                updateVoiceStatus("idle")
            }
        })
    }

    private var currentMascotState: com.orbital.ui.MascotState = com.orbital.ui.MascotState.IDLE
    private var idleTimerJob: Job? = null
    private var floatAnimator: android.animation.ObjectAnimator? = null
    private var breatheAnimator: android.animation.ObjectAnimator? = null

    private fun startFloatingBreathingAnimation() {
        if (!::characterImage.isInitialized) return
        
        floatAnimator?.cancel()
        breatheAnimator?.cancel()

        // Subtle vertical floating bob
        floatAnimator = android.animation.ObjectAnimator.ofFloat(characterImage, "translationY", -5f, 5f).apply {
            duration = 2000
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }

        // Gentle breathing scale
        breatheAnimator = android.animation.ObjectAnimator.ofFloat(characterImage, "scaleX", 0.98f, 1.02f).apply {
            duration = 1800
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun playMascotJumpAnimation(onComplete: (() -> Unit)? = null) {
        if (!::characterImage.isInitialized) return
        setMascotState(com.orbital.ui.MascotState.JUMP)

        val jumpY = android.animation.ObjectAnimator.ofFloat(characterImage, "translationY", 0f, -22f, 0f).apply {
            duration = 420
            interpolator = android.view.animation.OvershootInterpolator(1.8f)
        }
        val scaleX = android.animation.ObjectAnimator.ofFloat(characterImage, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 420
        }
        val scaleY = android.animation.ObjectAnimator.ofFloat(characterImage, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 420
        }

        android.animation.AnimatorSet().apply {
            playTogether(jumpY, scaleX, scaleY)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                    startFloatingBreathingAnimation()
                    serviceScope.launch {
                        delay(600)
                        if (currentMascotState == com.orbital.ui.MascotState.JUMP) {
                            setMascotState(com.orbital.ui.MascotState.IDLE)
                        }
                    }
                }
            })
            start()
        }
    }

    private fun setMascotSpriteInternal(state: com.orbital.ui.MascotState) {
        if (::characterImage.isInitialized) {
            com.orbital.ui.EmotionMediaLoader.loadEmotion(this, currentCharacter, state, characterImage)
        }
    }

    private fun setMascotState(state: com.orbital.ui.MascotState) {
        currentMascotState = state
        resetInactivityTimer()
        setMascotSpriteInternal(state)
    }

    private fun resetInactivityTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = serviceScope.launch {
            delay(150000) // 2.5 minutes of inactivity -> gentle sleep mode
            if (currentMascotState == com.orbital.ui.MascotState.IDLE) {
                setMascotState(com.orbital.ui.MascotState.SLEEPING)
            }
        }
    }

    private var snapAnimator: android.animation.ValueAnimator? = null

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun animateToPosition(targetX: Int, targetY: Int? = null) {
        snapAnimator?.cancel()
        val startX = windowParams.x
        val startY = windowParams.y
        snapAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { anim ->
                val frac = anim.animatedValue as Float
                windowParams.x = (startX + (targetX - startX) * frac).toInt()
                if (targetY != null) {
                    windowParams.y = (startY + (targetY - startY) * frac).toInt()
                }
                if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
                    windowManager.updateViewLayout(overlayView, windowParams)
                }
            }
            start()
        }
    }

    private fun snapToNearestEdge() {
        val (screenWidth, _) = getScreenDimensions()
        val bubbleSize = if (::bubbleAvatarContainer.isInitialized && bubbleAvatarContainer.width > 0) bubbleAvatarContainer.width else (52 * resources.displayMetrics.density).toInt()
        val snapToLeft = (windowParams.x + bubbleSize / 2) < (screenWidth / 2)
        val targetX = if (snapToLeft) 16 else (screenWidth - bubbleSize - 16).coerceAtLeast(0)
        animateToPosition(targetX)
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("OverlayService", "Cannot start overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        // Enforce strictly 1 overlay avatar at a time
        if (isOverlayAttached && ::overlayView.isInitialized) {
            updateCharacter(currentCharacter)
            return
        }

        stopOverlay()

        windowParams = WindowManager.LayoutParams(
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
            y = 300
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        bubbleAvatarContainer = overlayView.findViewById(R.id.bubbleAvatarContainer)
        chatPanel = overlayView.findViewById(R.id.chatPanel)
        chatMessagesContainer = overlayView.findViewById(R.id.chatMessagesContainer)
        chatSuggestionsContainer = overlayView.findViewById(R.id.chatSuggestionsContainer)
        chatScrollView = overlayView.findViewById(R.id.chatScrollView)
        chatInputEditText = overlayView.findViewById(R.id.chatInputEditText)
        chatSendButton = overlayView.findViewById(R.id.chatSendButton)
        chatMicButton = overlayView.findViewById(R.id.chatMicButton)
        chatCloseButton = overlayView.findViewById(R.id.chatCloseButton)
        chatExpandButton = overlayView.findViewById(R.id.chatExpandButton)
        chatCompanionName = overlayView.findViewById(R.id.chatCompanionName)
        connectionIndicator = overlayView.findViewById(R.id.connectionIndicator)
        voiceStatusIndicator = overlayView.findViewById(R.id.voiceStatusIndicator)
        characterImage = overlayView.findViewById(R.id.characterImage)

        // Immediately set the current character sprite before attaching view
        com.orbital.ui.EmotionMediaLoader.loadEmotion(this, currentCharacter, com.orbital.ui.MascotState.IDLE, characterImage, false)

        // Setup Chat buttons
        chatExpandButton.setOnClickListener {
            toggleChatPanel(false)
            val appIntent = Intent(this@OverlayService, com.orbital.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(appIntent)
        }

        chatCloseButton.setOnClickListener {
            toggleChatPanel(false)
            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ResetToIdle)
        }

        chatSendButton.setOnClickListener {
            val text = chatInputEditText.text.toString().trim()
            if (text.isNotBlank()) {
                sendPromptToCompanion(text, speakResult = false)
                chatInputEditText.setText("")
            }
        }

        chatMicButton.setOnClickListener {
            if (voiceManager.isListening()) {
                voiceManager.stopListening()
            } else {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.VoiceListening)
                voiceManager.startListening()
                val listeningMsg = if (voiceManager.isUsingWhisper()) "🎙️ Listening with Whisper STT..." else "🎙️ Listening..."
                Toast.makeText(this@OverlayService, listeningMsg, Toast.LENGTH_SHORT).show()
            }
        }

        chatInputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val text = chatInputEditText.text.toString().trim()
                if (text.isNotBlank()) {
                    sendPromptToCompanion(text, speakResult = false)
                    chatInputEditText.setText("")
                }
                true
            } else false
        }

        // Touch & gesture handling on Avatar Bubble with full screen freedom
        bubbleAvatarContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchDownTime = 0L
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        avatarBrain?.onUserInteracting(true)
                        snapAnimator?.cancel()
                        initialX = windowParams.x
                        initialY = windowParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchDownTime = System.currentTimeMillis()
                        isDragging = false
                        floatAnimator?.pause()
                        breatheAnimator?.pause()
                        bubbleAvatarContainer.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            isDragging = true
                            val (screenWidth, screenHeight) = getScreenDimensions()
                            val viewW = overlayView.width.coerceAtLeast(bubbleAvatarContainer.width)
                            val viewH = overlayView.height.coerceAtLeast(bubbleAvatarContainer.height)

                            val minX = 0
                            val maxX = (screenWidth - viewW).coerceAtLeast(0)
                            val minY = 40 // Below status bar
                            val maxY = (screenHeight - viewH - 60).coerceAtLeast(minY)

                            windowParams.x = (initialX + dx).coerceIn(minX, maxX)
                            windowParams.y = (initialY + dy).coerceIn(minY, maxY)
                            if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
                                windowManager.updateViewLayout(overlayView, windowParams)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        avatarBrain?.onUserInteracting(false)
                        bubbleAvatarContainer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        floatAnimator?.resume()
                        breatheAnimator?.resume()

                        val duration = System.currentTimeMillis() - touchDownTime
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)

                        if (!isDragging && dx < 15 && dy < 15) {
                            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.Tap)
                            playMascotJumpAnimation {
                                toggleChatPanel(chatPanel.visibility != View.VISIBLE)
                            }
                        } else if (isDragging && chatPanel.visibility != View.VISIBLE) {
                            snapToNearestEdge()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(overlayView, windowParams)
            isOverlayAttached = true
            startFloatingBreathingAnimation()
            resetInactivityTimer()

            // Initialize and launch Autonomous Avatar Brain
            avatarBrain?.stop()
            avatarBrain = AvatarBrain(
                windowManager = windowManager,
                overlayViewProvider = { if (::overlayView.isInitialized) overlayView else null },
                avatarContainerProvider = { if (::bubbleAvatarContainer.isInitialized) bubbleAvatarContainer else null },
                characterImageProvider = { if (::characterImage.isInitialized) characterImage else null },
                windowParams = windowParams,
                getScreenDimensions = { getScreenDimensions() },
                onStateChanged = { state -> setMascotState(state) }
            ).apply {
                start(serviceScope)
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Failed to add view to windowManager", e)
        }

        // Initial status updates
        updateConnectionStatus("connected")
        updateVoiceStatus("idle")
        updateCharacter(currentCharacter)
        renderQuickSuggestions()
        renderMessagesUI(chatEngine.messages.value, chatEngine.isStreaming.value, chatEngine.streamingContent.value)
    }

    private fun toggleChatPanel(open: Boolean) {
        avatarBrain?.onChatPanelVisibilityChanged(open)
        if (open) {
            chatPanel.visibility = View.VISIBLE
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            
            // Adjust position so chat panel does not get cut off by screen borders
            val (screenWidth, screenHeight) = getScreenDimensions()
            val panelWidthPx = (320 * resources.displayMetrics.density).toInt()
            val maxX = (screenWidth - panelWidthPx - 24).coerceAtLeast(16)
            if (windowParams.x > maxX) {
                animateToPosition(maxX)
            }
            val maxY = (screenHeight - (320 * resources.displayMetrics.density).toInt()).coerceAtLeast(80)
            if (windowParams.y > maxY) {
                windowParams.y = maxY
            }
            renderQuickSuggestions()
            renderMessagesUI(chatEngine.messages.value, chatEngine.isStreaming.value, chatEngine.streamingContent.value)
        } else {
            chatPanel.visibility = View.GONE
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            snapToNearestEdge()
        }
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.updateViewLayout(overlayView, windowParams)
        }
    }

    private fun initChatEngineObservation() {
        // Continuous live streaming update
        serviceScope.launch {
            chatEngine.streamingContent.collect { content ->
                if (chatEngine.isStreaming.value) {
                    avatarBrain?.onThinking(false)
                    renderMessagesUI(chatEngine.messages.value, true, content)
                }
            }
        }

        // Continuous message completion observation
        serviceScope.launch {
            chatEngine.messages.collect { messages ->
                val isStreaming = chatEngine.isStreaming.value
                val streamContent = chatEngine.streamingContent.value
                if (!isStreaming) {
                    avatarBrain?.onThinking(false)
                }
                renderMessagesUI(messages, isStreaming, streamContent)

                val lastMsg = messages.lastOrNull()
                if (lastMsg != null && lastMsg.role == "assistant" && !isStreaming) {
                    val contentDisplay = lastMsg.content ?: ""
                    if (shouldSpeakLastResult && contentDisplay.isNotBlank()) {
                        shouldSpeakLastResult = false
                        voiceManager.speak(contentDisplay)
                    }
                }
            }
        }
    }

    private fun renderMessagesUI(
        messages: List<ChatMessage>,
        isStreaming: Boolean,
        streamContent: String
    ) {
        if (!::chatMessagesContainer.isInitialized) return
        chatMessagesContainer.removeAllViews()

        val displayMessages = if (messages.isEmpty()) {
            listOf(
                ChatMessage(
                    role = "assistant",
                    content = "Hi! I am ${currentCharacter.replaceFirstChar { it.uppercase() }} (AI Companion). Ask or command me to open apps, compose emails, play music, or check your device!"
                )
            )
        } else {
            messages.takeLast(6)
        }

        val density = resources.displayMetrics.density

        displayMessages.forEach { msg ->
            if (msg.role == "user") {
                // User Bubble (Right Aligned, Purple)
                val userRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (6 * density).toInt()
                        bottomMargin = (4 * density).toInt()
                    }
                }

                val userBubble = TextView(this).apply {
                    text = msg.content ?: ""
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setBackgroundResource(R.drawable.bg_msg_user)
                    setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                    maxWidth = (240 * density).toInt()
                }

                userRow.addView(userBubble)
                chatMessagesContainer.addView(userRow)
            } else if (msg.role == "assistant") {
                // Assistant Bubble (Left Aligned, Dark Card + Action Badge + Next Step Chips)
                val assistantRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.START
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (6 * density).toInt()
                        bottomMargin = (4 * density).toInt()
                    }
                }

                val contentCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_msg_assistant)
                    setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val contentText = TextView(this).apply {
                    text = msg.content ?: ""
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 13f
                    setLineSpacing(3 * density, 1f)
                }
                contentCard.addView(contentText)

                // Web Link Buttons
                val urlRegex = Regex("https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?")
                val urls = urlRegex.findAll(msg.content ?: "").map { it.value }.toList()
                if (urls.isNotEmpty()) {
                    urls.take(2).forEach { url ->
                        val linkBtn = TextView(this).apply {
                            text = "🔗 Open Link"
                            textSize = 11f
                            setTextColor(Color.parseColor("#60A5FA"))
                            setBackgroundResource(R.drawable.bg_chip_suggestion)
                            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = (6 * density).toInt()
                            }
                            setOnClickListener {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                        contentCard.addView(linkBtn)
                    }
                }

                // Action Badge
                msg.actionLabel?.let { actionBadgeText ->
                    val badge = TextView(this).apply {
                        text = actionBadgeText
                        textSize = 11f
                        setTextColor(Color.parseColor("#6EE7B7"))
                        setBackgroundResource(R.drawable.bg_badge_success)
                        setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (8 * density).toInt()
                        }
                    }
                    contentCard.addView(badge)
                }

                // Next Step Suggestions
                val suggestions = NextStepSuggester.getSuggestions(null, msg.content ?: "")
                if (suggestions.isNotEmpty() && !isStreaming) {
                    val suggestionHeader = TextView(this).apply {
                        text = "💡 Next steps:"
                        textSize = 11f
                        setTextColor(Color.parseColor("#A78BFA"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (10 * density).toInt()
                            bottomMargin = (4 * density).toInt()
                        }
                    }
                    contentCard.addView(suggestionHeader)

                    suggestions.take(3).forEach { suggestion ->
                        val chip = TextView(this).apply {
                            text = suggestion
                            textSize = 11f
                            setTextColor(Color.parseColor("#CBD5E1"))
                            setBackgroundResource(R.drawable.bg_chip_suggestion)
                            setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = (4 * density).toInt()
                            }
                            setOnClickListener {
                                handleSuggestionClick(suggestion)
                            }
                        }
                        contentCard.addView(chip)
                    }
                }

                assistantRow.addView(contentCard)
                chatMessagesContainer.addView(assistantRow)
            }
        }

        // Live streaming state
        if (isStreaming && streamContent.isNotBlank()) {
            val parsed = ActionParser.parse(streamContent)
            val streamRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_msg_assistant)
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * density).toInt()
                }
            }
            val streamText = TextView(this).apply {
                text = parsed.userDisplayText.ifBlank { "⚡ Executing..." }
                setTextColor(Color.parseColor("#C084FC"))
                textSize = 13f
            }
            streamRow.addView(streamText)
            chatMessagesContainer.addView(streamRow)
        }

        if (::chatScrollView.isInitialized) {
            chatScrollView.post {
                chatScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun handleSuggestionClick(suggestion: String) {
        val clean = NextStepSuggester.cleanPromptForInput(suggestion)
        val current = chatInputEditText.text.toString().trim()
        val newText = if (current.isBlank()) clean else "$current and then $clean"
        chatInputEditText.setText(newText)
        chatInputEditText.setSelection(newText.length)
        chatInputEditText.requestFocus()
    }

    private fun renderQuickSuggestions() {
        if (!::chatSuggestionsContainer.isInitialized) return
        chatSuggestionsContainer.removeAllViews()
        val density = resources.displayMetrics.density

        val quickList = listOfNotNull(foregroundSuggestion()) + listOf(
            "✉️ Open Gmail",
            "▶️ Open YouTube",
            "💬 Open WhatsApp",
            "⏱️ Set 5m Timer",
            "🔋 Check Battery",
            "🌐 Search AI News"
        )

        quickList.forEach { prompt ->
            val chip = TextView(this).apply {
                text = prompt
                textSize = 11f
                setTextColor(Color.parseColor("#CBD5E1"))
                setBackgroundResource(R.drawable.bg_chip_suggestion)
                setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
                setOnClickListener {
                    handleSuggestionClick(prompt)
                }
            }
            chatSuggestionsContainer.addView(chip)
        }
    }

    private fun foregroundSuggestion(): String? {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val packageName = try {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, System.currentTimeMillis() - 60_000, System.currentTimeMillis())
                .maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: SecurityException) { null } ?: return null
        return when {
            packageName.contains("gmail") -> "✦ Summarize recent emails"
            packageName.contains("whatsapp") -> "✦ Draft a quick reply"
            packageName.contains("youtube") -> "✦ Find a focus video"
            packageName.contains("maps") -> "✦ Navigate home"
            packageName.contains("chrome") || packageName.contains("browser") -> "✦ Summarize this page"
            else -> null
        }
    }

    private var shouldSpeakLastResult = false

    private fun sendPromptToCompanion(prompt: String, speakResult: Boolean) {
        // Debounce: ignore rapid successive calls within ACTION_DEBOUNCE_MS
        val now = System.currentTimeMillis()
        if (now - lastActionTime < ACTION_DEBOUNCE_MS) {
            Log.d("OverlayService", "Action debounced: too soon since last action")
            return
        }
        lastActionTime = now
        shouldSpeakLastResult = speakResult

        toggleChatPanel(true)
        avatarBrain?.onThinking(true)
        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.PromptSent(prompt))

        // Set character in ChatEngine
        chatEngine.setCharacter(currentCharacter)

        // Use ChatEngine for unified streaming logic
        serviceScope.launch {
            chatEngine.sendMessage(prompt)
        }
    }

    private fun stopOverlay(stopService: Boolean = false) {
        try {
            avatarBrain?.stop()
            avatarBrain = null
            floatAnimator?.cancel()
            breatheAnimator?.cancel()
            idleTimerJob?.cancel()
            actionDebounceJob?.cancel()
            if (::overlayView.isInitialized) {
                try {
                    windowManager.removeViewImmediate(overlayView)
                } catch (_: Exception) {
                    try {
                        windowManager.removeView(overlayView)
                    } catch (_: Exception) {}
                }
            }
            isOverlayAttached = false
            if (stopService) {
                try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
                try { unregisterReceiver(configChangeReceiver) } catch (_: Exception) {}
                try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
                voiceManager.shutdown()
                stopSelf()
            }
        } catch (_: Exception) {}
    }

    private fun updateConnectionStatus(status: String) {
        val colorRes = when (status.lowercase()) {
            "connected" -> Color.parseColor("#10B981") // Crisp Emerald Green
            "connecting" -> Color.parseColor("#F59E0B") // Amber
            "error" -> Color.parseColor("#EF4444") // Red
            else -> Color.parseColor("#10B981")
        }
        if (::connectionIndicator.isInitialized) {
            val bg = connectionIndicator.background
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(colorRes)
            } else {
                connectionIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(colorRes)
            }
        }
    }

    private fun updateVoiceStatus(status: String) {
        currentVoiceStatus = status
        val colorRes = when (status) {
            "listening" -> {
                avatarBrain?.onThinking(true)
                setMascotState(com.orbital.ui.MascotState.CURIOUS)
                Color.parseColor("#06B6D4") // Cyan
            }
            "speaking" -> {
                avatarBrain?.onThinking(false)
                setMascotState(com.orbital.ui.MascotState.HAPPY)
                Color.parseColor("#8B5CF6") // Purple
            }
            "error" -> {
                avatarBrain?.onThinking(false)
                setMascotState(com.orbital.ui.MascotState.SAD)
                Color.parseColor("#EF4444")
            }
            else -> Color.TRANSPARENT
        }
        if (::voiceStatusIndicator.isInitialized) {
            if (status == "listening") startVoiceHud(colorRes) else {
                stopVoiceHud()
                voiceStatusIndicator.setBackgroundColor(colorRes)
                voiceStatusIndicator.alpha = if (colorRes == Color.TRANSPARENT) 0f else 1f
            }
        }
    }

    private fun startVoiceHud(color: Int) {
        voiceStatusIndicator.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke((2 * resources.displayMetrics.density).toInt(), color) }
        voiceHudAnimator?.cancel()
        voiceHudAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            voiceStatusIndicator,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f)
        ).apply {
            duration = 600; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
        voiceStatusIndicator.animate().alpha(0.9f).setDuration(150).start()
    }

    private fun stopVoiceHud() {
        voiceHudAnimator?.cancel()
        voiceHudAnimator = null
        voiceStatusIndicator.animate().alpha(0f).setDuration(150).start()
    }

    private fun updateCharacter(character: String) {
        currentCharacter = character
        setMascotState(com.orbital.ui.MascotState.IDLE)
        val char = com.orbital.ui.Character.find(character)
        if (::chatCompanionName.isInitialized) {
            chatCompanionName.text = "${char.name} (AI Companion)"
        }
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
            avatarBrain?.stop()
            avatarBrain = null
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
                unregisterReceiver(statusReceiver)
                unregisterReceiver(configChangeReceiver)
                unregisterReceiver(batteryReceiver)
            }
            stopEmbeddedServer()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    // Embedded server functionality
    private fun startEmbeddedServer() {
        if (isServerRunning.get()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(CIO, port = serverConfig.embeddedServerPort) {
                    install(ContentNegotiation) {
                        json()
                    }

                    routing {
                        post("/v1/chat/completions") {
                            val request = call.receive<ChatRequest>()
                            if (request.stream) {
                                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                    val channel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                                    llmRepository.streamCompletion(
                                        request.model,
                                        request.messages,
                                        onChunk = { chunk ->
                                            val escaped = JSONObject.quote(chunk)
                                            val sseData = "data: {\"choices\":[{\"delta\":{\"content\":$escaped}}]}\n\n"
                                            channel.trySend(sseData)
                                        },
                                        onError = { _ ->
                                            channel.trySend("data: [DONE]\n\n")
                                            channel.close()
                                        }
                                    )
                                    for (msg in channel) {
                                        write(msg)
                                        flush()
                                    }
                                }
                            } else {
                                val fullContent = CompletableDeferred<String>()
                                val sb = StringBuilder()
                                llmRepository.streamCompletion(
                                    request.model,
                                    request.messages,
                                    onChunk = { chunk -> sb.append(chunk) },
                                    onError = { _ -> fullContent.complete(sb.toString()) }
                                )
                                val text = withTimeoutOrNull(30000L) { fullContent.await() } ?: sb.toString()
                                call.respond(
                                    HttpStatusCode.OK,
                                    ChatResponse(
                                        id = "chatcmpl-" + System.currentTimeMillis(),
                                        model = request.model,
                                        choices = listOf(
                                            ChatChoice(
                                                index = 0,
                                                message = ChatMessage(role = "assistant", content = text),
                                                delta = null,
                                                finish_reason = "stop"
                                            )
                                        )
                                    )
                                )
                            }
                        }
                    }
                }.start(wait = false)
                isServerRunning.set(true)
            } catch (e: Exception) {
                isServerRunning.set(false)
            }
        }
    }

    private fun stopEmbeddedServer() {
        if (isServerRunning.get()) {
            server?.stop(1000, 2000)
            isServerRunning.set(false)
        }
    }
}
