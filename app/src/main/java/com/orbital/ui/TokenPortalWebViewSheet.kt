package com.orbital.ui

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.orbital.data.ProviderInfo

class OrbitalTokenBridge(private val onTokenReceived: (String) -> Unit) {
    @JavascriptInterface
    fun onTokenCaptured(token: String) {
        if (token.isNotBlank()) {
            onTokenReceived(token.trim())
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenPortalWebViewSheet(
    providerInfo: ProviderInfo,
    onDismiss: () -> Unit,
    onTokenCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var capturedToken by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf(providerInfo.displayName) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Clipboard listener to capture user manual copy actions
    DisposableEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()?.trim()
                    if (isValidTokenCandidate(text)) {
                        capturedToken = text
                    }
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(clipListener)
        onDispose {
            clipboard.removePrimaryClipChangedListener(clipListener)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F1322),
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C14))
        ) {
            // Header Bar
            Surface(
                color = Color(0xFF141829),
                border = BorderStroke(1.dp, Color(0xFF1E243D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = providerInfo.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = providerInfo.portalUrl,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { webView?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                }
            }

            // Loading progress bar
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF8B5CF6),
                    trackColor = Color(0xFF1E243D)
                )
            }

            // WebView Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                userAgentString = settings.userAgentString + " OrbitalApp/1.0"
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            // Javascript Interface
                            addJavascriptInterface(
                                OrbitalTokenBridge { token ->
                                    if (isValidTokenCandidate(token)) {
                                        post {
                                            capturedToken = token
                                        }
                                    }
                                },
                                "OrbitalTokenBridge"
                            )

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    isLoading = newProgress < 100
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    // Inject script to listen to clipboard write and button copies
                                    val hookScript = """
                                        (function() {
                                            if (window._orbitalHooked) return;
                                            window._orbitalHooked = true;
                                            
                                            // Intercept clipboard.writeText
                                            if (navigator.clipboard && navigator.clipboard.writeText) {
                                                const originalWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);
                                                navigator.clipboard.writeText = function(text) {
                                                    try {
                                                        if (window.OrbitalTokenBridge) {
                                                            window.OrbitalTokenBridge.onTokenCaptured(text);
                                                        }
                                                    } catch (e) {}
                                                    return originalWriteText(text);
                                                };
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(hookScript, null)
                                }
                            }

                            loadUrl(providerInfo.portalUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Captured Token Notification Banner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = capturedToken != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                    capturedToken?.let { token ->
                        Surface(
                            color = Color(0xFF141829),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF7C3AED)),
                            shadowElevation = 12.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Token Detected",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "API Key Detected!",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = token.take(6) + "••••••••" + token.takeLast(4),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        onTokenCaptured(token)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Use Key", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}


private fun isValidTokenCandidate(text: String?): Boolean {
    if (text == null) return false
    val trimmed = text.trim()
    // A sensible token length filter (at least 15 chars, no newlines/tabs, reasonable length)
    return trimmed.length in 15..500 && !trimmed.contains("\n") && !trimmed.contains(" ")
}
