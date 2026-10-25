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
import com.orbital.data.ProviderType
import com.orbital.data.ProviderRegistry
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
    var showBridgeScreen by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showDeviceAutomationDialog by remember { mutableStateOf(false) }
    var feedbackInitialLog by remember { mutableStateOf<String?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    if (showLegalScreen) {
        LegalScreen(
            onBack = { showLegalScreen = false },
            initialTab = legalTab
        )
        return
    }

    if (showBridgeScreen) {
        com.orbital.bridge.ui.LaptopBridgeScreen(
            bridgeClient = chatViewModel.bridgeClient,
            discoveryService = chatViewModel.discoveryService,
            onNavigateBack = { showBridgeScreen = false }
        )
        return
    }

    // Media & Document Pickers - Fully isolated on background IO dispatcher
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { chatViewModel.processAndAttachUri(context, it, isImage = false) }
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { chatViewModel.processAndAttachUri(context, it, isImage = false) }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { chatViewModel.processAndAttachUri(context, it, isImage = true) }
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
    val isOnline by chatViewModel.isOnline.collectAsState()
    val currentCharacter = remember(currentCharacterId) { Character.find(currentCharacterId) }
    val characterName = "${currentCharacter.name} (AI Companion)"

    var showRoutingSheet by remember { mutableStateOf(false) }
    var showActionTemplatesSheet by remember { mutableStateOf(false) }
    var showCharacterPickerSheet by remember { mutableStateOf(false) }
    var showSkillsSheet by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideNavDrawer(
                currentCharacterId = currentCharacterId,
                sessions = sessions,
                onSelectCharacter = { charId: String ->
                    chatViewModel.switchCharacter(charId)
                },
                onNewChat = {
                    chatViewModel.startNewChat()
                    Toast.makeText(context, "Started fresh chat session", Toast.LENGTH_SHORT).show()
                },
                onSelectSession = { sessionId: String -> chatViewModel.loadSession(sessionId) },
                onRenameSession = { sessionId: String, newTitle: String -> chatViewModel.renameSession(sessionId, newTitle) },
                onShareChat = { asExport: Boolean -> chatViewModel.shareCurrentChat(asExport) },
                onOpenKeys = onOpenKeys,
                onOpenCharacters = onOpenCharacters,
                onOpenAutomations = onOpenAutomations,
                onOpenSkills = { showSkillsSheet = true },
                onOpenRoutingMode = { showRoutingSheet = true },
                onOpenFeedback = {
                    feedbackInitialLog = null
                    showFeedbackDialog = true
                },
                onOpenLegal = { tab: LegalTab ->
                    showLegalScreen = true
                    legalTab = tab
                },
                onOpenAbout = { showAboutDialog = true },
                onCheckForUpdates = {
                    Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                    chatViewModel.checkForUpdates(force = true)
                },
                onOpenBridge = { showBridgeScreen = true },
                onOpenAccessibilityDisclosure = { showDeviceAutomationDialog = true },
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
            containerColor = OrbitalTokens.Background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                // Offline Warning Banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isOnline,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("📡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "You are currently offline",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFCA5A5)
                                    )
                                    Text(
                                        text = "Connect to Wi-Fi or cellular to chat with AI",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }

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
                        if (messages.isEmpty()) {
                            chatViewModel.sendMessage(suggestion)
                        } else {
                            chatViewModel.onSuggestionClick(suggestion)
                        }
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

    // AI Model & Routing Selection Bottom Sheet
    if (showRoutingSheet) {
        val activeProviders = remember { chatViewModel.getActiveProviders() }
        val otherActiveProviders = remember(activeProviders) {
            activeProviders.filter { it.type != ProviderType.PAISACHOICE }
        }
        val paisaChoiceInfo = remember { ProviderRegistry.getInfo(ProviderType.PAISACHOICE) }
        val currentPaisaModel = remember(selectedPinnedProvider) { chatViewModel.getSelectedModel(ProviderType.PAISACHOICE) }

        ModalBottomSheet(
            onDismissRequest = { showRoutingSheet = false },
            containerColor = Color(0xFF101323),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🤖 Select AI Model to Run",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Choose our cloud server, an active provider, or auto-routing",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = { showRoutingSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 1. Featured Built-In Cloud AI (PaisaChoice Server)
                item {
                    Text(
                        text = "⭐ BUILT-IN CLOUD AI (OUR SERVER)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF0F241C),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚡", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PaisaChoice Cloud Cluster",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Active · 4 Slots",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Model Option 1: qwen2.5:0.5b (Fast)
                            val is05bSelected = currentRoutingMode == RoutingMode.PINNED &&
                                    selectedPinnedProvider == ProviderType.PAISACHOICE &&
                                    currentPaisaModel == "qwen2.5:0.5b"

                            Surface(
                                color = if (is05bSelected) Color(0xFF064E3B) else Color(0xFF122E23),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (is05bSelected) Color(0xFF34D399) else Color(0xFF1A4635)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chatViewModel.onSelectModel(ProviderType.PAISACHOICE, "qwen2.5:0.5b")
                                        showRoutingSheet = false
                                        Toast.makeText(context, "Running PaisaChoice Qwen 2.5 0.5B (Fast)", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "qwen2.5:0.5b (Fast Mobile)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Blazing fast (~20 tok/sec) · Instant chat, FAQs, summaries",
                                            fontSize = 11.sp,
                                            color = Color(0xFF9AE6B4)
                                        )
                                    }
                                    if (is05bSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF34D399))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Model Option 2: qwen2.5:1.5b (Reasoning)
                            val is15bSelected = currentRoutingMode == RoutingMode.PINNED &&
                                    selectedPinnedProvider == ProviderType.PAISACHOICE &&
                                    currentPaisaModel == "qwen2.5:1.5b"

                            Surface(
                                color = if (is15bSelected) Color(0xFF064E3B) else Color(0xFF122E23),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (is15bSelected) Color(0xFF34D399) else Color(0xFF1A4635)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chatViewModel.onSelectModel(ProviderType.PAISACHOICE, "qwen2.5:1.5b")
                                        showRoutingSheet = false
                                        Toast.makeText(context, "Running PaisaChoice Qwen 2.5 1.5B (Reasoning)", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "qwen2.5:1.5b (Deep Reasoning)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Deeper reasoning (~12 tok/sec) · Complex logic, advice & planning",
                                            fontSize = 11.sp,
                                            color = Color(0xFF9AE6B4)
                                        )
                                    }
                                    if (is15bSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF34D399))
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Other Active Providers
                if (otherActiveProviders.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🌐 OTHER ACTIVE PROVIDERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    items(otherActiveProviders) { prov ->
                        val provModel = chatViewModel.getSelectedModel(prov.type)
                        val isProvSelected = currentRoutingMode == RoutingMode.PINNED && selectedPinnedProvider == prov.type

                        Surface(
                            color = if (isProvSelected) Color(0xFF231C4A) else Color(0xFF16192E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isProvSelected) Color(0xFF818CF8) else Color(0xFF262C4E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatViewModel.onSelectModel(prov.type, provModel)
                                    showRoutingSheet = false
                                    Toast.makeText(context, "Pinned to ${prov.displayName} ($provModel)", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = prov.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Active",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Model: $provModel",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFA5B4FC)
                                    )
                                }
                                if (isProvSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF818CF8))
                                }
                            }
                        }
                    }
                }

                // 3. Automated Routing Strategies
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚡ AUTOMATED ROUTING TIERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    listOf(
                        RoutingMode.AUTO to Pair("Auto-Router (Smart Failover)", "Automatically fails over across all active providers with cooldown tracking"),
                        RoutingMode.FAST to Pair("Fast Tier (Sub-Second)", "Prioritizes ultra-fast providers like Groq, Cerebras & PaisaChoice"),
                        RoutingMode.FRONTIER to Pair("Frontier Tier (Highest Reasoning)", "Prioritizes high-parameter reasoning models (Gemini Pro, Claude, DeepSeek)")
                    ).forEach { (mode, details) ->
                        val isModeSelected = currentRoutingMode == mode
                        Surface(
                            color = if (isModeSelected) Color(0xFF2E1B5B) else Color(0xFF16192E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isModeSelected) Color(0xFF8B5CF6) else Color(0xFF262C4E)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatViewModel.onRoutingModeChanged(mode)
                                    showRoutingSheet = false
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = details.first,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = details.second,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                if (isModeSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFA78BFA))
                                }
                            }
                        }
                    }
                }

                // 4. Footer: Key Management Navigation
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            showRoutingSheet = false
                            onOpenKeys()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔑 Configure All 24+ Providers & Models in Settings",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
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
                    "✦ Summarize current screen",
                    "✉️ Reply to active email (Smart Staging)",
                    "🔎 Search an installed app",
                    "💬 Draft a message",
                    "⏱️ Set a focus timer",
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

    // Prominent Device Automation Information Modal (Zero Accessibility)
    if (showDeviceAutomationDialog) {
        DeviceAutomationDisclosureDialog(
            onDismiss = { showDeviceAutomationDialog = false },
            onEnableClicked = { showDeviceAutomationDialog = false }
        )
    }

    // Auto-Update & Sideload Dialog
    val updateResult by chatViewModel.updateResult.collectAsState()
    updateResult?.let { res ->
        com.orbital.updater.ui.UpdateDialog(
            updateResult = res,
            onDismiss = chatViewModel::dismissUpdateDialog,
            onOtaPatchApplied = {
                Toast.makeText(context, "OTA Hot-Patch applied successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Mobile Skills Sheet (Antigravity Architecture)
    if (showSkillsSheet) {
        com.orbital.skills.ui.MobileSkillsSheet(
            skillRegistry = chatViewModel.skillRegistry,
            onDismiss = { showSkillsSheet = false },
            onRunSkillPrompt = { prompt ->
                chatViewModel.sendMessage(prompt)
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
                    UiMessage(role = "user", content = "Summarize the current screen"),
                    UiMessage(
                        role = "assistant",
                        content = "I can summarize the current screen for you.",
                        providerName = "Groq",
                        actionLabel = "⚡ Executed: Screen summary"
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
                quickSuggestions = listOf("✦ Summarize current screen", "⚙️ Check device status"),
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
