package com.orbital.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatBubbleItem(
    message: UiMessage,
    characterName: String,
    characterId: String = "lumy",
    isStreaming: Boolean = false,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRetry: () -> Unit = {},
    onReportError: (String) -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
    onAddStepToInput: (String) -> Unit = {}
) {
    val isUser = message.role == "user"
    val character = remember(characterId) { Character.find(characterId) }
    var showMoreMenu by remember { mutableStateOf(false) }

    if (isUser) {
        // Modern User Message Bubble (Hugs content, sleek gradient & subtle glowing border)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 36.dp, max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFFA78BFA).copy(alpha = 0.35f),
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp
                )
            }
        }
    } else {
        // AI Assistant Message Bubble
        Row(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            val spriteRes = MascotSpriteHelper.getSprite(character.id, MascotState.IDLE)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                character.gradientColors.firstOrNull()?.copy(alpha = 0.4f) ?: Color(0xFF7C3AED),
                                Color(0xFF1E1B4B)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        character.gradientColors.firstOrNull()?.copy(alpha = 0.6f) ?: Color(0xFF8B5CF6),
                        CircleShape
                    )
                    .clickable { MascotEventBus.postEvent(MascotEvent.Tap) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = spriteRes),
                    contentDescription = characterName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = Color(0xFF131728),
                border = BorderStroke(1.dp, Color(0xFF262D4A)),
                shadowElevation = 2.dp,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val urlRegex = remember { Regex("https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?") }
                    val foundUrls = remember(message.content) { urlRegex.findAll(message.content).map { it.value }.toList() }

                    // Dynamic Live Streaming / Thinking Indicator or Markdown Content
                    if (isStreaming && message.content.isBlank()) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(650, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF8B5CF6).copy(alpha = pulseAlpha),
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Text(
                                text = "Thinking & planning actions...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA78BFA).copy(alpha = pulseAlpha)
                            )
                        }
                    } else {
                        val infiniteTransition = rememberInfiniteTransition()
                        val cursorAlpha by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        // Rich Markdown Formatted Response
                        FormattedMarkdownContent(
                            content = if (isStreaming && cursorAlpha > 0.5f) "${message.content} ▌" else message.content,
                            textColor = Color.White
                        )
                    }

                    // Clickable URL Badges if web links are present
                    if (foundUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            foundUrls.take(2).forEach { url ->
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                                    modifier = Modifier.clickable {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            ).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "🔗 Open Link",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF60A5FA)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Interactive File Quick Actions (Open, Share, Copy Path) if an existing file is referenced
                    val referencedFile = remember(message.content, message.actionDetails) {
                        com.orbital.file.FileViewHelper.findExistingFile(context, "${message.actionDetails.orEmpty()} ${message.content}")
                    }
                    if (referencedFile != null && referencedFile.exists()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FileActionCard(file = referencedFile)
                    }

                    // Prominently Render Fetched Data / Action Outcome Card
                    ActionOutcomeCard(
                        actionDetails = message.actionDetails
                    )

                    // Antigravity-Style Live Execution Timeline
                    AntigravityExecutionTimeline(
                        message = message
                    )

                    // Contextual Interactive Next Step Options (Only when real action executed)
                    if (!isStreaming && message.nextStepSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "💡 What would you like to do next?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA78BFA)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.nextStepSuggestions.forEach { suggestion ->
                                Surface(
                                    color = Color(0xFF1E2338),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF333D66)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE2E8F0),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSuggestionClick(suggestion) }
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E1065))
                                                .clickable { onAddStepToInput(suggestion) }
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add to input",
                                                tint = Color(0xFFC084FC),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Gemini-Style Bottom Action Footer (Retry, Copy, Speak, 3-Dots Menu)
                    if (!isStreaming) {
                        var isCopied by remember { mutableStateOf(false) }
                        LaunchedEffect(isCopied) {
                            if (isCopied) {
                                kotlinx.coroutines.delay(2000)
                                isCopied = false
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Retry / Regenerate
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry & regenerate",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Copy
                            IconButton(
                                onClick = {
                                    onCopy()
                                    isCopied = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = if (isCopied) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Speak / Read aloud
                            IconButton(
                                onClick = onSpeak,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Read aloud",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // 3-Dots Overflow Menu (Report issue & Model information)
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF1E2338))
                                        .border(1.dp, Color(0xFF333D66), RoundedCornerShape(12.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF43F5E),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Report issue & Feedback",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onReportError(message.content)
                                        }
                                    )

                                    HorizontalDivider(color = Color(0xFF333D66), thickness = 0.8.dp)

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Used ${message.providerName ?: "Auto-Router"} model",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        },
                                        onClick = { showMoreMenu = false },
                                        enabled = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AntigravityExecutionTimeline(
    message: UiMessage,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Gather explicit steps or synthesize them from actionLabel/actionDetails
    val steps = remember(message.steps, message.actionLabel, message.actionDetails) {
        if (message.steps.isNotEmpty()) {
            message.steps
        } else if (!message.actionLabel.isNullOrBlank()) {
            val synthesized = mutableListOf<com.orbital.action.ExecutionStep>()
            synthesized += com.orbital.action.ExecutionStep(
                title = "Thought for 0.4s",
                status = com.orbital.action.StepStatus.INFO,
                toolName = "Reasoner",
                details = "Evaluated request intent and identified target device actions"
            )
            val lines = message.actionLabel.lines().filter { it.isNotBlank() }
            lines.forEach { line ->
                val isSuccess = line.startsWith("⚡") || line.contains("Executed")
                synthesized += com.orbital.action.ExecutionStep(
                    title = line.replace("⚡ ", "").replace("⚠️ ", "").replace("⏳ ", ""),
                    status = if (isSuccess) com.orbital.action.StepStatus.SUCCESS else com.orbital.action.StepStatus.FAILED,
                    toolName = "DeviceAction",
                    details = message.actionDetails ?: line
                )
            }
            synthesized
        } else {
            emptyList()
        }
    }

    if (steps.isEmpty()) return

    val anyFailure = steps.any { it.status == com.orbital.action.StepStatus.FAILED }
    val initialExpandedSteps = remember(steps) {
        steps.filter { it.status == com.orbital.action.StepStatus.FAILED }.map { it.id }.toSet()
    }

    var isOverallExpanded by remember { mutableStateOf(anyFailure) }
    var expandedStepIds by remember { mutableStateOf(initialExpandedSteps) }

    val totalDurationFormatted = remember(message.durationMs) {
        val ms = message.durationMs.coerceAtLeast(400L)
        if (ms < 1000) "${ms}ms" else "%.1fs".format(ms / 1000.0)
    }

    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF13141F),
        border = BorderStroke(
            1.dp,
            if (anyFailure) Color(0xFFDC2626).copy(alpha = 0.5f) else Color(0xFF2B2E42)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header Row (like Antigravity "Worked for 2m ▾")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isOverallExpanded = !isOverallExpanded }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (anyFailure) "⚠️" else "⚡",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Worked for $totalDurationFormatted",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF222436)
                    ) {
                        Text(
                            text = "${steps.size} step${if (steps.size > 1) "s" else ""}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (isOverallExpanded) "▾" else "▸",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }

            // Expanded Steps Timeline List
            AnimatedVisibility(visible = isOverallExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF222436), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(2.dp))

                    steps.forEach { step ->
                        val isStepExpanded = expandedStepIds.contains(step.id)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isStepExpanded) Color(0xFF1A1B28) else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedStepIds = if (isStepExpanded) {
                                            expandedStepIds - step.id
                                        } else {
                                            expandedStepIds + step.id
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    val statusIcon = when (step.status) {
                                        com.orbital.action.StepStatus.RUNNING -> "⏳"
                                        com.orbital.action.StepStatus.SUCCESS -> "›"
                                        com.orbital.action.StepStatus.FAILED -> "⚠️"
                                        com.orbital.action.StepStatus.INFO -> "›"
                                    }
                                    val statusColor = when (step.status) {
                                        com.orbital.action.StepStatus.RUNNING -> Color(0xFF38BDF8)
                                        com.orbital.action.StepStatus.SUCCESS -> Color(0xFF34D399)
                                        com.orbital.action.StepStatus.FAILED -> Color(0xFFF87171)
                                        com.orbital.action.StepStatus.INFO -> Color(0xFFA78BFA)
                                    }

                                    Text(
                                        text = statusIcon,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                    Text(
                                        text = step.title,
                                        fontSize = 11.sp,
                                        color = if (step.status == com.orbital.action.StepStatus.FAILED) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (step.durationMs > 0) {
                                        Text(
                                            text = "${step.durationMs}ms",
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Text(
                                        text = if (isStepExpanded) "▾" else "›",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Step Details Dropdown
                            AnimatedVisibility(visible = isStepExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0D0E15))
                                        .border(1.dp, Color(0xFF222436), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    if (!step.toolName.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Tool: ${step.toolName}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF818CF8)
                                            )
                                            Text(
                                                text = "Copy",
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                modifier = Modifier.clickable {
                                                    clipboardManager.setText(
                                                        androidx.compose.ui.text.AnnotatedString(step.details ?: step.title)
                                                    )
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    Text(
                                        text = step.details ?: "No additional execution payload.",
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 14.sp
                                    )

                                    if (step.details?.contains("Accessibility", ignoreCase = true) == true ||
                                        step.details?.contains("Settings > Accessibility", ignoreCase = true) == true) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF6366F1).copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, Color(0xFF6366F1)),
                                            modifier = Modifier.clickable {
                                                com.orbital.automation.OrbitalAccessibilityService.openSettings(context)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("⚙️", fontSize = 10.sp)
                                                Text(
                                                    "Open Accessibility Settings",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFA5B4FC)
                                                )
                                            }
                                        }
                                    }

                                    // File & Media Quick Actions (Open, Share, Copy Path)
                                    val stepFile = remember(step.details, step.title) {
                                        com.orbital.file.FileViewHelper.findExistingFile(context, "${step.details.orEmpty()} ${step.title}")
                                    }

                                    if (stepFile != null && stepFile.exists()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        FileActionPills(file = stepFile)
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

@Composable
fun ActionOutcomeCard(
    actionDetails: String?,
    modifier: Modifier = Modifier
) {
    if (actionDetails.isNullOrBlank()) return

    val lines = remember(actionDetails) {
        actionDetails.lines().map { it.trim() }.filter { it.isNotBlank() }
    }
    if (lines.isEmpty()) return

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val referencedFile = remember(actionDetails) {
        com.orbital.file.FileViewHelper.findExistingFile(context, actionDetails)
    }

    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF13192B),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))),
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0369A1).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📊", fontSize = 11.sp)
                    }
                    Text(
                        text = "Live Action Outcome",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(actionDetails))
                        android.widget.Toast.makeText(context, "Copied data to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Copy",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FormattedMarkdownContent(
                content = actionDetails,
                textColor = Color(0xFFE2E8F0)
            )

            if (referencedFile != null && referencedFile.exists()) {
                Spacer(modifier = Modifier.height(10.dp))
                FileActionCard(file = referencedFile)
            }
        }
    }
}

