package com.orbital.updater.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.orbital.updater.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    updateResult: UpdateCheckResult,
    downloader: ApkDownloader,
    installer: ApkInstaller,
    onDismiss: () -> Unit,
    onOtaPatchApplied: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadProgress by downloader.downloadProgress.collectAsState()
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var isPatching by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        if (!updateResult.isForceUpdate) onDismiss()
    }) {
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon & Title
                val icon = when (updateResult.updateType) {
                    UpdateType.OTA_HOT_PATCH -> "⚡"
                    UpdateType.PLAY_STORE_REDIRECT -> "🛒"
                    else -> "🚀"
                }
                Text(text = icon, fontSize = 42.sp)
                Spacer(modifier = Modifier.height(12.dp))

                val titleText = when (updateResult.updateType) {
                    UpdateType.OTA_HOT_PATCH -> "Instant Hot-Patch Available"
                    else -> "New Version Available"
                }
                Text(
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                val versionSub = when (updateResult.updateType) {
                    UpdateType.OTA_HOT_PATCH -> "Patch #${updateResult.latestPatchVersion} (Current: #${updateResult.currentPatchVersion})"
                    else -> "v${updateResult.latestVersionName} (Build ${updateResult.latestVersionCode})"
                }
                Text(
                    text = versionSub,
                    fontSize = 13.sp,
                    color = Color(0xFFA5B4FC)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog Box
                if (updateResult.changelog.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2540), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "What's New:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            updateResult.changelog.forEach { note ->
                                Text(
                                    text = "• $note",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Download Progress (for Standalone APK track)
                when (val prog = downloadProgress) {
                    is DownloadProgress.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { prog.percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = Color(0xFF7C3AED),
                                trackColor = Color(0xFF1E2540)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Downloading: ${prog.percent}%",
                                fontSize = 12.sp,
                                color = Color(0xFFA5B4FC),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is DownloadProgress.Failed -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "❌ ${prog.errorMessage}",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val fallbackUrl = updateResult.standaloneApkUrl?.substringBeforeLast("/download/")
                                        ?: "https://github.com/jaypal1046/Orbital/releases"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🌐 Open Releases in Browser", fontSize = 12.sp, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    else -> {}
                }

                // Action Buttons
                when (updateResult.updateType) {
                    UpdateType.PLAY_STORE_REDIRECT -> {
                        Button(
                            onClick = {
                                val url = updateResult.playStoreUrl ?: "market://details?id=${context.packageName}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Update on Google Play", fontWeight = FontWeight.Bold)
                        }
                    }

                    UpdateType.GITHUB_APK_DOWNLOAD -> {
                        if (downloadedFile != null) {
                            Button(
                                onClick = {
                                    if (!installer.canInstallPackages()) {
                                        installer.openInstallPermissionSettings()
                                    } else {
                                        installer.installApk(downloadedFile!!)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Update Now", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val isDownloading = downloadProgress is DownloadProgress.Downloading
                            Button(
                                onClick = {
                                    val apkUrl = updateResult.standaloneApkUrl
                                    if (!apkUrl.isNullOrBlank()) {
                                        scope.launch {
                                            val file = downloader.downloadApk(apkUrl)
                                            downloadedFile = file
                                            if (file != null) {
                                                if (!installer.canInstallPackages()) {
                                                    installer.openInstallPermissionSettings()
                                                } else {
                                                    installer.installApk(file)
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isDownloading) "Downloading..." else "Download & Install", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    UpdateType.OTA_HOT_PATCH -> {
                        Button(
                            onClick = {
                                val manifest = updateResult.otaPatchManifest
                                if (manifest != null) {
                                    isPatching = true
                                    scope.launch {
                                        val applied = DynamicOtaConfigStore(context).applyOtaPatch(manifest)
                                        isPatching = false
                                        if (applied) {
                                            onOtaPatchApplied()
                                            onDismiss()
                                        }
                                    }
                                }
                            },
                            enabled = !isPatching,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPatching) "Applying Patch..." else "Apply Hot-Patch Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    UpdateType.UP_TO_DATE -> {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }
                }

                if (!updateResult.isForceUpdate && updateResult.updateType != UpdateType.UP_TO_DATE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Later", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
