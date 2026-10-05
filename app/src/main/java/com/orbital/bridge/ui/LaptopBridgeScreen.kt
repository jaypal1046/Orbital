package com.orbital.bridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.bridge.BridgeConnectionState
import com.orbital.bridge.DiscoveredLaptop
import com.orbital.bridge.OrbitalBridgeClient
import com.orbital.bridge.OrbitalDiscoveryService
import kotlinx.coroutines.launch

enum class BridgeTab {
    QR_SCAN,
    NEARBY_DEVICES,
    MANUAL_PIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopBridgeScreen(
    bridgeClient: OrbitalBridgeClient,
    discoveryService: OrbitalDiscoveryService,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by bridgeClient.connectionState.collectAsState()
    val hostName by bridgeClient.connectedHostName.collectAsState()
    val eventLogs by bridgeClient.eventLogs.collectAsState()
    val discoveredLaptops by discoveryService.discoveredLaptops.collectAsState()
    val isScanning by discoveryService.isScanning.collectAsState()

    var selectedTab by remember { mutableStateOf(BridgeTab.QR_SCAN) }
    var manualInput by remember { mutableStateOf("") }
    var pendingQrPayload by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        discoveryService.startDiscovery()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == BridgeTab.NEARBY_DEVICES) {
            discoveryService.startDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            discoveryService.stopDiscovery()
        }
    }

    // High-Security Pairing Authorization Modal
    pendingQrPayload?.let { qrPayload ->
        AlertDialog(
            onDismissRequest = { pendingQrPayload = null },
            containerColor = Color(0xFF11162B),
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔐", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Authorize AI Bridge",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "You scanned a secure Laptop AI Bridge pairing token with 256-bit Bitcoin-grade cryptographic HMAC signature verification.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Surface(
                        color = Color(0xFF090D1A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "256-bit ECDSA & HMAC Active",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "All remote automation commands will be verified against this crypto key. Fake/unauthorized commands are dropped immediately.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Text(
                        "Authorize this laptop to inspect screen nodes and execute AI agent tasks?",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = bridgeClient.connectFromQr(qrPayload)
                        pendingQrPayload = null
                        if (success) {
                            Toast.makeText(context, "Pairing securely...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to parse QR payload", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Authorize & Connect", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingQrPayload = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF070913),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🛰️ Laptop AI Bridge",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val (badgeBg, badgeText) = when (connectionState) {
                                BridgeConnectionState.CONNECTED -> Color(0xFF059669) to "CONNECTED"
                                BridgeConnectionState.CONNECTING -> Color(0xFFD97706) to "PAIRING..."
                                BridgeConnectionState.ERROR -> Color(0xFFDC2626) to "ERROR"
                                BridgeConnectionState.DISCONNECTED -> Color(0xFF334155) to "STANDBY"
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "256-bit Cryptographic AI Delegation Tunnel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF38BDF8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161B2E))
                    ) {
                        Text(text = "←", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1322)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Connected Hero Status Card or Multi-tab Pairing Control
            if (connectionState == BridgeConnectionState.CONNECTED) {
                Surface(
                    color = Color(0xFF10152B),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF38BDF8)))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulseAlpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = pulseAlpha))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = hostName ?: "Connected Laptop",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Secure Tunnel Active",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            Button(
                                onClick = { bridgeClient.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF0A0F1D),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🛡️", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("HMAC Signed", fontSize = 11.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Medium)
                                }
                            }

                            Surface(
                                color = Color(0xFF0A0F1D),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delegated AI", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab Segmented Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF131729))
                        .padding(4.dp)
                ) {
                    listOf(
                        BridgeTab.QR_SCAN to "📷 Scan QR",
                        BridgeTab.NEARBY_DEVICES to "📡 Quick Share",
                        BridgeTab.MANUAL_PIN to "⌨️ PIN / Host"
                    ).forEach { (tab, title) ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)))
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .clickable { selectedTab = tab }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Tab Content Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (selectedTab) {
                        BridgeTab.QR_SCAN -> {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF0C101E),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                            ) {
                                QrCodeScannerView(
                                    onQrScanned = { barcode ->
                                        pendingQrPayload = barcode
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        BridgeTab.NEARBY_DEVICES -> {
                            NearbyDevicesView(
                                discoveredLaptops = discoveredLaptops,
                                isScanning = isScanning,
                                onRefresh = {
                                    discoveryService.stopDiscovery()
                                    discoveryService.startDiscovery()
                                },
                                onConnect = { laptop ->
                                    bridgeClient.connect(laptop)
                                }
                            )
                        }

                        BridgeTab.MANUAL_PIN -> {
                            ManualPinView(
                                manualInput = manualInput,
                                onInputChange = { manualInput = it },
                                onConnect = {
                                    bridgeClient.connect(manualInput)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Activity Terminal Console
            TerminalConsoleView(
                logs = eventLogs,
                onClearLogs = { bridgeClient.clearLogs() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (connectionState == BridgeConnectionState.CONNECTED) 360.dp else if (selectedTab == BridgeTab.MANUAL_PIN) 120.dp else 170.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun NearbyDevicesView(
    discoveredLaptops: List<DiscoveredLaptop>,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onConnect: (DiscoveredLaptop) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_ring")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101426))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Nearby Laptops",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isScanning) {
                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fast Subnet Probe", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A223B))
            ) {
                Text("🔄", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (discoveredLaptops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .scale(if (isScanning) radarScale else 1f)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFF7C3AED).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📡", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Searching for orbital MCP hosts...",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Make sure 'node tools/orbital-mcp/index.js' is running on your laptop on the same Wi-Fi.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoveredLaptops) { laptop ->
                    Surface(
                        color = Color(0xFF171D36),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2B355A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnect(laptop) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💻", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        laptop.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${laptop.host}:${laptop.port}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                laptop.pin?.let {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "PIN: $it",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Button(
                                onClick = { onConnect(laptop) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("⚡ Connect", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualPinView(
    manualInput: String,
    onInputChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101426))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF7C3AED).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("⌨️", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter Laptop PIN or Host IP",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Enter pairing PIN (e.g. 'ORB-1045') or host IP:port",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = manualInput,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text = "e.g. 192.168.1.5:8765 or ORB-1045",
                    color = Color(0xFF64748B),
                    fontSize = 13.5.sp
                )
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start
            ),
            singleLine = true,
            leadingIcon = {
                Text("🔑", fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
            },
            trailingIcon = {
                TextButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onInputChange(clip.trim())
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("📋 Paste", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF8B5CF6),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF090D18),
                unfocusedContainerColor = Color(0xFF090D18),
                cursorColor = Color(0xFF38BDF8)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onConnect,
            enabled = manualInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C3AED),
                disabledContainerColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text("⚡ Connect to Bridge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun TerminalConsoleView(
    logs: List<String>,
    onClearLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        color = Color(0xFF070912),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF1B2238)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "LIVE ACTIVITY CONSOLE",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📋 Copy",
                        fontSize = 10.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val text = logs.joinToString("\n")
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Orbital Logs", text)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🗑️ Clear",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClearLogs() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Awaiting connection & remote agent actions...",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        val textColor = when {
                            log.contains("🟢") || log.contains("successfully") || log.contains("Verified") -> Color(0xFF4ADE80)
                            log.contains("⚡") || log.contains("Executing") || log.contains("INCOMING") -> Color(0xFFFBBF24)
                            log.contains("🔐") || log.contains("Bitcoin") || log.contains("crypto") || log.contains("AUTH") -> Color(0xFF38BDF8)
                            log.contains("🚨") || log.contains("🔴") || log.contains("error") || log.contains("REJECTED") -> Color(0xFFF87171)
                            else -> Color(0xFFCBD5E1)
                        }
                        Text(
                            text = log,
                            color = textColor,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
