package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import com.orbital.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    initialTab: LegalTab = LegalTab.PRIVACY
) {
    var currentTab by remember { mutableStateOf(initialTab) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_privacy_policy), color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
            )
        },
        containerColor = Color(0xFF0A0C14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Simple tab row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LegalTab.values().forEach { tab ->
                    Text(
                        text = tab.label,
                        color = if (currentTab == tab) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (currentTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable { currentTab = tab }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E2030),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141724))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    when (currentTab) {
                        LegalTab.PRIVACY -> LegalHtmlView(resourceId = R.raw.privacy_policy)
                        LegalTab.TERMS -> LegalHtmlView(resourceId = R.raw.terms_of_service)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit, onOpenGithub: () -> Unit) {
    val context = LocalContext.current
    val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_about_title), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.dialog_about_message, versionName as Any),
                    color = Color(0xFFE2E8F0),
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenGithub,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.link_github), color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Open", tint = Color(0xFF8B5CF6))
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.link_license),
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

enum class LegalTab(val label: String) {
    PRIVACY("Privacy"),
    TERMS("Terms")
}

@Composable
fun LegalHtmlView(resourceId: Int) {
    val context = LocalContext.current

    AndroidView(
        factory = {
            TextView(it).apply {
                setMovementMethod(LinkMovementMethod.getInstance())
                setTextColor(0xFFE2E8F0.toInt())
                setTextSize(14f)
                setLineSpacing(4f, 1.2f)
            }
        },
        update = { textView ->
            val html = context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            } else {
                Html.fromHtml(html)
            }
            textView.text = spanned
        },
        modifier = Modifier.fillMaxSize()
    )
}