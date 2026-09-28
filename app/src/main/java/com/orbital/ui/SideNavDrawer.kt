package com.orbital.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.orbital.overlay.OverlayService
import com.orbital.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideNavDrawer(
    currentCharacterId: String,
    recentMessages: List<UiMessage> = emptyList(),
    onSelectCharacter: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectRecentChat: (UiMessage) -> Unit = {},
    onOpenKeys: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenRoutingMode: () -> Unit,
    onOpenLegal: (LegalTab) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFeedback: () -> Unit = {},
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        modifier = modifier.width(310.dp),
        drawerContainerColor = Color(0xFF0F121E),
        drawerContentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Search Bar (Gemini style)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search for chats",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7C3AED),
                    unfocusedBorderColor = Color(0xFF232840),
                    focusedContainerColor = Color(0xFF161A2C),
                    unfocusedContainerColor = Color(0xFF161A2C),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // New Chat Action Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E1B4B),
                border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNewChat()
                        onCloseDrawer()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New chat",
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "New chat",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E1065))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA78BFA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF20263E), thickness = 1.dp)

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                // SECTION: Settings & Tools
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "SETTINGS & TOOLS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA78BFA),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                DrawerMenuItem(
                    icon = Icons.Default.Settings,
                    iconTint = Color(0xFF38BDF8),
                    title = "API Keys & Providers",
                    subtitle = "Manage Gemini, Groq, OpenAI & 24+ keys",
                    onClick = {
                        onOpenKeys()
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Share,
                    iconTint = Color(0xFFA855F7),
                    title = "AI Model Routing",
                    subtitle = "Smart Auto-Router & Speed tiers",
                    onClick = {
                        onOpenRoutingMode()
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Build,
                    iconTint = Color(0xFF10B981),
                    title = "Automation & Power",
                    subtitle = "Device tasks, summary scheduling",
                    onClick = {
                        onOpenAutomations()
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.PlayArrow,
                    iconTint = Color(0xFFF59E0B),
                    title = "Floating Mascot Overlay",
                    subtitle = "Launch companion on screen",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_START
                                putExtra("character_id", currentCharacterId)
                            }
                            context.startService(intent)
                            Toast.makeText(context, "Floating Companion Overlay Active!", Toast.LENGTH_SHORT).show()
                        }
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Warning,
                    iconTint = Color(0xFFF43F5E),
                    title = "Feedback & Bug Report",
                    subtitle = "Submit issue or log directly to GitHub",
                    onClick = {
                        onOpenFeedback()
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Lock,
                    iconTint = Color(0xFF38BDF8),
                    title = stringResource(R.string.menu_privacy_policy),
                    subtitle = "How we protect your data",
                    onClick = {
                        onOpenLegal(LegalTab.PRIVACY)
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFFA855F7),
                    title = stringResource(R.string.menu_terms_of_service),
                    subtitle = "Terms of use",
                    onClick = {
                        onOpenLegal(LegalTab.TERMS)
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF10B981),
                    title = stringResource(R.string.menu_about),
                    subtitle = "Version, license, open source",
                    onClick = {
                        onOpenAbout()
                        onCloseDrawer()
                    }
                )

                // SECTION: Recent Topics / History
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT CHATS (30 DAYS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA),
                        letterSpacing = 1.sp
                    )
                    val totalUserChats = recentMessages.filter { it.role == "user" }.size
                    if (totalUserChats > 0) {
                        Text(
                            text = "$totalUserChats saved",
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val userChats = recentMessages
                    .filter { it.role == "user" }
                    .filter {
                        if (searchQuery.isBlank()) true
                        else it.content.contains(searchQuery, ignoreCase = true)
                    }
                    .reversed()

                if (userChats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching chats found" else "No recent chats yet",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    userChats.take(20).forEach { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectRecentChat(msg)
                                    onCloseDrawer()
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = msg.content,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (msg.actionLabel != null) {
                                    Text(
                                        text = msg.actionLabel,
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF34D399),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Drawer Footer
            HorizontalDivider(color = Color(0xFF20263E), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Orbital Companion",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "v2.0 • Client-Side Engine",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "● Online",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF34D399)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF151829),
        border = BorderStroke(1.dp, Color(0xFF222842)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Preview
@Composable
fun PreviewSideNavDrawer() {
    SideNavDrawer(
        currentCharacterId = "lumy",
        onSelectCharacter = {},
        onNewChat = {},
        onOpenKeys = {},
        onOpenCharacters = {},
        onOpenAutomations = {},
        onOpenRoutingMode = {},
        onOpenLegal = {},
        onOpenAbout = {},
        onCloseDrawer = {}
    )
}
