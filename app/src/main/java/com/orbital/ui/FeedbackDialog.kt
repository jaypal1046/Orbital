package com.orbital.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialog(
    initialErrorLog: String? = null,
    activeCharacterName: String = "Orbital",
    activeProviderName: String = "Auto-Router",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var issueTitle by remember { mutableStateOf(if (initialErrorLog != null) "Bug: Unexpected error in AI response / action" else "") }
    var issueDescription by remember { mutableStateOf("") }

    val deviceInfo = remember {
        """
        - App Version: Orbital v2.0 (Client-Side Engine)
        - Device: ${Build.MANUFACTURER} ${Build.MODEL}
        - Android OS: API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
        - Active Companion: $activeCharacterName
        - Active AI Provider: $activeProviderName
        """.trimIndent()
    }

    val fullDiagnostics = remember(initialErrorLog, deviceInfo) {
        buildString {
            append("### Environment Info\n")
            append(deviceInfo)
            append("\n\n")
            if (!initialErrorLog.isNullOrBlank()) {
                append("### Error Context / Log\n```\n")
                append(initialErrorLog.take(1500))
                append("\n```\n")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101322),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E1065)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Report Issue / Feedback",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Direct GitHub Issue & Error Submission",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Issue Title Field
            Text(
                text = "Issue Summary",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = issueTitle,
                onValueChange = { issueTitle = it },
                placeholder = { Text("e.g. Action failed to set timer", color = Color(0xFF64748B), fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0xFF2E385B),
                    focusedContainerColor = Color(0xFF161A2C),
                    unfocusedContainerColor = Color(0xFF161A2C),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description / Steps to reproduce
            Text(
                text = "What happened? (Optional details)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = issueDescription,
                onValueChange = { issueDescription = it },
                placeholder = { Text("Describe what went wrong or what you were expecting...", color = Color(0xFF64748B), fontSize = 13.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0xFF2E385B),
                    focusedContainerColor = Color(0xFF161A2C),
                    unfocusedContainerColor = Color(0xFF161A2C),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Error Context & Diagnostics Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F121E),
                border = BorderStroke(1.dp, Color(0xFF232B45)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 Attached Diagnostic Info",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA78BFA)
                        )
                        Text(
                            text = "Copy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(fullDiagnostics))
                                Toast.makeText(context, "Copied diagnostics to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = fullDiagnostics,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp,
                        maxLines = 6
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // GitHub Submit Button
            Button(
                onClick = {
                    val title = if (issueTitle.isNotBlank()) issueTitle else "Bug Report: Orbital Assistant Error"
                    val body = buildString {
                        if (issueDescription.isNotBlank()) {
                            append("### Description\n")
                            append(issueDescription)
                            append("\n\n")
                        }
                        append(fullDiagnostics)
                    }

                    try {
                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        val encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8.toString())
                        val githubUrl = "https://github.com/jaypal1046/Orbital/issues/new?title=$encodedTitle&body=$encodedBody"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        onDismiss()
                    } catch (e: Exception) {
                        clipboardManager.setText(AnnotatedString(body))
                        Toast.makeText(context, "Copied error report to clipboard", Toast.LENGTH_LONG).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Submit on GitHub Issues",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Copy Report Only Button
            Button(
                onClick = {
                    val body = buildString {
                        if (issueTitle.isNotBlank()) append("Title: $issueTitle\n\n")
                        if (issueDescription.isNotBlank()) append("Description: $issueDescription\n\n")
                        append(fullDiagnostics)
                    }
                    clipboardManager.setText(AnnotatedString(body))
                    Toast.makeText(context, "Error report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2338)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy Error Log to Clipboard",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