@Composable
fun FileActionCard(
    file: java.io.File,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val icon = remember(file.extension) {
        when (file.extension.lowercase()) {
            "pptx", "ppt" -> "📽️"
            "docx", "doc" -> "📝"
            "xlsx", "xls", "csv" -> "📊"
            "pdf" -> "📄"
            "png", "jpg", "jpeg", "webp" -> "🖼️"
            else -> "📃"
        }
    }

    val sizeFormatted = remember(file.length()) {
        val bytes = file.length()
        if (bytes < 1024) "${bytes} B"
        else if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0)
        else "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    var showInAppViewer by remember { mutableStateOf(false) }

    if (showInAppViewer) {
        InAppFileViewerModal(file = file, onDismiss = { showInAppViewer = false })
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showInAppViewer = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 16.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$sizeFormatted • Tap to view inside app",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Primary In-App View Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2563EB),
                    border = BorderStroke(1.dp, Color(0xFF60A5FA)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showInAppViewer = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("👁️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "View In-App",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Share Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable { com.orbital.file.FileViewHelper.shareFile(context, file) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📤", fontSize = 11.sp)
                        Text(
                            "Share",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                // Copy Path Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(file.absolutePath))
                        android.widget.Toast.makeText(context, "Copied path", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📋", fontSize = 11.sp)
                        Text(
                            "Path",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileActionPills(
    file: java.io.File,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showInAppViewer by remember { mutableStateOf(false) }

    if (showInAppViewer) {
        InAppFileViewerModal(file = file, onDismiss = { showInAppViewer = false })
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF2563EB).copy(alpha = 0.25f),
            border = BorderStroke(1.dp, Color(0xFF3B82F6)),
            modifier = Modifier.clickable { showInAppViewer = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("👁️", fontSize = 10.sp)
                Text(
                    "View ${file.name}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF93C5FD),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF0284C7).copy(alpha = 0.2f),
            border = BorderStroke(1.dp, Color(0xFF0284C7)),
            modifier = Modifier.clickable { com.orbital.file.FileViewHelper.shareFile(context, file) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📤", fontSize = 10.sp)
                Text(
                    "Share",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7DD3FC)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.clickable {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(file.absolutePath))
                android.widget.Toast.makeText(context, "Copied path", android.widget.Toast.LENGTH_SHORT).show()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📋", fontSize = 10.sp)
                Text(
                    "Copy Path",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewUserChatBubbleItem() {
    ChatBubbleItem(
        message = UiMessage(
            role = "user",
            content = "hi"
        ),
        characterName = "Aether",
        onCopy = {},
        onSpeak = {}
    )
}

