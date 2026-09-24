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
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.orbital.R
import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceActionExecutor
import com.orbital.data.ChatChoice
import com.orbital.data.ChatMessage
import com.orbital.data.ChatRequest
import com.orbital.data.ChatResponse
import com.orbital.data.LlmRepository
import com.orbital.data.RouterConfig
import com.orbital.data.SecureStorage
import com.orbital.data.ServerConfig
import com.orbital.data.ServerMode
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
    private lateinit var bubbleAvatarContainer: View
    private lateinit var chatPanel: View
    private lateinit var chatResponseText: TextView
    private lateinit var chatScrollView: android.widget.ScrollView
    private lateinit var chatInputEditText: android.widget.EditText
    private lateinit var chatSendButton: View
    private lateinit var chatMicButton: View
    private lateinit var chatCloseButton: View
    private lateinit var chatCompanionName: TextView
    private lateinit var connectionIndicator: View
    private lateinit var voiceStatusIndicator: View
    private lateinit var characterImage: ImageView

    private var isOverlayAttached = false
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var voiceManager: com.orbital.voice.VoiceManager
    private lateinit var actionExecutor: DeviceActionExecutor
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var actionDebounceJob: Job? = null

    private var currentStatus: ConnectionStatus = ConnectionStatus.CONNECTED
    private var currentVoiceStatus: String = "idle"
    private var currentCharacter: String = "aether"

    // Server-related fields
    private var server: ApplicationEngine? = null
    private val isServerRunning = AtomicBoolean(false)
    private lateinit var serverConfig: ServerConfig
    private lateinit var routerConfig: RouterConfig
    private lateinit var llmRepository: LlmRepository

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

        // Initialize server & router with multi-provider secure storage
        serverConfig = ServerConfig(enableEmbeddedServer = true)
        routerConfig = RouterConfig()
        llmRepository = LlmRepository(secureStorage)
        actionExecutor = DeviceActionExecutor(this)

        initVoiceManager()

        // Observe global MascotEventBus state changes
        serviceScope.launch {
            com.orbital.ui.MascotEventBus.currentState.collect { state ->
                setMascotState(state)
            }
        }

        // Start embedded server on port 3001 if enabled
        if (serverConfig.enableEmbeddedServer) {
            startEmbeddedServer()
        }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val charExtra = intent?.getStringExtra("character")
        if (!charExtra.isNullOrBlank()) {
            currentCharacter = charExtra
            updateCharacter(charExtra)
        }

        when (intent?.action) {
            ACTION_STOP -> stopOverlay()
            ACTION_UPDATE_CHARACTER -> {
                startOverlay()
                charExtra?.let { updateCharacter(it) }
            }
            ACTION_START -> startOverlay()
            else -> startOverlay()
        }
        return START_STICKY
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
        floatAnimator = android.animation.ObjectAnimator.ofFloat(characterImage, "translationY", -6f, 6f).apply {
            duration = 1800
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }

        // Gentle breathing scale
        breatheAnimator = android.animation.ObjectAnimator.ofFloat(characterImage, "scaleX", 0.98f, 1.03f).apply {
            duration = 1600
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun playMascotJumpAnimation(onComplete: (() -> Unit)? = null) {
        if (!::characterImage.isInitialized) return
        
        setMascotState(com.orbital.ui.MascotState.JUMP)

        val jumpY = android.animation.ObjectAnimator.ofFloat(characterImage, "translationY", 0f, -24f, 0f).apply {
            duration = 450
            interpolator = android.view.animation.OvershootInterpolator(2.0f)
        }
        val scaleX = android.animation.ObjectAnimator.ofFloat(characterImage, "scaleX", 1f, 1.18f, 1f).apply {
            duration = 450
        }
        val scaleY = android.animation.ObjectAnimator.ofFloat(characterImage, "scaleY", 1f, 1.18f, 1f).apply {
            duration = 450
        }

        android.animation.AnimatorSet().apply {
            playTogether(jumpY, scaleX, scaleY)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                    startFloatingBreathingAnimation()
                }
            })
            start()
        }
    }

    private fun setMascotState(state: com.orbital.ui.MascotState) {
        currentMascotState = state
        resetInactivityTimer()

        if (::characterImage.isInitialized) {
            val spriteRes = com.orbital.ui.MascotSpriteHelper.getSprite(currentCharacter, state)
            
            // Quick cross-scale transition for smooth sprite switch
            characterImage.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .alpha(0.6f)
                .setDuration(120)
                .withEndAction {
                    characterImage.setImageResource(spriteRes)
                    characterImage.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(160)
                        .start()
                }
                .start()
        }
    }

    private fun resetInactivityTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = serviceScope.launch {
            delay(50000) // 50 seconds of idle
            if (currentMascotState == com.orbital.ui.MascotState.IDLE) {
                setMascotState(com.orbital.ui.MascotState.SLEEPING)
            }
        }
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("OverlayService", "Cannot start overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        // Enforce strictly 1 overlay avatar at a time
        if (isOverlayAttached && ::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            updateCharacter(currentCharacter)
            return
        }

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
        chatResponseText = overlayView.findViewById(R.id.chatResponseText)
        chatScrollView = overlayView.findViewById(R.id.chatScrollView)
        chatInputEditText = overlayView.findViewById(R.id.chatInputEditText)
        chatSendButton = overlayView.findViewById(R.id.chatSendButton)
        chatMicButton = overlayView.findViewById(R.id.chatMicButton)
        chatCloseButton = overlayView.findViewById(R.id.chatCloseButton)
        chatCompanionName = overlayView.findViewById(R.id.chatCompanionName)
        connectionIndicator = overlayView.findViewById(R.id.connectionIndicator)
        voiceStatusIndicator = overlayView.findViewById(R.id.voiceStatusIndicator)
        characterImage = overlayView.findViewById(R.id.characterImage)

        // Setup Chat buttons
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
            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.VoiceListening)
            voiceManager.startListening()
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

        // Touch & gesture handling on Avatar Bubble
        bubbleAvatarContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchDownTime = 0L

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowParams.x
                        initialY = windowParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchDownTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            windowParams.x = initialX + dx
                            windowParams.y = initialY + dy
                            windowManager.updateViewLayout(overlayView, windowParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - touchDownTime
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)

                        if (dx < 15 && dy < 15) {
                            if (duration >= 500) {
                                // Long Press -> Whisper Mode (Voice)
                                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.VoiceListening)
                                voiceManager.startListening()
                            } else {
                                // Tap / Click -> Trigger Tap Event + Jump Animation + Toggle Chat Panel
                                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.Tap)
                                playMascotJumpAnimation {
                                    val shouldOpen = chatPanel.visibility != View.VISIBLE
                                    toggleChatPanel(shouldOpen)
                                }
                            }
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
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Failed to add view to windowManager", e)
        }

        // Initial status updates
        updateConnectionStatus("connected")
        updateVoiceStatus("idle")
        updateCharacter(currentCharacter)
    }

    private fun toggleChatPanel(open: Boolean) {
        if (open) {
            chatPanel.visibility = View.VISIBLE
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            chatPanel.visibility = View.GONE
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.updateViewLayout(overlayView, windowParams)
        }
    }

    private fun sendPromptToCompanion(prompt: String, speakResult: Boolean) {
        toggleChatPanel(true)
        chatResponseText.text = "You: $prompt\n\nThinking & Executing..."
        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.PromptSent(prompt))
        val responseBuilder = StringBuilder()
        var hasExecutedAction = false

        val messages = listOf(
            com.orbital.data.ChatMessage(role = "system", content = ActionParser.buildSystemPrompt(currentCharacter)),
            com.orbital.data.ChatMessage(role = "user", content = prompt)
        )

        fun finalizeResponse() {
            if (hasExecutedAction) return
            hasExecutedAction = true
            val fullText = responseBuilder.toString()
            val parsed = ActionParser.parse(fullText)
            
            var actionStatus = ""
            if (parsed.action != null) {
                // Post working event
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionExecuting(parsed.action.javaClass.simpleName))
                val actionResult = actionExecutor.execute(parsed.action)
                actionStatus = when (actionResult) {
                    is ActionResult.Success -> {
                        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionSuccess(actionResult.message))
                        "\n\n⚡ ${actionResult.message}"
                    }
                    is ActionResult.Error -> {
                        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(actionResult.errorMessage))
                        "\n\n⚠️ ${actionResult.errorMessage}"
                    }
                }
            } else {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ResetToIdle)
            }

            serviceScope.launch {
                chatResponseText.text = "You: $prompt\n\n$currentCharacter:\n${parsed.userDisplayText}$actionStatus"
                chatScrollView.fullScroll(View.FOCUS_DOWN)
                if (speakResult && parsed.userDisplayText.isNotBlank()) {
                    voiceManager.speak(parsed.userDisplayText)
                }
            }
        }

        llmRepository.streamCompletion(
            model = "auto",
            messages = messages,
            onChunk = { chunk ->
                responseBuilder.append(chunk)
                if (currentMascotState != com.orbital.ui.MascotState.THINKING) {
                    setMascotState(com.orbital.ui.MascotState.THINKING)
                }
                serviceScope.launch {
                    val currentText = responseBuilder.toString()
                    val parsedCurrent = ActionParser.parse(currentText)
                    chatResponseText.text = "You: $prompt\n\n$currentCharacter:\n${parsedCurrent.userDisplayText}"
                    chatScrollView.fullScroll(View.FOCUS_DOWN)
                }

                // Debounce action execution when chunk stream pauses
                actionDebounceJob?.cancel()
                actionDebounceJob = serviceScope.launch {
                    delay(1200)
                    finalizeResponse()
                }
            },
            onError = { error ->
                actionDebounceJob?.cancel()
                setMascotState(com.orbital.ui.MascotState.SAD)
                serviceScope.launch {
                    chatResponseText.text = "You: $prompt\n\nError: ${error.message}"
                }
            }
        )
    }

    private fun stopOverlay() {
        try {
            floatAnimator?.cancel()
            breatheAnimator?.cancel()
            idleTimerJob?.cancel()
            if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
                windowManager.removeView(overlayView)
            }
            isOverlayAttached = false
            unregisterReceiver(statusReceiver)
            voiceManager.shutdown()
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun updateConnectionStatus(status: String) {
        val colorRes = when (status.lowercase()) {
            "connected" -> Color.parseColor("#10B981") // Green
            "connecting" -> Color.parseColor("#F59E0B") // Amber
            "error" -> Color.parseColor("#EF4444") // Red
            else -> Color.parseColor("#10B981")
        }
        if (::connectionIndicator.isInitialized) {
            connectionIndicator.setBackgroundColor(colorRes)
        }
    }

    private fun updateVoiceStatus(status: String) {
        currentVoiceStatus = status
        val colorRes = when (status) {
            "listening" -> {
                setMascotState(com.orbital.ui.MascotState.CURIOUS)
                Color.parseColor("#06B6D4") // Cyan
            }
            "speaking" -> {
                setMascotState(com.orbital.ui.MascotState.HAPPY)
                Color.parseColor("#8B5CF6") // Purple
            }
            "error" -> {
                setMascotState(com.orbital.ui.MascotState.SAD)
                Color.parseColor("#EF4444")
            }
            else -> Color.TRANSPARENT
        }
        if (::voiceStatusIndicator.isInitialized) {
            voiceStatusIndicator.setBackgroundColor(colorRes)
        }
    }

    private fun updateCharacter(character: String) {
        currentCharacter = character
        setMascotState(com.orbital.ui.MascotState.IDLE)
        val name = if (character.equals("lumy", ignoreCase = true)) "Lumy" else "Aether"
        if (::chatCompanionName.isInitialized) {
            chatCompanionName.text = "$name (AI Companion)"
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
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
                unregisterReceiver(statusReceiver)
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