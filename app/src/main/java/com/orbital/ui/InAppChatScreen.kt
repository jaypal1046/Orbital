package com.orbital.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.data.ChatMessage
import com.orbital.data.LlmRepository
import com.orbital.data.ProviderType
import com.orbital.data.SecureStorage
import com.orbital.overlay.OverlayService
import kotlinx.coroutines.launch
import java.util.Locale

import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceActionExecutor

data class UiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val providerName: String? = null,
    val modelName: String? = null,
    val actionLabel: String? = null,
    val actionDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppChatScreen(
    secureStorage: SecureStorage,
    llmRepository: LlmRepository,
    onOpenKeys: () -> Unit,
    onOpenCharacters: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val actionExecutor = remember { DeviceActionExecutor(context) }

    var characterName by remember {
        mutableStateOf(secureStorage.getCharacter() ?: "Aether (AI Companion)")
    }

    var messages by remember {
        mutableStateOf(
            listOf(
                UiMessage(
                    role = "assistant",
                    content = "Hello! I am $characterName, your client-side executive AI companion. I can answer questions, open apps (e.g. Gmail, YouTube, WhatsApp), search the web, set timers, and manage your device tasks. How can I help you today?",
                    providerName = "Orbital Router",
                    modelName = "Executive Agent"
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var currentStreamContent by remember { mutableStateOf("") }
    var activeServingProvider by remember { mutableStateOf<String?>("Auto-Router") }
    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Text to Speech
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized
            }
        }
        ttsInstance.language = Locale.US
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "companion_tts")
    }

    val currentMascotState by MascotEventBus.currentState.collectAsState()

    // When stream finishes (or on complete response)
    fun finalizeStreamedResponse() {
        if (!isStreaming || currentStreamContent.isBlank()) return
        isStreaming = false
        val parsed = ActionParser.parse(currentStreamContent)
        var actionBadge: String? = null
        var actionDetails: String? = null

        if (parsed.action != null) {
            MascotEventBus.postEvent(MascotEvent.ActionExecuting(parsed.action.javaClass.simpleName))
            val result = actionExecutor.execute(parsed.action)
            when (result) {
                is ActionResult.Success -> {
                    MascotEventBus.postEvent(MascotEvent.ActionSuccess(result.message))
                    actionBadge = "⚡ Executed: ${result.message}"
                    actionDetails = result.details
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                is ActionResult.Error -> {
                    MascotEventBus.postEvent(MascotEvent.ActionFailed(result.errorMessage))
                    actionBadge = "⚠️ Action Failed: ${result.errorMessage}"
                    Toast.makeText(context, result.errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            MascotEventBus.postEvent(MascotEvent.ResetToIdle)
        }

        val assistantMsg = UiMessage(
            role = "assistant",
            content = parsed.userDisplayText,
            providerName = activeServingProvider ?: "Auto-Router",
            modelName = "executive",
            actionLabel = actionBadge,
            actionDetails = actionDetails
        )
        messages = messages + assistantMsg
        currentStreamContent = ""
    }

    fun sendMessage(textToSend: String) {
        val cleanText = textToSend.trim()
        if (cleanText.isBlank() || isStreaming) return

        val userMessage = UiMessage(role = "user", content = cleanText)
        messages = messages + userMessage
        inputText = ""
        isStreaming = true
        currentStreamContent = ""
        MascotEventBus.postEvent(MascotEvent.PromptSent(cleanText))

        val activeType = llmRepository.getCurrentProviderType() ?: ProviderType.GROQ
        activeServingProvider = activeType.name

        // Build history with executive system prompt
        val chatHistory = mutableListOf<ChatMessage>()
        chatHistory.add(ChatMessage(role = "system", content = ActionParser.buildSystemPrompt(characterName)))
        
        messages.takeLast(10).forEach { msg ->
            chatHistory.add(ChatMessage(role = msg.role, content = msg.content))
        }

        coroutineScope.launch {
            listState.animateScrollToItem((messages.size).coerceAtLeast(0))
        }

        llmRepository.streamCompletion(
            model = "auto",
            messages = chatHistory,
            onChunk = { chunk ->
                currentStreamContent += chunk
                coroutineScope.launch {
                    listState.animateScrollToItem((messages.size).coerceAtLeast(0))
                }

                debounceJob?.cancel()
                debounceJob = coroutineScope.launch {
                    kotlinx.coroutines.delay(1200)
                    finalizeStreamedResponse()
                }
            },
            onError = { error ->
                debounceJob?.cancel()
                isStreaming = false
                MascotEventBus.postEvent(MascotEvent.ActionFailed(error.message ?: "Request failed"))
                val errText = error.message ?: "Request failed"
                val assistantMsg = UiMessage(
                    role = "assistant",
                    content = if (currentStreamContent.isNotBlank()) currentStreamContent else "⚠️ $errText",
                    providerName = llmRepository.getCurrentProviderType()?.name ?: "Failover",
                    modelName = "auto"
                )
                messages = messages + assistantMsg
                currentStreamContent = ""
            }
        )
    }

    var showRoutingSheet by remember { mutableStateOf(false) }
    var currentRoutingMode by remember { mutableStateOf(llmRepository.getRoutingMode()) }
    var selectedPinnedProvider by remember { mutableStateOf(llmRepository.getCurrentProviderType()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedMascotView(
                            characterId = secureStorage.getSelectedCharacter() ?: "aether",
                            currentState = currentMascotState,
                            size = 38.dp,
                            showGlow = false,
                            onClick = {
                                MascotEventBus.postEvent(MascotEvent.Tap)
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = characterName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showRoutingSheet = true }
                                    .padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (currentRoutingMode) {
                                                com.orbital.data.RoutingMode.AUTO -> Color(0xFF10B981)
                                                com.orbital.data.RoutingMode.FAST -> Color(0xFF38BDF8)
                                                com.orbital.data.RoutingMode.FRONTIER -> Color(0xFFA855F7)
                                                com.orbital.data.RoutingMode.PINNED -> Color(0xFFF59E0B)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentRoutingMode) {
                                        com.orbital.data.RoutingMode.AUTO -> "⚡ Auto-Router"
                                        com.orbital.data.RoutingMode.FAST -> "🚀 Fast Tier"
                                        com.orbital.data.RoutingMode.FRONTIER -> "🧠 Frontier Tier"
                                        com.orbital.data.RoutingMode.PINNED -> "🎯 ${selectedPinnedProvider?.name ?: "Pinned"}"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA7F3D0)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Provider Mode",
                                    tint = Color(0xFFA7F3D0),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Floating Avatar Launcher
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(context, OverlayService::class.java)
                                context.startService(intent)
                                Toast.makeText(context, "Floating Companion Avatar Launched!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Launch Overlay", tint = Color(0xFF38BDF8))
                    }

                    // Character Picker
                    IconButton(onClick = onOpenCharacters) {
                        Icon(Icons.Default.Face, contentDescription = "Switch Character", tint = Color(0xFFC084FC))
                    }

                    // Keys Dashboard
                    IconButton(onClick = onOpenKeys) {
                        Icon(Icons.Default.Settings, contentDescription = "Keys & Providers", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
            )
        },
        containerColor = Color(0xFF0A0C14)
    ) { innerPadding ->
        if (showRoutingSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRoutingSheet = false },
                containerColor = Color(0xFF131722),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "AI Routing & Provider Selector",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Replicating FreeLLMAPI multi-tier intelligent routing",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mode Options
                    com.orbital.data.RoutingMode.values().forEach { mode ->
                        val isSelected = currentRoutingMode == mode
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    currentRoutingMode = mode
                                    llmRepository.setRoutingMode(mode)
                                    if (mode != com.orbital.data.RoutingMode.PINNED) {
                                        showRoutingSheet = false
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF2E1065) else Color(0xFF1E2235)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFA855F7)) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = mode.emoji, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (currentRoutingMode == com.orbital.data.RoutingMode.PINNED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select Pinned Provider:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(com.orbital.data.ProviderRegistry.allProviders) { providerInfo ->
                                val key = llmRepository.getProviderKey(providerInfo.type)
                                val status = llmRepository.getProviderStatus(providerInfo.type)
                                val isSelected = selectedPinnedProvider == providerInfo.type
                                val isConfigured = key.isNotBlank() || providerInfo.type == com.orbital.data.ProviderType.KILO || providerInfo.type == com.orbital.data.ProviderType.OVH || providerInfo.type == com.orbital.data.ProviderType.POLLINATIONS

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF3B1D70) else Color(0xFF181C2C))
                                        .clickable {
                                            selectedPinnedProvider = providerInfo.type
                                            llmRepository.setCurrentProvider(providerInfo.type)
                                            showRoutingSheet = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    !isConfigured -> Color(0xFF64748B)
                                                    status == com.orbital.data.ProviderState.IN_COOLDOWN -> Color(0xFFF59E0B)
                                                    status == com.orbital.data.ProviderState.AVAILABLE -> Color(0xFF10B981)
                                                    else -> Color(0xFFEF4444)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = providerInfo.displayName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isConfigured) Color.White else Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = "${providerInfo.defaultModel} · ${if (isConfigured) "Ready" else "No Key"}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Executive Action Suggestion Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "✉️ Open Gmail",
                    "▶️ Open YouTube",
                    "💬 Open WhatsApp",
                    "⚙️ Open Settings",
                    "🔋 Check Battery Status",
                    "⏱️ Set 5m Timer",
                    "🌐 Search AI News"
                ).forEach { prompt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E2238))
                            .border(1.dp, Color(0xFF333A5E), RoundedCornerShape(20.dp))
                            .clickable { sendMessage(prompt) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = prompt, fontSize = 12.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        characterName = characterName,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(msg.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onSpeak = { speak(msg.content) }
                    )
                }

                // Live Streaming Bubble
                if (isStreaming && currentStreamContent.isNotBlank()) {
                    item {
                        ChatBubbleItem(
                            message = UiMessage(
                                role = "assistant",
                                content = currentStreamContent,
                                providerName = llmRepository.getCurrentProviderType()?.name ?: "Auto-Router",
                                modelName = "streaming"
                            ),
                            characterName = characterName,
                            isStreaming = true,
                            onCopy = {},
                            onSpeak = {}
                        )
                    }
                } else if (isStreaming && currentStreamContent.isBlank()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF8B5CF6),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Routing prompt & checking device executive tasks...",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = Color(0xFF131625),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask or command anything (e.g. open gmail)...", color = Color(0xFF64748B), fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp, max = 120.dp),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF232840),
                            focusedContainerColor = Color(0xFF0F111A),
                            unfocusedContainerColor = Color(0xFF0F111A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isStreaming,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isStreaming)
                                    Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)))
                                else
                                    Brush.linearGradient(listOf(Color(0xFF1E2238), Color(0xFF1E2238)))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isStreaming) Color.White else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: UiMessage,
    characterName: String,
    isStreaming: Boolean = false,
    onCopy: () -> Unit,
    onSpeak: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                val spriteRes = MascotSpriteHelper.getSprite(characterName, MascotState.HAPPY)
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = spriteRes),
                    contentDescription = characterName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E1065))
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) Color(0xFF6D28D9) else Color(0xFF181B2C),
                border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E334D)) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Action Execution Badge
                    message.actionLabel?.let { badge ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF064E3B))
                                .border(1.dp, Color(0xFF059669), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6EE7B7)
                            )
                        }
                    }

                    if (!isUser && !isStreaming) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Serving provider badge
                            message.providerName?.let { provider ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F111A))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⚡ $provider",
                                        fontSize = 10.sp,
                                        color = Color(0xFFA78BFA),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Speak", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
