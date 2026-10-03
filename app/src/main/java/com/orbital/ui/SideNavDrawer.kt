package com.orbital.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbital.data.db.ChatSessionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideNavDrawer(
    currentCharacterId: String,
    sessions: List<ChatSessionSummary> = emptyList(),
    onSelectCharacter: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onShareChat: (Boolean) -> Unit = {},
    onOpenKeys: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenRoutingMode: () -> Unit,
    onOpenLegal: (LegalTab) -> Unit,
    onOpenAbout: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onOpenSkills: () -> Unit = {},
    onOpenBridge: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onOpenAccessibilityDisclosure: () -> Unit = {},
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedDestination by remember { mutableStateOf<String?>(null) }
    var sessionToRename by remember { mutableStateOf<ChatSessionSummary?>(null) }
    val matchingSessions = remember(sessions, query) {
        sessions.filter { query.isBlank() || it.title.contains(query, true) || it.preview.contains(query, true) }
    }

    ModalDrawerSheet(
        modifier = modifier.widthIn(max = 336.dp),
        drawerContainerColor = OrbitalTokens.Surface,
        drawerContentColor = OrbitalTokens.TextPrimary
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(OrbitalTokens.RadiusSmall).background(OrbitalTokens.Primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Orbital", style = MaterialTheme.typography.titleMedium)
                    Text("AI workspace", style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextSecondary)
                }
                IconButton(onClick = onCloseDrawer) { Icon(Icons.Default.Close, "Close navigation", tint = OrbitalTokens.TextSecondary) }
            }

            Button(
                onClick = { onNewChat(); onCloseDrawer() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = OrbitalTokens.RadiusSmall,
                colors = ButtonDefaults.buttonColors(containerColor = OrbitalTokens.Primary)
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New chat")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = OrbitalTokens.RadiusSmall,
                placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isBlank()) null else {{ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }},
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OrbitalTokens.SurfaceRaised, unfocusedContainerColor = OrbitalTokens.SurfaceRaised,
                    focusedBorderColor = OrbitalTokens.Primary, unfocusedBorderColor = OrbitalTokens.Border,
                    focusedTextColor = OrbitalTokens.TextPrimary, unfocusedTextColor = OrbitalTokens.TextPrimary,
                    focusedPlaceholderColor = OrbitalTokens.TextMuted, unfocusedPlaceholderColor = OrbitalTokens.TextMuted
                )
            )

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp)) {
                DrawerSection("Chats")
                if (matchingSessions.isEmpty()) {
                    Text(if (query.isBlank()) "Your recent chats will appear here." else "No chats match “$query”.", style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                } else matchingSessions.take(12).forEach { session ->
                    DrawerChatItem(session, onOpen = { onSelectSession(session.id); onCloseDrawer() }, onRename = { sessionToRename = session })
                }
                if (sessions.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
                    TextButton(onClick = { onShareChat(false) }) { Text("Share") }
                    TextButton(onClick = { onShareChat(true) }) { Text("Export") }
                }

                Spacer(Modifier.height(20.dp))
                DrawerSection("Workspace")
                DrawerNavItem(Icons.Default.Settings, "Providers", "Keys and model access", selectedDestination == "providers") { selectedDestination = "providers"; onOpenKeys(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Share, "Routing", "Choose how models are selected", selectedDestination == "routing") { selectedDestination = "routing"; onOpenRoutingMode(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Build, "Automation", "Tasks and power settings", selectedDestination == "automation") { selectedDestination = "automation"; onOpenAutomations(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Star, "Skills", "Capabilities and instructions", selectedDestination == "skills") { selectedDestination = "skills"; onOpenSkills(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Share, "Laptop bridge", "Connect a development machine", selectedDestination == "bridge") { selectedDestination = "bridge"; onOpenBridge(); onCloseDrawer() }

                Spacer(Modifier.height(20.dp))
                DrawerSection("Support")
                DrawerNavItem(Icons.Default.Warning, "Send feedback", null, false) { onOpenFeedback(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Settings, "Screen access", null, false) { onOpenAccessibilityDisclosure(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Refresh, "Check for updates", null, false) { onCheckForUpdates(); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Lock, "Privacy and terms", null, false) { onOpenLegal(LegalTab.PRIVACY); onCloseDrawer() }
                DrawerNavItem(Icons.Default.Info, "About Orbital", null, false) { onOpenAbout(); onCloseDrawer() }
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider(color = OrbitalTokens.Border)
            Row(Modifier.fillMaxWidth().clickable { onOpenCharacters(); onCloseDrawer() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(OrbitalTokens.SurfaceSelected), contentAlignment = Alignment.Center) { Icon(Icons.Default.Face, null, tint = OrbitalTokens.Primary, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Companion", style = MaterialTheme.typography.titleSmall)
                    Text("Choose character", style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextSecondary)
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(OrbitalTokens.Success))
            }
        }
    }

    sessionToRename?.let { session ->
        var title by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null }, title = { Text("Rename chat") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRenameSession(session.id, title.trim()); sessionToRename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { sessionToRename = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DrawerSection(label: String) = Text(label, style = MaterialTheme.typography.labelMedium, color = OrbitalTokens.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

@Composable
private fun DrawerNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(OrbitalTokens.RadiusSmall).background(if (selected) OrbitalTokens.SurfaceSelected else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = if (subtitle == null) 10.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) OrbitalTokens.Primary else OrbitalTokens.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) OrbitalTokens.TextPrimary else OrbitalTokens.TextSecondary)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun DrawerChatItem(session: ChatSessionSummary, onOpen: () -> Unit, onRename: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(OrbitalTokens.RadiusSmall).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Face, null, tint = OrbitalTokens.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.preview, style = MaterialTheme.typography.bodySmall, color = OrbitalTokens.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "Chat options", tint = OrbitalTokens.TextMuted) }
    }
}
