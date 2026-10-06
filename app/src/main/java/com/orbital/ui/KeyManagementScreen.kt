package com.orbital.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orbital.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ProviderFilter {
    ALL, HEALTHY, ISSUES, DISABLED
}

enum class DashboardTab {
    PROVIDERS, MODELS_QUOTA, QUOTA_SIGNALS, AUTO_ROUTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagementScreen(
    secureStorage: SecureStorage,
    llmRepository: LlmRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    isOnboarding: Boolean = true
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(DashboardTab.PROVIDERS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ProviderFilter.ALL) }
    var expandedProvider by remember { mutableStateOf<ProviderType?>(null) }
    var providerKeys by remember {
        mutableStateOf(
            ProviderRegistry.allProviders.associate { it.type to (secureStorage.getProviderApiKey(it.type.name) ?: "") }
        )
    }
    var providerEnabledStates by remember {
        mutableStateOf(
            ProviderRegistry.allProviders.associate { it.type to (secureStorage.isProviderEnabled(it.type.name)) }
        )
    }
    var providerSelectedModels by remember {
        mutableStateOf(
            ProviderRegistry.allProviders.associate { it.type to (secureStorage.getProviderSelectedModel(it.type.name) ?: it.defaultModel) }
        )
    }
    var providerDiscoveredModels by remember {
        mutableStateOf(
            ProviderRegistry.allProviders.associate { it.type to llmRepository.getAvailableModels(it.type) }
        )
    }
    var providerModelScopes by remember {
        mutableStateOf(
            ProviderRegistry.allProviders.associate { it.type to secureStorage.getProviderModelScope(it.type.name) }
        )
    }
    var editingModelsProvider by remember { mutableStateOf<ProviderInfo?>(null) }
    var webViewPortalProvider by remember { mutableStateOf<ProviderInfo?>(null) }
    var exportingProvider by remember { mutableStateOf<ProviderInfo?>(null) }
    var importingPackageJson by remember { mutableStateOf<String?>(null) }
    var pendingChromeProvider by remember { mutableStateOf<ProviderInfo?>(null) }
    var chromeCapturedToken by remember { mutableStateOf<String?>(null) }
    var isTestingCapturedToken by remember { mutableStateOf(false) }
    var selectedTargetProvider by remember(pendingChromeProvider) { mutableStateOf(pendingChromeProvider) }
    var discoveringMap by remember { mutableStateOf(mapOf<ProviderType, Boolean>()) }
    var isCheckingAll by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val currentPendingProvider by rememberUpdatedState(pendingChromeProvider)
    val currentProviderKeys by rememberUpdatedState(providerKeys)
    val currentExpandedProvider by rememberUpdatedState(expandedProvider)

