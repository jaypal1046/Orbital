package com.orbital.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.orbital.data.ProviderInfo
import com.orbital.security.OrbitalTokenCrypto
import com.orbital.security.TokenLockType
import com.orbital.security.TokenPayload
import java.io.File

@Composable
fun ExportTokenDialog(
    providerInfo: ProviderInfo,
    apiKey: String,
    selectedModel: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedLockType by remember { mutableStateOf(TokenLockType.DEVICE_LOCKED) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF0F1322),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF1E243D)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export .orbtoken",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = providerInfo.displayName,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose security protection for your token file:",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: Device-Locked (Default)
                Surface(
                    color = if (selectedLockType == TokenLockType.DEVICE_LOCKED) Color(0xFF192038) else Color(0xFF13182C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedLockType == TokenLockType.DEVICE_LOCKED) Color(0xFF7C3AED) else Color(0xFF1E243D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedLockType = TokenLockType.DEVICE_LOCKED
                            errorMessage = null
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedLockType == TokenLockType.DEVICE_LOCKED,
                            onClick = {
                                selectedLockType = TokenLockType.DEVICE_LOCKED
                                errorMessage = null
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7C3AED))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "📱 Lock to this phone only",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "No PIN needed. Cannot be opened on any other phone even if leaked.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: PIN-Protected
                Surface(
                    color = if (selectedLockType == TokenLockType.PIN_PROTECTED) Color(0xFF192038) else Color(0xFF13182C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedLockType == TokenLockType.PIN_PROTECTED) Color(0xFF7C3AED) else Color(0xFF1E243D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedLockType = TokenLockType.PIN_PROTECTED
                            errorMessage = null
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedLockType == TokenLockType.PIN_PROTECTED,
                                onClick = {
                                    selectedLockType = TokenLockType.PIN_PROTECTED
                                    errorMessage = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7C3AED))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "🔑 Protect with PIN",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Set a 4-digit PIN to share and open on other phones or tablets.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        if (selectedLockType == TokenLockType.PIN_PROTECTED) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = pin,
                                    onValueChange = { if (it.length <= 6) pin = it },
                                    label = { Text("4-digit PIN", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF7C3AED),
                                        unfocusedBorderColor = Color(0xFF2E3856),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = confirmPin,
                                    onValueChange = { if (it.length <= 6) confirmPin = it },
                                    label = { Text("Confirm PIN", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF7C3AED),
                                        unfocusedBorderColor = Color(0xFF2E3856),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E3856))
                    ) {
                        Text("Cancel", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (selectedLockType == TokenLockType.PIN_PROTECTED) {
                                if (pin.length < 4) {
                                    errorMessage = "PIN must be at least 4 digits"
                                    return@Button
                                }
                                if (pin != confirmPin) {
                                    errorMessage = "PINs do not match"
                                    return@Button
                                }
                            }

                            isExporting = true
                            try {
                                val payload = TokenPayload(
                                    provider = providerInfo.type.name,
                                    apiKey = apiKey,
                                    selectedModel = selectedModel
                                )

                                val exportedJson = if (selectedLockType == TokenLockType.DEVICE_LOCKED) {
                                    OrbitalTokenCrypto.exportDeviceLockedToken(context, payload)
                                } else {
                                    OrbitalTokenCrypto.exportPinProtectedToken(pin, payload)
                                }

                                shareTokenFile(context, providerInfo.type.name.lowercase(), exportedJson)
                                onDismiss()
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Failed to export token"
                            } finally {
                                isExporting = false
                            }
                        },
                        modifier = Modifier.weight(1.3f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Text("Export / Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun shareTokenFile(context: Context, providerName: String, fileContent: String) {
    try {
        val cacheDir = File(context.cacheDir, "tokens").apply { mkdirs() }
        val fileName = "${providerName}_token.orbtoken"
        val file = File(cacheDir, fileName).apply {
            writeText(fileContent)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Orbital Token Package - $fileName")
            putExtra(Intent.EXTRA_TEXT, "Here is your encrypted Orbital token file ($fileName). Tap it to open with Orbital.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share .orbtoken file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
