package com.orbital.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.automation.OrbitalAutomationEngine
import com.orbital.automation.ShizukuAutomationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAutomationDialog(
    onDismiss: () -> Unit,
    onEnableClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1322),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7C3AED), Color(0xFF38BDF8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🛡️", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Device Automation & Shell Engine",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "100% Banking & UPI Safe (Zero Accessibility)",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Orbital automates your device via rootless ADB scripts (Shizuku) and standard Android Intents. Zero Accessibility Services are registered, ensuring Google Pay, PhonePe, and banking apps are never blocked.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Feature List
                Surface(
                    color = Color(0xFF161B2E),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF242C48)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutomationFeatureItem(
                            icon = "✅",
                            title = "Zero Accessibility Service",
                            desc = "No accessibility service is registered in Android, preventing any UPI security alerts."
                        )
                        AutomationFeatureItem(
                            icon = "⚡",
                            title = "Native Script & ADB Execution",
                            desc = "Executes taps, swipes, and text entry via system shell with high precision."
                        )
                        AutomationFeatureItem(
                            icon = "🔒",
                            title = "Rootless & Wireless",
                            desc = "Works via standard Android 11+ Wireless Debugging without root or cable connection."
                        )
                    }
                }

                // Privacy Box
                Surface(
                    color = Color(0xFF0C1F17),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("🛡️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Screen data and privacy",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Orbital only reads on-screen text when actively executing a task. You can stop or revoke permissions at any time.",
                                color = Color(0xFFA7F3D0),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ShizukuAutomationManager.isRunning() && !ShizukuAutomationManager.hasPermission()) {
                        ShizukuAutomationManager.requestPermission()
                    } else {
                        OrbitalAutomationEngine.openSettings(context)
                    }
                    onEnableClicked()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (ShizukuAutomationManager.isRunning()) "Authorize ADB / Shizuku" else "Open Shizuku App",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Continue with Intents Only",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
    )
}

@Composable
private fun AutomationFeatureItem(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

// Backwards compatibility alias
@Composable
fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onEnableClicked: () -> Unit = {}
) {
    DeviceAutomationDialog(onDismiss = onDismiss, onEnableClicked = onEnableClicked)
}
