package com.orbital.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.orbital.security.OrbitalTokenCrypto
import com.orbital.security.TokenLockType
import com.orbital.security.TokenPackageHeader
import com.orbital.security.TokenPayload

@Composable
fun ImportTokenDialog(
    packageJson: String,
    onImportSuccess: (TokenPayload) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var header by remember { mutableStateOf<TokenPackageHeader?>(null) }
    var decryptedPayload by remember { mutableStateOf<TokenPayload?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(packageJson) {
        val inspection = OrbitalTokenCrypto.inspectTokenPackage(packageJson)
        inspection.onSuccess { hdr ->
            header = hdr
            if (hdr.lockType == TokenLockType.DEVICE_LOCKED) {
                // Try immediate auto-decrypt for device-locked
                val importResult = OrbitalTokenCrypto.importTokenPackage(context, packageJson, null)
                importResult.onSuccess { payload ->
                    decryptedPayload = payload
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Failed to unlock device-locked token"
                }
            }
        }.onFailure { err ->
            errorMessage = err.localizedMessage ?: "Invalid .orbtoken file format"
        }
    }

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Import",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import .orbtoken File",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = header?.provider ?: "Detected Token Package",
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

                // Case 1: Successfully decrypted (Device-Locked or PIN unlocked)
                if (decryptedPayload != null) {
                    val payload = decryptedPayload!!
                    Surface(
                        color = Color(0xFF13182C),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ready to Import",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Provider: ${payload.provider}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Token: ${payload.apiKey.take(6)}••••••••${payload.apiKey.takeLast(4)}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                                onImportSuccess(payload)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Apply Token", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Case 2: PIN Protected - needs PIN entry
                else if (header?.lockType == TokenLockType.PIN_PROTECTED) {
                    Text(
                        text = "This token package is protected with a PIN. Enter the 4-digit PIN to unlock it:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 6) pinInput = it
                            errorMessage = null
                        },
                        label = { Text("4-digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF2E3856),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFF87171),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                                if (pinInput.isBlank()) {
                                    errorMessage = "Please enter the PIN"
                                    return@Button
                                }
                                isProcessing = true
                                val res = OrbitalTokenCrypto.importTokenPackage(context, packageJson, pinInput)
                                res.onSuccess { payload ->
                                    decryptedPayload = payload
                                    errorMessage = null
                                }.onFailure { err ->
                                    errorMessage = err.localizedMessage ?: "Incorrect PIN"
                                }
                                isProcessing = false
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Case 3: Error occurred (e.g. Device mismatch or corrupt)
                else {
                    Surface(
                        color = Color(0xFF2D1515),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cannot Import",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage ?: "Unknown error while reading token package.",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3856))
                    ) {
                        Text("Close", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
