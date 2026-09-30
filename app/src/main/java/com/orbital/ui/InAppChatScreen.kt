package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.orbital.overlay.OverlayService
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.orbital.data.RoutingMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppChatScreen(
    chatViewModel: ChatViewModel,
    onOpenKeys: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenAutomations: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showLegalScreen by remember { mutableStateOf(false) }
    var legalTab by remember { mutableStateOf(LegalTab.PRIVACY) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackInitialLog by remember { mutableStateOf<String?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    // Media & Document Pickers
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val media = com.orbital.media.DocumentReader.readUri(context, it)
                chatViewModel.attachMedia(media)
                Toast.makeText(context, "Attached: ${media.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val media = com.orbital.media.DocumentReader.readUri(context, it)
                chatViewModel.attachMedia(media)
                Toast.makeText(context, "Attached: ${media.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val media = com.orbital.media.ImageReader.readUri(context, it)
                chatViewModel.attachMedia(media)
                Toast.makeText(context, "Attached: ${media.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val media = com.orbital.media.ImageReader.fromBitmap(it, "camera_capture_${System.currentTimeMillis()}.jpg")
            chatViewModel.attachMedia(media)
            Toast.makeText(context, "Camera photo attached", Toast.LENGTH_SHORT).show()
        }
    }

    // Observe ViewModel state
    val messages by chatViewModel.messages.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val inputText by chatViewModel.inputText.collectAsState()
    val isStreaming by chatViewModel.isStreaming.collectAsState()
    val currentStreamContent by chatViewModel.currentStreamContent.collectAsState()
    val activeServingProvider by chatViewModel.activeServingProvider.collectAsState()
    val quickSuggestions by chatViewModel.quickSuggestions.collectAsState()
    val currentRoutingMode by chatViewModel.currentRoutingMode.collectAsState()
    val selectedPinnedProvider by chatViewModel.selectedPinnedProvider.collectAsState()
    val isVoiceListening by chatViewModel.isVoiceListening.collectAsState()
    val currentCharacterId by chatViewModel.currentCharacter.collectAsState()
    val attachedMedia by chatViewModel.attachedMedia.collectAsState()
    val pendingConfirmation by chatViewModel.pendingConfirmation.collectAsState()
    val characterName = chatViewModel.characterName

    var showRoutingSheet by remember { mutableStateOf(false) }
    var showActionTemplatesSheet by remember { mutableStateOf(false) }
    var showCharacterPickerSheet by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideNavDrawer(
                currentCharacterId = currentCharacterId,
                sessions = sessions,
                onSelectCharacter = { charId ->
                    chatViewModel.switchCharacter(charId)
                },
                onNewChat = {
                    chatViewModel.startNewChat()
                    Toast.makeText(context, "Started fresh chat session", Toast.LENGTH_SHORT).show()
                },
                onSelectSession = chatViewModel::loadSession,
                onRenameSession = chatViewModel::renameSession,
                onShareChat = chatViewModel::shareCurrentChat,
                onOpenKeys = onOpenKeys,
                onOpenCharacters = onOpenCharacters,
                onOpenAutomations = onOpenAutomations,
                onOpenRoutingMode = { showRoutingSheet = true },
                onOpenFeedback = {
                    feedbackInitialLog = null
                    showFeedbackDialog = true
                },
                onOpenLegal = { tab ->
                    showLegalScreen = true
                    legalTab = tab
                },
                onOpenAbout = { showAboutDialog = true },
                onCheckForUpdates = {
                    Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                    chatViewModel.checkForUpdates(force = true)
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    characterId = currentCharacterId,
                    characterName = characterName,
                    currentRoutingMode = currentRoutingMode,
                    selectedPinnedProvider = selectedPinnedProvider?.name,
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onOpenCharacters = {
                        showCharacterPickerSheet = true
                    },
                    onRoutingModeClick = { showRoutingSheet = true }
                )
            },
            containerColor = Color(0xFF0A0C14)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MessageList(
                    messages = messages,
                    characterName = characterName,
                    characterId = currentCharacterId,
                    isStreaming = isStreaming,
                    currentStreamContent = currentStreamContent,
                    activeServingProvider = activeServingProvider,
                    modifier = Modifier.weight(1f),
                    onCopy = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onSpeak = { text ->
                        chatViewModel.speak(text)
                    },
                    onRetry = { msg ->
                        chatViewModel.retryMessage(msg)
                    },
                    onReportError = { log ->
                        feedbackInitialLog = log
                        showFeedbackDialog = true
                    },
                    onSuggestionClick = { suggestion ->
                        chatViewModel.onSuggestionClick(suggestion)
                    },
                    onAddStepToInput = { step ->
                        chatViewModel.onAddStepToInput(step)
                    }
                )

                if (messages.isNotEmpty() && quickSuggestions.isNotEmpty()) {
                    QuickSuggestionsBar(
                        quickSuggestions = quickSuggestions,
                        onSuggestionClick = { prompt ->
                            chatViewModel.onQuickSuggestionClick(prompt)
                        }
                    )
                }

                InputBar(
                    inputText = inputText,
                    onInputChange = { chatViewModel.onInputChange(it) },
                    isVoiceListening = isVoiceListening,
                    onVoiceClick = { chatViewModel.toggleVoiceListening() },
                    isStreaming = isStreaming,
                    onSendClick = { chatViewModel.onSendClick() },
                    onQuickTemplateClick = { showAttachmentSheet = true },
                    currentRoutingMode = currentRoutingMode,
                    selectedPinnedProvider = selectedPinnedProvider?.name,
                    onRoutingModeClick = { showRoutingSheet = true },
                    attachedMedia = attachedMedia,
                    onRemoveAttachment = { chatViewModel.clearAttachment() }
                )
            }
        }
    }

    // Attachment & Multi-modal Media Picker Sheet (+ Button)
    if (showAttachmentSheet) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentSheet = false },
            onPickPdf = {
                try {
                    pdfLauncher.launch(arrayOf("application/pdf"))
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot open PDF picker", Toast.LENGTH_SHORT).show()
                }
            },
            onPickDocument = {
                try {
                    docLauncher.launch(arrayOf("text/*", "application/json", "application/pdf", "*/*"))
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot open Document picker", Toast.LENGTH_SHORT).show()
                }
            },
            onPickImage = {
                try {
                    imageLauncher.launch("image/*")
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot open Image gallery", Toast.LENGTH_SHORT).show()
                }
            },
            onTakePhoto = {
                try {
                    cameraLauncher.launch(null)
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot launch Camera", Toast.LENGTH_SHORT).show()
                }
            },
            onReadLiveScreen = {
                chatViewModel.sendMessage("Please read and inspect whatever is currently on my active screen and summarize key information.")
            },
            onQuickAction = { prompt ->
                chatViewModel.sendMessage(prompt)
            }
        )
    }

    pendingConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = chatViewModel::cancelPendingAction,
            title = { Text("Confirm ${action.action.replace('_', ' ').lowercase()}") },
            text = {
                Text(listOfNotNull(action.recipient ?: action.phoneNumber, action.message).joinToString("\n").ifBlank { "This action needs your approval." })
            },
            confirmButton = { TextButton(onClick = chatViewModel::confirmPendingAction) { Text("Confirm") } },
            dismissButton = {
                Row {
                    TextButton(onClick = chatViewModel::editPendingAction) { Text("Edit") }
                    TextButton(onClick = chatViewModel::cancelPendingAction) { Text("Cancel") }
                }
            }
        )
    }

    // Quick Character Switcher Bottom Sheet (Triggered by Face Icon in TopBar)
    if (showCharacterPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCharacterPickerSheet = false },
            containerColor = Color(0xFF121524),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "✨ Choose AI Companion",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Instant personality, voice, and aura switch",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = { showCharacterPickerSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Character.all) { char ->
                        val isSelected = char.id.equals(currentCharacterId, ignoreCase = true)
                        val spriteRes = MascotSpriteHelper.getSprite(char.id, MascotState.IDLE)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF2E1065) else Color(0xFF191D30),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF8B5CF6) else Color(0xFF262D4A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatViewModel.switchCharacter(char.id)
                                    showCharacterPickerSheet = false
                                    Toast.makeText(context, "Switched to ${char.name}!", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                char.gradientColors.map { it.copy(alpha = 0.35f) }
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            char.gradientColors.firstOrNull()?.copy(alpha = 0.7f) ?: Color(0xFF8B5CF6),
                                            CircleShape
                                        )
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = spriteRes),
                                        contentDescription = char.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = char.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = char.emoji, fontSize = 14.sp)
                                    }
                                    Text(
                                        text = char.title,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) Color(0xFFA78BFA) else Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF8B5CF6)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showCharacterPickerSheet = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(context, OverlayService::class.java).apply {
                                    action = OverlayService.ACTION_START
                                    putExtra("character_id", currentCharacterId)
                                }
                                context.startService(intent)
                                Toast.makeText(context, "Floating Companion Launched!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🚀 Float Mascot", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            showCharacterPickerSheet = false
                            onOpenCharacters()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E243C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Gallery", color = Color(0xFFA78BFA), fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Routing Mode Bottom Sheet
    if (showRoutingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRoutingSheet = false },
            containerColor = Color(0xFF131625),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "🤖 AI Model Routing Mode",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Orbital automatically routes requests based on speed, reasoning depth, and rate limits.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(16.dp))

                listOf(
                    RoutingMode.AUTO to Pair("⚡ Auto Router (Smart Tiering)", "Automatically falls back through 24+ providers with cooldown tracking"),
                    RoutingMode.FAST to Pair("🚀 Fast Tier (Sub-second TTFT)", "Prioritizes Groq, Cerebras, Sambanova, and DeepInfra"),
                    RoutingMode.FRONTIER to Pair("🧠 Frontier Tier (Reasoning)", "Routes to Claude 3.5 Sonnet, GPT-4o, and Gemini 1.5 Pro"),
                    RoutingMode.PINNED to Pair("🎯 Pinned Provider", "Locks requests to your specifically selected provider")
                ).forEach { (mode, details) ->
                    val isSelected = currentRoutingMode == mode
                    Surface(
                        color = if (isSelected) Color(0xFF2E1B5B) else Color(0xFF1B1E30),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF8B5CF6) else Color(0xFF2B304C)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chatViewModel.onRoutingModeChanged(mode)
                                showRoutingSheet = false
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = details.first,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = details.second,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFFA78BFA)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Action Template Quick Sheets
    if (showActionTemplatesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionTemplatesSheet = false },
            containerColor = Color(0xFF131625)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "⚡ Executive Action Templates",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select a preset command to quickly run or combine device actions.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(16.dp))

                listOf(
                    "✉️ Open Gmail and summarize new emails",
                    "▶️ Open YouTube and search Kotlin tutorials",
                    "💬 Open WhatsApp and send a message",
                    "⏱️ Set a 15 minute focus timer",
                    "🔋 Check battery health and system storage",
                    "🌐 Search web for latest tech headlines"
                ).forEach { template ->
                    Surface(
                        color = Color(0xFF1B1E30),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chatViewModel.onActionTemplateClick(template)
                                showActionTemplatesSheet = false
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Text(text = template, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Legal Screen (Privacy Policy / Terms of Service)
    if (showLegalScreen) {
        LegalScreen(
            onBack = { showLegalScreen = false },
            initialTab = legalTab
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenGithub = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jaypal1046/Orbital"))
                context.startActivity(intent)
            }
        )
    }

    // Feedback & Bug Report Dialog (Direct GitHub Issues)
    if (showFeedbackDialog) {
        FeedbackDialog(
            initialErrorLog = feedbackInitialLog,
            activeCharacterName = characterName,
            activeProviderName = activeServingProvider ?: "Auto-Router",
            onDismiss = { showFeedbackDialog = false }
        )
    }

    // Auto-Update & Sideload Dialog
    val updateResult by chatViewModel.updateResult.collectAsState()
    updateResult?.let { res ->
        com.orbital.updater.ui.UpdateDialog(
            updateResult = res,
            downloader = chatViewModel.downloader,
            installer = chatViewModel.installer,
            onDismiss = chatViewModel::dismissUpdateDialog,
            onOtaPatchApplied = {
                Toast.makeText(context, "OTA Hot-Patch applied successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// Backward-compatible alias
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    onOpenKeys: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenAutomations: () -> Unit = {}
) {
    InAppChatScreen(
        chatViewModel = chatViewModel,
        onOpenKeys = onOpenKeys,
        onOpenCharacters = onOpenCharacters,
        onOpenAutomations = onOpenAutomations
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewInAppChatScreenContent() {
    Scaffold(
        topBar = {
            TopBar(
                characterId = "lumy",
                characterName = "Lumy (AI Companion)",
                currentRoutingMode = RoutingMode.AUTO,
                selectedPinnedProvider = null,
                onOpenDrawer = {},
                onOpenCharacters = {},
                onRoutingModeClick = {}
            )
        },
        containerColor = Color(0xFF0A0C14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MessageList(
                messages = listOf(
                    UiMessage(role = "user", content = "Open Gmail and check messages"),
                    UiMessage(
                        role = "assistant",
                        content = "I've opened Gmail for you.",
                        providerName = "Groq",
                        actionLabel = "⚡ Executed: Opened Gmail"
                    )
                ),
                characterName = "Lumy",
                characterId = "lumy",
                isStreaming = false,
                currentStreamContent = "",
                activeServingProvider = "Groq",
                modifier = Modifier.weight(1f),
                onCopy = {},
                onSpeak = {},
                onSuggestionClick = {},
                onAddStepToInput = {}
            )

            QuickSuggestionsBar(
                quickSuggestions = listOf("✉️ Open Gmail", "▶️ Open YouTube"),
                onSuggestionClick = {}
            )

            InputBar(
                inputText = "",
                onInputChange = {},
                isVoiceListening = false,
                onVoiceClick = {},
                isStreaming = false,
                onSendClick = {},
                onQuickTemplateClick = {}
            )
        }
    }
}
