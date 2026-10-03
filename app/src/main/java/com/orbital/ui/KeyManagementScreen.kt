package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var discoveringMap by remember { mutableStateOf(mapOf<ProviderType, Boolean>()) }
    var isCheckingAll by remember { mutableStateOf(false) }

    val configuredCount = providerKeys.count { it.value.isNotBlank() }
    val totalCount = ProviderRegistry.allProviders.size

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
                            text = if (isOnboarding) "Step 2 of 4 · Provider setup is optional" else "$configuredCount of $totalCount providers configured",
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
                    Text(
                        text = "API keys stay encrypted on this device. Add only the providers you use; you can change them later.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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
                            modifier = Modifier.weight(1.3f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Text(
                                text = if (isOnboarding) "Continue" else "Done",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
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
            // Top Tab Navigation Bar
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

            when (currentTab) {
                DashboardTab.PROVIDERS -> {
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

                    // Provider List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProviders) { info ->
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
                    }

                    // Render EditModelsDialog popup when a provider is selected for scoping
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
    onRemoveKey: () -> Unit,
    onTestKey: (String) -> Unit
) {
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var revealKey by remember { mutableStateOf(false) }
    var confirmRemoval by remember { mutableStateOf(false) }
    val configured = apiKey.isNotBlank()
    val (statusLabel, statusColor) = when {
        !isEnabled -> "Disabled" to OrbitalTokens.TextMuted
        !configured -> "Not connected" to OrbitalTokens.TextMuted
        status == ProviderState.IN_COOLDOWN -> "Rate limited" to OrbitalTokens.Warning
        status == ProviderState.UNAVAILABLE -> "Needs attention" to OrbitalTokens.Error
        else -> "Connected" to OrbitalTokens.Success
    }

    Card(
        shape = OrbitalTokens.RadiusMedium,
        colors = CardDefaults.cardColors(containerColor = OrbitalTokens.Surface),
        border = BorderStroke(1.dp, if (isExpanded) OrbitalTokens.Primary.copy(alpha = .7f) else OrbitalTokens.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(OrbitalTokens.RadiusSmall).background(if (configured) OrbitalTokens.SurfaceSelected else OrbitalTokens.SurfaceRaised), contentAlignment = Alignment.Center) {
                    Text(info.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = if (configured) OrbitalTokens.Primary else OrbitalTokens.TextSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.displayName, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = configured,
                    colors = SwitchDefaults.colors(checkedTrackColor = OrbitalTokens.Primary, checkedThumbColor = Color.White)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(info.quotaDescription, style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextSecondary)
            TextButton(onClick = onToggleExpand, contentPadding = PaddingValues(0.dp)) {
                Text(if (isExpanded) "Hide setup" else if (configured) "Manage connection" else "Configure provider")
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = OrbitalTokens.Border)
                    Spacer(Modifier.height(16.dp))
                    Text(if (configured) "Connection" else "1. Add an API key", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("The key stays masked and is only saved after a successful connection test.", style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API key") },
                        placeholder = { Text(info.keyPlaceholder) },
                        visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { revealKey = !revealKey }) { Text(if (revealKey) "Hide" else "Show") }
                        },
                        shape = OrbitalTokens.RadiusSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrbitalTokens.Primary, unfocusedBorderColor = OrbitalTokens.Border,
                            focusedContainerColor = OrbitalTokens.SurfaceRaised, unfocusedContainerColor = OrbitalTokens.SurfaceRaised,
                            focusedTextColor = OrbitalTokens.TextPrimary, unfocusedTextColor = OrbitalTokens.TextPrimary
                        )
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpenPortal) { Text("Get a key") }
                        Button(onClick = { onTestKey(keyInput.trim()) }, enabled = keyInput.isNotBlank(), shape = OrbitalTokens.RadiusSmall) { Text("Test connection") }
                    }

                    if (configured) {
                        Spacer(Modifier.height(8.dp))
                        Text("2. Choose a model", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("$selectedModel selected", style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextSecondary, modifier = Modifier.weight(1f))
                            TextButton(onClick = onEditModels) { Text(if (modelScope == null) "Manage models" else "${modelScope.size} models") }
                            TextButton(onClick = onDiscoverModels, enabled = !isDiscoveringModels) { Text(if (isDiscoveringModels) "Checking…" else "Refresh") }
                        }
                        val models = if (modelScope.isNullOrEmpty()) availableModels else availableModels.filter(modelScope::contains).ifEmpty { availableModels }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            models.forEach { model -> FilterChip(selected = model == selectedModel, onClick = { onSelectModel(model) }, label = { Text(model, maxLines = 1) }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("3. Keep this provider ready", style = MaterialTheme.typography.titleSmall)
                        Text("Orbital will use the selected model when this provider is enabled.", style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextSecondary)
                        TextButton(onClick = { confirmRemoval = true }, contentPadding = PaddingValues(0.dp)) { Text("Remove saved key", color = OrbitalTokens.Error) }
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

@Composable
fun ProviderItemCard(
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
    onKeyChanged: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenPortal: () -> Unit,
    onTestKey: () -> Unit
) {
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131728)),
        border = BorderStroke(1.dp, if (apiKey.isNotBlank() && isEnabled) Color(0xFF2E385C) else Color(0xFF1C2238)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                // Provider Logo / Initial Avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (apiKey.isNotBlank()) listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                else listOf(Color(0xFF222942), Color(0xFF181D2E))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info.displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Provider Info & Badges
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = info.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            maxLines = 1
                        )

                        // Status Badge Pill
                        val (statusColor, statusBg, statusLabel) = when {
                            !isEnabled -> Triple(Color(0xFF94A3B8), Color(0xFF1E243A), "Off")
                            apiKey.isBlank() -> Triple(Color(0xFF94A3B8), Color(0xFF191E33), "No key")
                            status == ProviderState.IN_COOLDOWN -> Triple(Color(0xFFF59E0B), Color(0xFF451A03), "Cooldown")
                            status == ProviderState.UNAVAILABLE -> Triple(Color(0xFFEF4444), Color(0xFF450A0A), "Error")
                            else -> Triple(Color(0xFF34D399), Color(0xFF064E3B).copy(alpha = 0.6f), "Ready")
                        }

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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = info.quotaDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Toggle Switch (on the RIGHT side for standard Android UX)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF7C3AED),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E2338)
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded Key & Model Configuration
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF2E334D), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "API Key / Token",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            onKeyChanged(it.trim())
                        },
                        placeholder = { Text(info.keyPlaceholder, color = Color(0xFF64748B)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF2E334D),
                            focusedContainerColor = Color(0xFF111422),
                            unfocusedContainerColor = Color(0xFF111422),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model Selector Header, Edit Models Dialog Button & Discover Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE MODEL (${availableModels.size} available)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // "Edit models" button trigger matching FreeLLMAPI
                            TextButton(
                                onClick = onEditModels,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                val scopeText = if (modelScope == null) "Edit models" else "Scoped (${modelScope.size})"
                                Text(
                                    text = "⚙ $scopeText",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (apiKey.isNotBlank()) {
                                TextButton(
                                    onClick = onDiscoverModels,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    if (isDiscoveringModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 1.5.dp)
                                    } else {
                                        Text("🔄 Discover Live", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Dynamic Model Selection Chips (filtered by scope if present)
                    val displayList = if (modelScope != null && modelScope.isNotEmpty()) {
                        availableModels.filter { modelScope.contains(it) }.ifEmpty { availableModels }
                    } else {
                        availableModels
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        displayList.forEach { modelName ->
                            val isModelSelected = selectedModel == modelName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isModelSelected) Color(0xFF7C3AED) else Color(0xFF1E2238))
                                    .clickable { onSelectModel(modelName) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = modelName,
                                    fontSize = 11.sp,
                                    color = if (isModelSelected) Color.White else Color(0xFF94A3B8),
                                    fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (apiKey.isNotBlank()) {
                            Button(
                                onClick = onTestKey,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("⚡ Test Key", color = Color(0xFFA7F3D0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        TextButton(onClick = onOpenPortal) {
                            Text("Get API Key ↗", color = Color(0xFF06B6D4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
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
        listOf(
            QuotaModelData(1, "Gemini 3.6 Flash", "google", ProviderType.GEMINI, "3.0M/mo", "RPD 14/20", "1M ctx", true, true, 45, 5, 99, 0.707f, Color(0xFF4285F4)),
            QuotaModelData(2, "Gemini 3.5 Flash", "google", ProviderType.GEMINI, "3.0M/mo", "RPD 13/20", "1M ctx", true, true, 44, 1, 99, 0.696f, Color(0xFF4285F4)),
            QuotaModelData(3, "Nemotron 3 Ultra 550B", "nvidia", ProviderType.NVIDIA_NIM, "40 RPM", "40 RPM", "1M ctx", false, true, 74, 16, 74, 0.683f, Color(0xFF76B900)),
            QuotaModelData(4, "Gemini 3.7 Flash", "google", ProviderType.GEMINI, "3.0M/mo", "RPD 11/20", "1M ctx", true, true, 39, 1, 99, 0.682f, Color(0xFF4285F4)),
            QuotaModelData(5, "Gemini 3 Flash Preview", "google", ProviderType.GEMINI, "3.0M/mo", "RPD 14/20", "1M ctx", true, true, 71, 6, 74, 0.662f, Color(0xFF4285F4)),
            QuotaModelData(6, "Llama 3.3 70B (Versatile)", "groq", ProviderType.GROQ, "14.4K RPD", "30 RPM", "128K ctx", false, true, 98, 95, 92, 0.942f, Color(0xFFF55036)),
            QuotaModelData(7, "Llama 3.1 70B (Ultra-Fast)", "cerebras", ProviderType.CEREBRAS, "14.4K RPD", "30 RPM", "128K ctx", false, true, 99, 99, 88, 0.955f, Color(0xFF10B981)),
            QuotaModelData(8, "Mistral Large 3", "mistral", ProviderType.MISTRAL, "100.0M/mo", "Free Tier", "128K ctx", false, true, 97, 22, 90, 0.837f, Color(0xFFFF7000)),
            QuotaModelData(9, "GLM-4.5 Flash", "zhipu", ProviderType.ZHIPU, "29.9M/30.0M", "20 RPM", "128K ctx", false, true, 90, 70, 85, 0.812f, Color(0xFF3B82F6)),
            QuotaModelData(10, "AlphaOx 1M Standard", "alphaox", ProviderType.ALPHAOX, "Unlimited", "1M ctx", "1M ctx", true, true, 92, 85, 90, 0.885f, Color(0xFF8B5CF6)),
            QuotaModelData(11, "DeepSeek V3.1 (Free)", "openrouter", ProviderType.OPENROUTER, "131K ctx", "Aggregated", "131K ctx", false, true, 85, 60, 94, 0.865f, Color(0xFF6366F1)),
            QuotaModelData(12, "GPT-5 (GitHub Preview)", "github", ProviderType.GITHUB_MODELS, "128K ctx", "15 RPM", "128K ctx", false, true, 95, 75, 98, 0.930f, Color(0xFF22C55E)),
            QuotaModelData(13, "Gemma 4 31B IT", "google", ProviderType.GEMINI, "30.0M/mo", "30.0M/mo", "33K ctx", true, false, 50, 30, 74, 0.642f, Color(0xFF4285F4)),
            QuotaModelData(14, "Gemma 4 26B IT", "google", ProviderType.GEMINI, "30.0M/mo", "30.0M/mo", "33K ctx", true, false, 50, 30, 74, 0.641f, Color(0xFF4285F4)),
            QuotaModelData(15, "Gemini 3.1 Flash-Lite", "google", ProviderType.GEMINI, "3.0M/mo", "RPD 5/20", "1M ctx", true, true, 50, 37, 74, 0.617f, Color(0xFF4285F4)),
            QuotaModelData(16, "Gemini Robotics-ER 2 Preview", "google", ProviderType.GEMINI, "3.0M/mo", "RPD 4/20", "131K ctx", true, false, 56, 1, 74, 0.605f, Color(0xFF4285F4)),
            QuotaModelData(17, "DeepSeek R1 Distill 70B", "groq", ProviderType.GROQ, "14.4K RPD", "30 RPM", "128K ctx", false, true, 96, 92, 96, 0.948f, Color(0xFFF55036)),
            QuotaModelData(18, "Llama-3.3-70B-Instruct (HF)", "huggingface", ProviderType.HUGGINGFACE, "128K ctx", "Meta Router", "128K ctx", false, true, 88, 70, 89, 0.840f, Color(0xFFFFD21E)),
            QuotaModelData(19, "Kilo Auto (Keyless)", "kilo", ProviderType.KILO, "200 req/hr", "Keyless", "128K ctx", false, true, 80, 65, 80, 0.770f, Color(0xFFA855F7)),
            QuotaModelData(20, "GPT-OSS 120B (OVH Keyless)", "ovh", ProviderType.OVH, "131K ctx", "Keyless", "131K ctx", false, true, 82, 60, 84, 0.785f, Color(0xFF0050D7))
        )
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

