package com.orbital.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AttachmentActionOption(
    val title: String,
    val subtitle: String,
    val icon: String,
    val gradient: List<Color>,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickPdf: () -> Unit,
    onPickDocument: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onReadLiveScreen: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F121F),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📎 AI Media & Document Reader",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Attach files, images, or inspect live screen with Orbital",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Documents Section
            Text(
                text = "📄 DOCUMENTS (P&L, Balance Sheet, PDF)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF60A5FA),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            val docOptions = listOf(
                AttachmentActionOption(
                    title = "PDF Financial & General Reports",
                    subtitle = "P&L statements, balance sheets, annual reports, contracts",
                    icon = "📄",
                    gradient = listOf(Color(0xFFEF4444), Color(0xFFDC2626)),
                    onClick = { onDismiss(); onPickPdf() }
                ),
                AttachmentActionOption(
                    title = "Text, Markdown & Word Documents",
                    subtitle = "Financial statements, text ledgers, notes, specifications",
                    icon = "📝",
                    gradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    onClick = { onDismiss(); onPickDocument() }
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                docOptions.forEach { opt -> RenderOptionCard(opt) }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Images Section (On-Device OCR)
            Text(
                text = "🖼️ IMAGES (On-Device OCR & Vision)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA78BFA),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            val imageOptions = listOf(
                AttachmentActionOption(
                    title = "Gallery Image / Statement Photo",
                    subtitle = "Extracts financial tables on-device via OCR before asking AI",
                    icon = "🖼️",
                    gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                    onClick = { onDismiss(); onPickImage() }
                ),
                AttachmentActionOption(
                    title = "Camera Capture (Receipts & Invoices)",
                    subtitle = "Take a photo of printed balance sheet, bill, or calculation",
                    icon = "📷",
                    gradient = listOf(Color(0xFF10B981), Color(0xFF047857)),
                    onClick = { onDismiss(); onTakePhoto() }
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                imageOptions.forEach { opt -> RenderOptionCard(opt) }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Other & Data Section
            Text(
                text = "📂 OTHER (Spreadsheets, Data & Live Screen)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            val otherOptions = listOf(
                AttachmentActionOption(
                    title = "Read Live Screen",
                    subtitle = "Inspect, OCR, and analyze whatever financial app is active",
                    icon = "📱",
                    gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                    onClick = { onDismiss(); onReadLiveScreen() }
                ),
                AttachmentActionOption(
                    title = "Email Smart Staging (Co-Pilot)",
                    subtitle = "Draft reply into active email (Outlook, Gmail) with floating review HUD",
                    icon = "✉️",
                    gradient = listOf(Color(0xFF6366F1), Color(0xFF4338CA)),
                    onClick = { onDismiss(); onQuickAction("Inspect the active email on screen, synthesize an appropriate reply, and stage it into the compose window.") }
                ),
                AttachmentActionOption(
                    title = "CSV, JSON & Data Logs",
                    subtitle = "Analyze raw tabular data, ledger exports, and data dumps",
                    icon = "📊",
                    gradient = listOf(Color(0xFF06B6D4), Color(0xFF0891B2)),
                    onClick = { onDismiss(); onPickDocument() }
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                otherOptions.forEach { opt -> RenderOptionCard(opt) }
            }
        }
    }
}

@Composable
private fun RenderOptionCard(opt: AttachmentActionOption) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161A2C),
        border = BorderStroke(1.dp, Color(0xFF262E4A)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { opt.onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(opt.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = opt.icon, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = opt.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = opt.subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 15.sp
                )
            }
        }
    }
}
