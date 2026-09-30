package com.orbital.bridge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val coroutineScope = rememberCoroutineScope()

    val connectionState by bridgeClient.connectionState.collectAsState()
    val activeCode by bridgeClient.activeChannelCode.collectAsState()
    val hostName by bridgeClient.connectedHostName.collectAsState()
    val eventLogs by bridgeClient.eventLogs.collectAsState()
    val discoveredLaptops by discoveryService.discoveredLaptops.collectAsState()
    val isScanning by discoveryService.isScanning.collectAsState()

    var selectedTab by remember { mutableStateOf(BridgeTab.QR_SCAN) }
    var manualInput by remember { mutableStateOf("") }
    var pendingQrPayload by remember { mutableStateOf<String?>(null) }
    var showPermissionPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        discoveryService.startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            discoveryService.stopDiscovery()
        }
    }

    // Confirmation dialog before establishing high-security connection
    pendingQrPayload?.let { qrPayload ->
        AlertDialog(
            onDismissRequest = { pendingQrPayload = null },
            containerColor = Color(0xFF1E1B4B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔐 Authorize Laptop AI Bridge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You scanned a secure Laptop AI Bridge pairing code with Bitcoin-grade HMAC-SHA256 signature verification.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("🛡️ Security: 256-bit Cryptographic Vault", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("🚫 Rogue commands from unauthorized MCPs will be blocked.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                    Text(
                        "Do you trust this laptop to inspect screen nodes and dispatch Orbital AI tasks?",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
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
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Authorize & Connect", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingQrPayload = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF0A0C14),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🛰️ Laptop AI Bridge",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = when (connectionState) {
                                    BridgeConnectionState.CONNECTED -> Color(0xFF10B981)
                                    BridgeConnectionState.CONNECTING -> Color(0xFFF59E0B)
                                    BridgeConnectionState.ERROR -> Color(0xFFEF4444)
                                    BridgeConnectionState.DISCONNECTED -> Color(0xFF475569)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = connectionState.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Bitcoin-grade End-to-End Cryptographic Tunnel",
                            fontSize = 11.sp,
                            color = Color(0xFFA78BFA)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "←", fontSize = 24.sp, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111422)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connected Status Card or Security Vault Card
            if (connectionState == BridgeConnectionState.CONNECTED) {
                Surface(
                    color = Color(0xFF13192F),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = hostName ?: "Connected Laptop",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Fingerprint: 0x${bridgeClient.cryptoAuth.getFingerprint()}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            Button(
                                onClick = { bridgeClient.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Disconnect", fontSize = 12.sp, color = Color(0xFFF87171))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🛡️ HMAC-SHA256: Active", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text("⚡ Delegated AI: Ready", fontSize = 11.sp, color = Color(0xFF10B981))
                        }
                    }
                }
            } else {
                // Tab Selection Bar (QR Scan vs Nearby vs Manual)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161B2E))
                        .padding(4.dp)
                ) {
                    listOf(
                        BridgeTab.QR_SCAN to "📷 Scan QR",
                        BridgeTab.NEARBY_DEVICES to "📡 Quick Share",
                        BridgeTab.MANUAL_PIN to "⌨️ PIN / IP"
                    ).forEach { (tab, title) ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF7C3AED) else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (selectedTab) {
                        BridgeTab.QR_SCAN -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
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

            Spacer(modifier = Modifier.height(10.dp))

            // Live Activity Terminal Console
            TerminalConsoleView(
                logs = eventLogs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (connectionState == BridgeConnectionState.CONNECTED) 340.dp else 180.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun NearbyDevicesView(
    discoveredLaptops: List<DiscoveredLaptop>,
    isScanning: Boolean,
    onConnect: (DiscoveredLaptop) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13172B))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Discovered Laptops (mDNS)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (isScanning) {
                Text("Searching...", color = Color(0xFF38BDF8), fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (discoveredLaptops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📡", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No laptops found nearby",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Make sure your laptop is running 'node tools/orbital-mcp/index.js' on the same Wi-Fi.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoveredLaptops) { laptop ->
                    Surface(
                        color = Color(0xFF1C223D),
                        shape = RoundedCornerShape(12.dp),
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
                            Column {
                                Text(laptop.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${laptop.host}:${laptop.port}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                laptop.pin?.let {
                                    Text("PIN: $it", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Button(
                                onClick = { onConnect(laptop) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Connect", fontSize = 12.sp, color = Color.White)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13172B))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter Laptop Pairing Code or IP",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "E.g. 'ORB-4892' or '192.168.1.15'",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = manualInput,
            onValueChange = onInputChange,
            placeholder = { Text("ORB-XXXX or 192.168.x.x", color = Color(0xFF64748B)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF7C3AED),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Connect to Bridge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun TerminalConsoleView(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        color = Color(0xFF090D16),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ACTIVITY TERMINAL CONSOLE", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "Awaiting connection and remote AI actions...",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        val textColor = when {
                            log.contains("🟢") || log.contains("successfully") -> Color(0xFF4ADE80)
                            log.contains("⚡") || log.contains("Executing") -> Color(0xFFFBBF24)
                            log.contains("🔐") || log.contains("Bitcoin") -> Color(0xFF38BDF8)
                            log.contains("🚨") || log.contains("🔴") || log.contains("error") -> Color(0xFFF87171)
                            else -> Color(0xFFCBD5E1)
                        }
                        Text(
                            text = log,
                            color = textColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