    fun checkAndCaptureClipboard(isExplicitAction: Boolean = false) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val raw = clipData.getItemAt(0)?.text?.toString()
                    if (!raw.isNullOrBlank()) {
                        val text = raw.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
                        
                        // Check if text is an .orbtoken JSON package
                        if (text.startsWith("{") && (text.contains("\"lockType\"") || text.contains("\"version\"") || text.contains("\"encryptedPayload\""))) {
                            importingPackageJson = text
                            Toast.makeText(context, "📦 Detected .orbtoken package from clipboard!", Toast.LENGTH_SHORT).show()
                            return
                        }
                        
                        if (text.length in 8..500) {
                            val alreadyConfigured = currentProviderKeys.values.any { it == text }
                            if (!alreadyConfigured || isExplicitAction) {
                                val target: ProviderInfo? = currentPendingProvider
                                    ?: currentExpandedProvider?.let { expType -> ProviderRegistry.allProviders.find { it.type == expType } }
                                    ?: ProviderRegistry.allProviders.find { info ->
                                        when (info.type) {
                                            ProviderType.GEMINI -> text.startsWith("AIzaSy")
                                            ProviderType.GROQ -> text.startsWith("gsk_")
                                            ProviderType.OPENROUTER -> text.startsWith("sk-or-")
                                            ProviderType.GITHUB_MODELS -> text.startsWith("ghp_") || text.startsWith("github_pat_")
                                            ProviderType.MISTRAL -> text.length == 32 && text.all { it.isLetterOrDigit() }
                                            ProviderType.CEREBRAS -> text.startsWith("csk-")
                                            ProviderType.COHERE -> text.length in 36..44
                                            ProviderType.HUGGINGFACE -> text.startsWith("hf_")
                                            else -> false
                                        }
                                    }
                                    ?: ProviderRegistry.allProviders.firstOrNull()

                                if (target != null) {
                                    val currentKey = currentProviderKeys[target.type] ?: ""
                                    if (text != currentKey || isExplicitAction) {
                                        pendingChromeProvider = target
                                        selectedTargetProvider = target
                                        chromeCapturedToken = text
                                        return
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isExplicitAction) {
                Toast.makeText(context, "No API key found in clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (isExplicitAction) {
                Toast.makeText(context, "Could not access clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            checkAndCaptureClipboard(isExplicitAction = false)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    checkAndCaptureClipboard(isExplicitAction = false)
                    kotlinx.coroutines.delay(350)
                    checkAndCaptureClipboard(isExplicitAction = false)
                    kotlinx.coroutines.delay(700)
                    checkAndCaptureClipboard(isExplicitAction = false)
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(clipListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            clipboard.removePrimaryClipChangedListener(clipListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    importingPackageJson = content
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val configuredCount = providerKeys.count { it.value.isNotBlank() }
    val totalCount = ProviderRegistry.allProviders.size

    var showAllInOnboarding by remember { mutableStateOf(false) }

    val filteredProviders = ProviderRegistry.allProviders.filter { info ->
        val key = providerKeys[info.type] ?: ""
        val isEnabled = providerEnabledStates[info.type] ?: true
        val status = llmRepository.getProviderStatus(info.type)

        val matchesSearch = info.displayName.contains(searchQuery, ignoreCase = true) ||
                info.models.any { it.contains(searchQuery, ignoreCase = true) }

        val matchesFilter = when (selectedFilter) {
            ProviderFilter.ALL -> true
            ProviderFilter.HEALTHY -> isEnabled && key.isNotBlank() && status == ProviderState.AVAILABLE
            ProviderFilter.ISSUES -> isEnabled && key.isNotBlank() && (status == ProviderState.IN_COOLDOWN || status == ProviderState.UNAVAILABLE)
            ProviderFilter.DISABLED -> !isEnabled
        }
        matchesSearch && matchesFilter
    }

    Scaffold(
        topBar = {
            if (isOnboarding) {
                OnboardingStepProgressHeader(
                    currentStepIndex = 2,
                    totalSteps = 4,
                    title = "Choose AI Brain",
                    subtitle = "Connect a free LLM provider to power Orbital. Free tiers included.",
                    onBack = onBack,
                    actions = {
                        // Action 1: Paste from Clipboard
                        IconButton(
                            onClick = { checkAndCaptureClipboard(isExplicitAction = true) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from Clipboard",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Action 2: Import .orbtoken File
                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Import .orbtoken",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Action 3: Export / Download .orbtoken (if keys exist)
                        if (configuredCount > 0) {
                            IconButton(
                                onClick = {
                                    val firstConfigured = ProviderRegistry.allProviders.firstOrNull { (providerKeys[it.type] ?: "").isNotBlank() }
                                    if (firstConfigured != null) {
                                        exportingProvider = firstConfigured
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Export .orbtoken",
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Keys & Providers",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "$configuredCount of $totalCount providers configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        // Action 1: Paste from Clipboard
                        IconButton(
                            onClick = { checkAndCaptureClipboard(isExplicitAction = true) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from Clipboard",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                        // Action 2: Import .orbtoken File
                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Import .orbtoken",
                                tint = Color(0xFF34D399)
                            )
                        }
                        // Action 3: Export / Download .orbtoken (if keys exist)
                        if (configuredCount > 0) {
                            IconButton(
                                onClick = {
                                    val firstConfigured = ProviderRegistry.allProviders.firstOrNull { (providerKeys[it.type] ?: "").isNotBlank() }
                                    if (firstConfigured != null) {
                                        exportingProvider = firstConfigured
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Export .orbtoken",
                                    tint = Color(0xFFA78BFA)
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                isCheckingAll = true
                                CoroutineScope(Dispatchers.IO).launch {
                                    ProviderRegistry.allProviders.forEach { p ->
                                        val key = providerKeys[p.type] ?: ""
                                        if (key.isNotBlank()) {
                                            llmRepository.testProvider(p.type, key)
                                        }
                                    }
                                    CoroutineScope(Dispatchers.Main).launch {
                                        isCheckingAll = false
                                        Toast.makeText(context, "Provider health check completed!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            if (isCheckingAll) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF8B5CF6))
                            } else {
                                Text("Check all", color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
                )
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF0F1322),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFF1E243D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E3856))
                        ) {
                            Text(if (isOnboarding) "Skip for now" else "Back", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        }
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.weight(1.4f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (configuredCount > 0) Color(0xFF10B981) else Color(0xFF7C3AED)
                            )
                        ) {
                            Text(
                                text = if (isOnboarding) {
                                    if (configuredCount > 0) "Continue ($configuredCount Ready) →" else "Continue →"
                                } else "Done",
                                color = if (configuredCount > 0 && isOnboarding) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF0A0C14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (isOnboarding) {
                // Onboarding Hero Card & Quick Actions with glowing mesh gradient
                Surface(
                    color = Color(0xFF13172C),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF263056)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF7C3AED).copy(alpha = 0.3f))
                                        .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "100% Free LLM Tiers",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$configuredCount Active",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Chrome Login' to use your existing Google AI Studio or GitHub account. Copy your key and Orbital detects it automatically.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { checkAndCaptureClipboard(isExplicitAction = true) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Key", fontSize = 11.5.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import File", fontSize = 11.5.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                            }
                            if (configuredCount > 0) {
                                OutlinedButton(
                                    onClick = {
                                        val first = ProviderRegistry.allProviders.firstOrNull { (providerKeys[it.type] ?: "").isNotBlank() }
                                        if (first != null) exportingProvider = first
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.6f)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export", fontSize = 11.5.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Top Tab Navigation Bar in Settings Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141829))
                        .border(1.dp, Color(0xFF222842), RoundedCornerShape(12.dp))
                        .padding(3.dp)
                ) {
                    DashboardTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val label = when (tab) {
                            DashboardTab.PROVIDERS -> "Keys"
                            DashboardTab.MODELS_QUOTA -> "Quotas"
                            DashboardTab.QUOTA_SIGNALS -> "Signals"
                            DashboardTab.AUTO_ROUTER -> "Router"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF7C3AED) else Color.Transparent)
                                .clickable { currentTab = tab }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            when (if (isOnboarding) DashboardTab.PROVIDERS else currentTab) {
                DashboardTab.PROVIDERS -> {
                    if (!isOnboarding || showAllInOnboarding) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search providers or models...", color = Color(0xFF64748B), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFF222842),
                                focusedContainerColor = Color(0xFF131625),
                                unfocusedContainerColor = Color(0xFF131625),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Filter Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProviderFilter.values().forEach { filter ->
                                val isSelected = selectedFilter == filter
                                val label = when (filter) {
                                    ProviderFilter.ALL -> "All ($totalCount)"
                                    ProviderFilter.HEALTHY -> "Ready ($configuredCount)"
                                    ProviderFilter.ISSUES -> "Issues"
                                    ProviderFilter.DISABLED -> "Disabled"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF7C3AED) else Color(0xFF161928))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFA78BFA) else Color(0xFF242B45),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedFilter = filter }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    val displayProviders = if (isOnboarding && !showAllInOnboarding) {
                        val topTypes = listOf(ProviderType.GEMINI, ProviderType.GROQ, ProviderType.GITHUB_MODELS, ProviderType.OPENROUTER)
                        ProviderRegistry.allProviders.filter { p -> topTypes.contains(p.type) || (providerKeys[p.type] ?: "").isNotBlank() }
                    } else {
                        filteredProviders
                    }

                    // Provider List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayProviders) { info ->
                            val key = providerKeys[info.type] ?: ""
                            val isEnabled = providerEnabledStates[info.type] ?: true
                            val isExpanded = expandedProvider == info.type
                            val status = llmRepository.getProviderStatus(info.type)
                            val selectedModel = providerSelectedModels[info.type] ?: info.defaultModel
                            val availableModels = providerDiscoveredModels[info.type] ?: info.models
                            val isDiscovering = discoveringMap[info.type] ?: false

                            val currentScope = providerModelScopes[info.type]

                            ProviderConfigurationCard(
                                info = info,
                                apiKey = key,
                                isEnabled = isEnabled,
                                isExpanded = isExpanded,
                                status = status,
                                selectedModel = selectedModel,
                                availableModels = availableModels,
                                isDiscoveringModels = isDiscovering,
                                modelScope = currentScope,
                                onEditModels = { editingModelsProvider = info },
                                onToggleEnabled = { enabled ->
                                    providerEnabledStates = providerEnabledStates + (info.type to enabled)
                                    secureStorage.saveProviderEnabled(info.type.name, enabled)
                                },
                                onSelectModel = { modelName ->
                                    providerSelectedModels = providerSelectedModels + (info.type to modelName)
                                    llmRepository.setProviderModel(info.type, modelName)
                                },
                                onDiscoverModels = {
                                    discoveringMap = discoveringMap + (info.type to true)
                                    llmRepository.discoverCatalogModels(info.type, key) { discoveredList ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            discoveringMap = discoveringMap + (info.type to false)
                                            val ids = discoveredList.map { it.modelId }
                                            providerDiscoveredModels = providerDiscoveredModels + (info.type to ids)
                                            Toast.makeText(context, "${info.displayName}: ${discoveredList.size} models discovered!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onToggleExpand = {
                                    expandedProvider = if (isExpanded) null else info.type
                                },
                                onOpenPortal = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.portalUrl))
                                    context.startActivity(intent)
                                },
                                onOpenChromePortal = {
                                    pendingChromeProvider = info
                                    selectedTargetProvider = info
                                    Toast.makeText(context, "Opening ${info.displayName}... Copy your API key in Chrome & return here to auto-connect!", Toast.LENGTH_LONG).show()
                                    com.orbital.util.CustomTabPortalHelper.openPortalCustomTab(context, info.portalUrl)
                                },
                                onOpenInAppPortal = {
                                    webViewPortalProvider = info
                                },
                                onExportToken = {
                                    exportingProvider = info
                                },
                                onRemoveKey = {
                                    providerKeys = providerKeys + (info.type to "")
                                    secureStorage.saveProviderApiKey(info.type.name, "")
                                    llmRepository.updateProviderKey(info.type, "")
                                    Toast.makeText(context, "${info.displayName}: key removed", Toast.LENGTH_SHORT).show()
                                },
                                onTestKey = { candidateKey ->
                                    llmRepository.testProvider(info.type, candidateKey) { success, error ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            if (success) {
                                                providerKeys = providerKeys + (info.type to candidateKey)
                                                secureStorage.saveProviderApiKey(info.type.name, candidateKey)
                                                llmRepository.updateProviderKey(info.type, candidateKey)
                                                llmRepository.discoverCatalogModels(info.type, candidateKey) { discovered ->
                                                    CoroutineScope(Dispatchers.Main).launch {
                                                        providerDiscoveredModels = providerDiscoveredModels + (info.type to discovered.map { it.modelId })
                                                    }
                                                }
                                                Toast.makeText(context, "${info.displayName}: connected", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "${info.displayName}: connection failed${error?.let { " · $it" } ?: ""}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        if (isOnboarding && !showAllInOnboarding) {
                            item {
                                OutlinedButton(
                                    onClick = { showAllInOnboarding = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Browse All Providers ($totalCount total) ▾",
                                        color = Color(0xFFA78BFA),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                DashboardTab.MODELS_QUOTA -> {
                    ModelsQuotaDashboard(
                        providerEnabledStates = providerEnabledStates,
                        onToggleProvider = { type, enabled ->
                            providerEnabledStates = providerEnabledStates + (type to enabled)
                            secureStorage.saveProviderEnabled(type.name, enabled)
                        }
                    )
                }

                DashboardTab.QUOTA_SIGNALS -> {
                    // Live Quota Signals Matrix
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "REAL-TIME QUOTA SIGNALS & STATUS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(ProviderRegistry.allProviders) { info ->
                            val key = providerKeys[info.type] ?: ""
                            val isEnabled = providerEnabledStates[info.type] ?: true
                            val status = llmRepository.getProviderStatus(info.type)
                            val currentModel = providerSelectedModels[info.type] ?: info.defaultModel

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141829)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = info.displayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                        val (pillColor, pillText) = when {
                                            !isEnabled -> Pair(Color(0xFF64748B), "Disabled")
                                            key.isBlank() -> Pair(Color(0xFF64748B), "No Key")
                                            status == ProviderState.IN_COOLDOWN -> Pair(Color(0xFFF59E0B), "Cooldown (429)")
                                            status == ProviderState.UNAVAILABLE -> Pair(Color(0xFFEF4444), "Issue")
                                            else -> Pair(Color(0xFF10B981), "Healthy")
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(pillColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(text = pillText, color = pillColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Quota Allowance: ${info.quotaDescription}", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Active Model: $currentModel", color = Color(0xFFA7F3D0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                DashboardTab.AUTO_ROUTER -> {
                    // Auto Router & Failover Priority Settings
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "MULTI-PROVIDER AUTO-FAILOVER CHAIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "When rate-limited (429) or on server errors, Orbital instantly routes your prompt to the next healthy provider in sequence without dropping your session.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141829)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = "Active Failover Sequence", fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ProviderConfig.defaultProviderOrder.forEachIndexed { index, type ->
                                        val p = ProviderRegistry.getInfo(type)
                                        val hasKey = (providerKeys[type] ?: "").isNotBlank()
                                        val activeModel = providerSelectedModels[type] ?: p.defaultModel
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(text = "${index + 1}.", color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = p.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                                                Text(text = "Model: $activeModel", color = Color(0xFF64748B), fontSize = 11.sp)
                                            }
                                            Text(
                                                text = if (hasKey) "Ready" else "No Key",
                                                color = if (hasKey) Color(0xFF10B981) else Color(0xFF64748B),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141829)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = "Embedded Ktor Server", fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Exposes an OpenAI-compatible endpoint on http://127.0.0.1:3001 for local tools and scripts.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Root-Level In-App WebView Portal
    if (webViewPortalProvider != null) {
        val activeProvider = webViewPortalProvider!!
        TokenPortalWebViewSheet(
            providerInfo = activeProvider,
            onDismiss = { webViewPortalProvider = null },
            onTokenCaptured = { capturedKey ->
                val target = activeProvider
                webViewPortalProvider = null
                llmRepository.testProvider(target.type, capturedKey) { success, error ->
                    CoroutineScope(Dispatchers.Main).launch {
                        if (success) {
                            providerKeys = providerKeys + (target.type to capturedKey)
                            secureStorage.saveProviderApiKey(target.type.name, capturedKey)
                            llmRepository.updateProviderKey(target.type, capturedKey)
                            Toast.makeText(context, "${target.displayName}: Token Verified & Saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "${target.displayName}: Token test failed: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // Root-Level Export .orbtoken Dialog
    if (exportingProvider != null) {
        val activeProvider = exportingProvider!!
        val currentKey = providerKeys[activeProvider.type] ?: ""
        val selectedModel = providerSelectedModels[activeProvider.type]
        ExportTokenDialog(
            providerInfo = activeProvider,
            apiKey = currentKey,
            selectedModel = selectedModel,
            onDismiss = { exportingProvider = null }
        )
    }

    // Root-Level Import .orbtoken Dialog
    if (importingPackageJson != null) {
        ImportTokenDialog(
            packageJson = importingPackageJson!!,
            onDismiss = { importingPackageJson = null },
            onImportSuccess = { payload ->
                try {
                    val pType = ProviderType.valueOf(payload.provider)
                    providerKeys = providerKeys + (pType to payload.apiKey)
                    secureStorage.saveProviderApiKey(payload.provider, payload.apiKey)
                    llmRepository.updateProviderKey(pType, payload.apiKey)
                    if (payload.selectedModel != null) {
                        providerSelectedModels = providerSelectedModels + (pType to payload.selectedModel)
                        secureStorage.saveProviderSelectedModel(payload.provider, payload.selectedModel)
                    }
                    Toast.makeText(context, "Imported token for ${payload.provider}!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    secureStorage.saveProviderApiKey(payload.provider, payload.apiKey)
                    Toast.makeText(context, "Saved token for ${payload.provider}", Toast.LENGTH_SHORT).show()
                }
                importingPackageJson = null
            }
        )
    }

    // Root-Level Captured Token from Chrome / Clipboard Alert Dialog
    if (chromeCapturedToken != null) {
        val capturedKey = chromeCapturedToken!!
        val activeTarget = selectedTargetProvider ?: pendingChromeProvider ?: ProviderRegistry.allProviders.first()

        AlertDialog(
            onDismissRequest = {
                chromeCapturedToken = null
                pendingChromeProvider = null
            },
            containerColor = Color(0xFF13172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔑 Key Captured from Clipboard!", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Orbital detected a copied API key. Select provider to connect:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProviderRegistry.allProviders.forEach { p ->
                            val isSel = activeTarget.type == p.type
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedTargetProvider = p },
                                label = { Text(p.displayName, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF7C3AED),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF0F111E),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF232A44)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("DETECTED KEY", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            val masked = if (capturedKey.length > 12) {
                                capturedKey.take(6) + "••••••••••••" + capturedKey.takeLast(4)
                            } else {
                                "••••••••••••"
                            }
                            Text(masked, fontSize = 13.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isTestingCapturedToken = true
                        llmRepository.testProvider(activeTarget.type, capturedKey) { success, error ->
                            CoroutineScope(Dispatchers.Main).launch {
                                isTestingCapturedToken = false
                                if (success) {
                                    providerKeys = providerKeys + (activeTarget.type to capturedKey)
                                    secureStorage.saveProviderApiKey(activeTarget.type.name, capturedKey)
                                    llmRepository.updateProviderKey(activeTarget.type, capturedKey)
                                    llmRepository.discoverCatalogModels(activeTarget.type, capturedKey) { discovered ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            providerDiscoveredModels = providerDiscoveredModels + (activeTarget.type to discovered.map { it.modelId })
                                        }
                                    }
                                    Toast.makeText(context, "🎉 ${activeTarget.displayName} connected successfully!", Toast.LENGTH_SHORT).show()
                                    chromeCapturedToken = null
                                    pendingChromeProvider = null
                                } else {
                                    Toast.makeText(context, "Connection test failed: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isTestingCapturedToken,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    if (isTestingCapturedToken) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Connect ${activeTarget.displayName}", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        chromeCapturedToken = null
                        pendingChromeProvider = null
                    }
                ) {
                    Text("Ignore", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // Root-Level EditModelsDialog popup
    if (editingModelsProvider != null) {
        val activeInfo = editingModelsProvider!!
        val activeKey = providerKeys[activeInfo.type] ?: ""
        val candidates = llmRepository.getAvailableCatalogModels(activeInfo.type)
        val scope = providerModelScopes[activeInfo.type]

        EditModelsDialog(
            providerInfo = activeInfo,
            currentCandidates = candidates,
            currentScope = scope,
            onDismiss = { editingModelsProvider = null },
            onSave = { newScope ->
                providerModelScopes = providerModelScopes + (activeInfo.type to newScope)
                secureStorage.saveProviderModelScope(activeInfo.type.name, newScope)
                editingModelsProvider = null
                Toast.makeText(
                    context,
                    if (newScope == null) "${activeInfo.displayName}: All models scope active" else "${activeInfo.displayName}: Scoped to ${newScope.size} models",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onCheckCatalogUpdates = {
                var result = candidates
                llmRepository.discoverCatalogModels(activeInfo.type, activeKey) { updated ->
                    result = updated
                }
                result
            }
        )
    }
}

@Composable
private fun ProviderConfigurationCard(
    info: ProviderInfo,
    apiKey: String,
    isEnabled: Boolean,
    isExpanded: Boolean,
    status: ProviderState,
    selectedModel: String,
    availableModels: List<String>,
    isDiscoveringModels: Boolean,
    modelScope: Set<String>?,
    onEditModels: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenPortal: () -> Unit,
    onOpenChromePortal: () -> Unit,
    onOpenInAppPortal: () -> Unit,
    onExportToken: () -> Unit,
    onRemoveKey: () -> Unit,
    onTestKey: (String) -> Unit
) {
    val context = LocalContext.current
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var revealKey by remember { mutableStateOf(false) }
    var confirmRemoval by remember { mutableStateOf(false) }
    val configured = apiKey.isNotBlank()
    val (statusLabel, statusColor, statusBg) = when {
        !isEnabled -> Triple("Disabled", Color(0xFF94A3B8), Color(0xFF1E243A))
        !configured -> Triple("Not connected", Color(0xFF94A3B8), Color(0xFF181D30))
        status == ProviderState.IN_COOLDOWN -> Triple("Rate limited", Color(0xFFF59E0B), Color(0xFF451A03))
        status == ProviderState.UNAVAILABLE -> Triple("Needs attention", Color(0xFFEF4444), Color(0xFF450A0A))
        else -> Triple("Connected", Color(0xFF34D399), Color(0xFF064E3B).copy(alpha = 0.6f))
    }

    val brandGradient = when (info.type) {
        ProviderType.GEMINI -> listOf(Color(0xFF4285F4), Color(0xFF1A73E8))
        ProviderType.GROQ -> listOf(Color(0xFFF55036), Color(0xFFEA580C))
        ProviderType.CEREBRAS -> listOf(Color(0xFF10B981), Color(0xFF059669))
        ProviderType.OPENROUTER -> listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
        ProviderType.MISTRAL -> listOf(Color(0xFFFF7000), Color(0xFFD97706))
        ProviderType.GITHUB_MODELS -> listOf(Color(0xFF22C55E), Color(0xFF16A34A))
        ProviderType.NVIDIA_NIM -> listOf(Color(0xFF76B900), Color(0xFF65A30D))
        ProviderType.ZHIPU -> listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
        ProviderType.ALPHAOX -> listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
        ProviderType.HUGGINGFACE -> listOf(Color(0xFFFFD21E), Color(0xFFEAB308))
        else -> listOf(Color(0xFF334155), Color(0xFF1E293B))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (configured && isEnabled) Color(0xFF12162B) else Color(0xFF0F1222)),
        border = BorderStroke(
            1.dp,
            if (isExpanded) Color(0xFF8B5CF6).copy(alpha = 0.7f)
            else if (configured && isEnabled) Color(0xFF263056)
            else Color(0xFF1C2238)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                // Provider Avatar with Brand Gradient
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (configured) brandGradient else listOf(Color(0xFF1E2540), Color(0xFF15192C))
                            )
                        )
                        .border(
                            1.dp,
                            if (configured) Color.White.copy(alpha = 0.3f) else Color(0xFF2A3456),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info.displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = info.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Color(0xFF94A3B8),
                            fontSize = 14.5.sp
                        )

                        // Status Pill Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 10.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = info.quotaDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.width(6.dp))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = configured,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF7C3AED),
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF1C2238),
                        uncheckedThumbColor = Color(0xFF64748B)
                    )
                )

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = Color(0xFF222B48))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (configured) "Connection & Key" else "1. Connect API Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "The key is encrypted on your hardware keychain and never shared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(info.keyPlaceholder, color = Color(0xFF64748B), fontSize = 12.5.sp) },
                        visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            if (cb != null && cb.hasPrimaryClip()) {
                                                val clip = cb.primaryClip?.getItemAt(0)?.text?.toString()
                                                if (!clip.isNullOrBlank()) {
                                                    val clean = clip.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
                                                    keyInput = clean
                                                    Toast.makeText(context, "Pasted into ${info.displayName}!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not access clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                TextButton(onClick = { revealKey = !revealKey }) {
                                    Text(if (revealKey) "Hide" else "Show", fontSize = 11.sp, color = Color(0xFFA78BFA))
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF263056),
                            focusedContainerColor = Color(0xFF0C0F1D),
                            unfocusedContainerColor = Color(0xFF0C0F1D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenChromePortal,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text("Chrome Login 🚀", fontSize = 11.sp, maxLines = 1, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onOpenInAppPortal,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.6f)),
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text("In-App 📱", fontSize = 11.sp, maxLines = 1, color = Color(0xFFA78BFA), fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { onTestKey(keyInput.trim()) },
                            enabled = keyInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text("Connect", fontSize = 11.5.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (configured) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "2. Active Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$selectedModel",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onEditModels, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                Text(if (modelScope == null) "⚙ Scope models" else "⚙ (${modelScope.size} scoped)", fontSize = 11.sp, color = Color(0xFFA78BFA))
                            }
                            TextButton(onClick = onDiscoverModels, enabled = !isDiscoveringModels, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                Text(if (isDiscoveringModels) "Checking…" else "🔄 Refresh", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }
                        val models = if (modelScope.isNullOrEmpty()) availableModels else availableModels.filter(modelScope::contains).ifEmpty { availableModels }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            models.forEach { model ->
                                val isSelected = model == selectedModel
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF7C3AED) else Color(0xFF1A1F36))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFA78BFA) else Color(0xFF283256),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectModel(model) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = model,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "3. Backup & Security",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onExportToken,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export .orbtoken", color = Color(0xFF10B981), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = { confirmRemoval = true }, contentPadding = PaddingValues(0.dp)) {
                                Text("Remove key", color = Color(0xFFEF4444), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("Remove ${info.displayName} key?") },
            text = { Text("This disconnects the provider from this device. You can add the key again later.") },
            confirmButton = { TextButton(onClick = { onRemoveKey(); confirmRemoval = false }) { Text("Remove", color = OrbitalTokens.Error) } },
            dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } }
        )
    }
}

data class QuotaModelData(
    val rank: Int,
    val name: String,
    val platform: String,
    val providerType: ProviderType,
    val monthlyQuota: String,
    val rpdQuota: String,
    val context: String,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val reliability: Int,
    val speed: Int,
    val intelligence: Int,
    val compositeScore: Float,
    val color: Color
)

@Composable
fun ModelsQuotaDashboard(
    providerEnabledStates: Map<ProviderType, Boolean>,
    onToggleProvider: (ProviderType, Boolean) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf("Smartest") }
    var selectedContextFilter by remember { mutableStateOf("Any context") }
    var filterVision by remember { mutableStateOf(false) }
    var filterTools by remember { mutableStateOf(false) }
    var showAllLegend by remember { mutableStateOf(false) }

    val allQuotaModels = remember {
        ProviderRegistry.allProviders.flatMapIndexed { providerIndex, provider ->
            provider.catalogModels.mapIndexed { modelIndex, model ->
                val supportsVision = model.displayName.contains("Vision", ignoreCase = true) ||
                        model.displayName.contains("Gemini", ignoreCase = true) ||
                        model.displayName.contains("Gemma", ignoreCase = true) ||
                        model.displayName.contains("AlphaOx", ignoreCase = true)
                val supportsTools = true
                val rel = (80 + ((model.modelId.hashCode() and 0x7FFFFFFF) % 20))
                val spd = (70 + ((model.displayName.hashCode() and 0x7FFFFFFF) % 30))
                val intel = when (model.sizeLabel) {
                    "Frontier" -> 95 + ((model.modelId.hashCode() and 0x7FFFFFFF) % 5)
                    "Large" -> 88 + ((model.modelId.hashCode() and 0x7FFFFFFF) % 7)
                    else -> 75 + ((model.modelId.hashCode() and 0x7FFFFFFF) % 15)
                }
                val compositeScore = (rel * 0.35f + spd * 0.10f + intel * 0.55f) / 100f
                val providerColor = when (provider.type) {
                    ProviderType.GEMINI -> Color(0xFF4285F4)
                    ProviderType.GROQ -> Color(0xFFF55036)
                    ProviderType.CEREBRAS -> Color(0xFF10B981)
                    ProviderType.MISTRAL -> Color(0xFFFF7000)
                    ProviderType.NVIDIA_NIM -> Color(0xFF76B900)
                    ProviderType.OPENROUTER -> Color(0xFF6366F1)
                    ProviderType.GITHUB_MODELS -> Color(0xFF22C55E)
                    ProviderType.ZHIPU -> Color(0xFF3B82F6)
                    ProviderType.ALPHAOX -> Color(0xFF8B5CF6)
                    ProviderType.HUGGINGFACE -> Color(0xFFFFD21E)
                    ProviderType.KILO -> Color(0xFFA855F7)
                    ProviderType.OVH -> Color(0xFF0050D7)
                    else -> Color(0xFF64748B)
                }
                QuotaModelData(
                    rank = 0,
                    name = model.displayName,
                    platform = provider.displayName.lowercase().replace(" ", ""),
                    providerType = provider.type,
                    monthlyQuota = provider.quotaDescription.split("·").firstOrNull()?.trim() ?: "Active",
                    rpdQuota = provider.quotaDescription.split("·").getOrNull(1)?.trim() ?: provider.quotaDescription,
                    context = model.contextWindow,
                    supportsVision = supportsVision,
                    supportsTools = supportsTools,
                    reliability = rel,
                    speed = spd,
                    intelligence = intel,
                    compositeScore = String.format(java.util.Locale.US, "%.3f", compositeScore).toFloat(),
                    color = providerColor
                )
            }
        }.sortedByDescending { it.compositeScore }.mapIndexed { idx, it -> it.copy(rank = idx + 1) }
    }

    val filteredList = allQuotaModels.filter { model ->
        val matchesContext = when (selectedContextFilter) {
            "32K+" -> model.context.contains("32K") || model.context.contains("33K") || model.context.contains("128K") || model.context.contains("131K") || model.context.contains("1M") || model.context.contains("2M")
            "128K+" -> model.context.contains("128K") || model.context.contains("131K") || model.context.contains("1M") || model.context.contains("2M")
            "1M+" -> model.context.contains("1M") || model.context.contains("2M")
            else -> true
        }
        val matchesVision = !filterVision || model.supportsVision
        val matchesTools = !filterTools || model.supportsTools
        matchesContext && matchesVision && matchesTools
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Monthly Token Budget Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141829)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C45)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly token budget",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "612.0M remaining · 97% of 632.0M · 20.1M used",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Multi-Segmented Stacked Progress Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF232840))
                    ) {
                        Box(modifier = Modifier.weight(0.35f).fillMaxHeight().background(Color(0xFF4285F4))) // Google
                        Box(modifier = Modifier.weight(0.18f).fillMaxHeight().background(Color(0xFFF55036))) // Groq
                        Box(modifier = Modifier.weight(0.15f).fillMaxHeight().background(Color(0xFF10B981))) // Cerebras
                        Box(modifier = Modifier.weight(0.12f).fillMaxHeight().background(Color(0xFF76B900))) // Nvidia
                        Box(modifier = Modifier.weight(0.08f).fillMaxHeight().background(Color(0xFFFF7000))) // Mistral
                        Box(modifier = Modifier.weight(0.07f).fillMaxHeight().background(Color(0xFF3B82F6))) // Zhipu
                        Box(modifier = Modifier.weight(0.05f).fillMaxHeight().background(Color(0xFFA855F7))) // Kilo
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Quota Legend
                    val legendList = if (showAllLegend) allQuotaModels else allQuotaModels.take(6)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        legendList.chunked(2).forEach { pair ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                pair.forEach { model ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(model.color)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = model.name,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = model.monthlyQuota,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showAllLegend) "Show less ▲" else "+14 with no published quota · Show all 20 models ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier
                            .clickable { showAllLegend = !showAllLegend }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Routing Strategy Bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141829)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C45)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Routing strategy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = when (selectedStrategy) {
                                "Smartest" -> "reliability 35% · speed 10% · intelligence 55%"
                                "Fastest" -> "reliability 20% · speed 70% · intelligence 10%"
                                "Most reliable" -> "reliability 70% · speed 10% · intelligence 20%"
                                "Balanced" -> "reliability 33% · speed 33% · intelligence 34%"
                                else -> "manual routing order"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F1220))
                            .padding(3.dp)
                    ) {
                        listOf("Manual", "Balanced", "Smartest", "Fastest", "Most reliable").forEach { strategy ->
                            val isSelected = selectedStrategy == strategy
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF7C3AED) else Color.Transparent)
                                    .clickable { selectedStrategy = strategy }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strategy,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Toolbar: Context and Capabilities
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Any context", "32K+", "128K+", "1M+").forEach { ctx ->
                    val isSelected = selectedContextFilter == ctx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2E1065) else Color(0xFF1E2235))
                            .clickable { selectedContextFilter = ctx }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = ctx,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFC084FC) else Color(0xFFCBD5E1)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (filterVision) Color(0xFF065F46) else Color(0xFF1E2235))
                        .clickable { filterVision = !filterVision }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Vision",
                        fontSize = 11.sp,
                        fontWeight = if (filterVision) FontWeight.Bold else FontWeight.Medium,
                        color = if (filterVision) Color(0xFFA7F3D0) else Color(0xFFCBD5E1)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (filterTools) Color(0xFF1E3A8A) else Color(0xFF1E2235))
                        .clickable { filterTools = !filterTools }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Tools",
                        fontSize = 11.sp,
                        fontWeight = if (filterTools) FontWeight.Bold else FontWeight.Medium,
                        color = if (filterTools) Color(0xFF93C5FD) else Color(0xFFCBD5E1)
                    )
                }
            }
        }

        // Header showing counts
        item {
            Text(
                text = "${filteredList.size} of ${allQuotaModels.size} models active in failover chain",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }

        // Model Rows List
        items(filteredList) { model ->
            val isEnabled = providerEnabledStates[model.providerType] ?: true
            ModelQuotaItemRow(
                model = model,
                isEnabled = isEnabled,
                onToggle = { onToggleProvider(model.providerType, it) }
            )
        }
    }
}

@Composable
fun ModelQuotaItemRow(
    model: QuotaModelData,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFF141829) else Color(0xFF0E111C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isEnabled) Color(0xFF262C45) else Color(0xFF1A1F30)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "# ${model.rank}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.width(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isEnabled) Color.White else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF232840))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(text = model.platform, fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Monthly Quota Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E3A8A).copy(alpha = 0.4f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(text = model.monthlyQuota, fontSize = 10.sp, color = Color(0xFF93C5FD))
                        }

                        // RPD Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF78350F).copy(alpha = 0.4f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(text = model.rpdQuota, fontSize = 10.sp, color = Color(0xFFFCD34D))
                        }

                        // Context Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF232840))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(text = model.context, fontSize = 10.sp, color = Color(0xFFCBD5E1))
                        }

                        if (model.supportsVision) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF065F46).copy(alpha = 0.4f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(text = "Vision", fontSize = 10.sp, color = Color(0xFFA7F3D0))
                            }
                        }

                        if (model.supportsTools) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF4C1D95).copy(alpha = 0.4f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(text = "Tools", fontSize = 10.sp, color = Color(0xFFD8B4FE))
                            }
                        }
                    }
                }

                // Composite Score & Toggle
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Score ${model.compositeScore}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFA7F3D0)
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF7C3AED),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E2235)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Score Progress Bars (Reliability, Speed, Intelligence)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reliability
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "Rel ${model.reliability}", fontSize = 9.sp, color = Color(0xFF10B981), modifier = Modifier.width(36.dp))
                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF232840))) {
                        Box(modifier = Modifier.fillMaxWidth(model.reliability / 100f).fillMaxHeight().background(Color(0xFF10B981)))
                    }
                }

                // Speed
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "Spd ${model.speed}", fontSize = 9.sp, color = Color(0xFF38BDF8), modifier = Modifier.width(36.dp))
                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF232840))) {
                        Box(modifier = Modifier.fillMaxWidth(model.speed / 100f).fillMaxHeight().background(Color(0xFF38BDF8)))
                    }
                }

                // Intelligence
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "Intel ${model.intelligence}", fontSize = 9.sp, color = Color(0xFFA855F7), modifier = Modifier.width(42.dp))
                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF232840))) {
                        Box(modifier = Modifier.fillMaxWidth(model.intelligence / 100f).fillMaxHeight().background(Color(0xFFA855F7)))
                    }
                }
            }
        }
    }
}

