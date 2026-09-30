package com.orbital.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.orbital.bridge.BridgeConnectionState
import com.orbital.bridge.OrbitalBridgeClient

@Composable
fun BridgeConnectDialog(
    bridgeClient: OrbitalBridgeClient,
    onDismiss: () -> Unit
) {
    val connectionState by bridgeClient.connectionState.collectAsState()
    val activeChannel by bridgeClient.activeChannelCode.collectAsState()
    val eventLogs by bridgeClient.eventLogs.collectAsState()

    var inputTarget by remember { mutableStateOf(activeChannel ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13182C)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFF333D66))),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💻", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Laptop AI Bridge",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "P2P Remote AI-to-Phone Controller",
                                fontSize = 11.5.sp,
                                color = Color(0xFFA5B4FC)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connection Status Pill
                val (statusColor, statusText) = when (connectionState) {
                    BridgeConnectionState.CONNECTED -> Pair(Color(0xFF10B981), "🟢 Connected to Laptop")
                    BridgeConnectionState.CONNECTING -> Pair(Color(0xFFF59E0B), "🟡 Connecting...")
                    BridgeConnectionState.ERROR -> Pair(Color(0xFFEF4444), "🔴 Connection Error")
                    BridgeConnectionState.DISCONNECTED -> Pair(Color(0xFF94A3B8), "⚪ Disconnected")
                }

                Surface(
                    color = Color(0xFF1E2540),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Field (6-digit PIN or IP)
                if (connectionState != BridgeConnectionState.CONNECTED) {
                    Text(
                        text = "Enter Laptop Pairing PIN or IP:",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = inputTarget,
                        onValueChange = { inputTarget = it },
                        placeholder = { Text("e.g. ORB-8421 or 192.168.1.5", fontSize = 13.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF333D66),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0F121E),
                            unfocusedContainerColor = Color(0xFF0F121E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { bridgeClient.connect(inputTarget) },
                        enabled = inputTarget.isNotBlank() && connectionState != BridgeConnectionState.CONNECTING,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (connectionState == BridgeConnectionState.CONNECTING) "Connecting..." else "Connect to Laptop", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { bridgeClient.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disconnect from Laptop", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Event Activity Log
                Text(
                    text = "Live Activity Log:",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color(0xFF0A0C14), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (eventLogs.isEmpty()) {
                        Text(
                            text = "Waiting for bridge connection...",
                            fontSize = 11.5.sp,
                            color = Color(0xFF475569)
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(eventLogs.reversed()) { logLine ->
                                Text(
                                    text = logLine,
                                    fontSize = 11.sp,
                                    color = if (logLine.contains("🟢")) Color(0xFF34D399) else if (logLine.contains("🔴")) Color(0xFFF87171) else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
