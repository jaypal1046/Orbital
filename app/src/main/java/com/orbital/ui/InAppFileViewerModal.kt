package com.orbital.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbital.file.DocxParagraphData
import com.orbital.file.FileViewHelper
import com.orbital.file.PptSlideData
import com.orbital.file.UniversalFileEngine
import java.io.File

@Composable
fun InAppFileViewerModal(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val category = remember(file) { UniversalFileEngine.detectCategory(file.name) }
    val ext = remember(file) { file.extension.lowercase() }

    val sizeFormatted = remember(file.length()) {
        val bytes = file.length()
        if (bytes < 1024) "${bytes} B"
        else if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0)
        else "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    val typeLabel = remember(category, ext) {
        when {
            category == UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION -> "PowerPoint Presentation (.$ext)"
            category == UniversalFileEngine.FileCategory.WORD_DOCUMENT -> "Word Document (.$ext)"
            category == UniversalFileEngine.FileCategory.PDF_DOCUMENT -> "PDF Document (.$ext)"
            ext == "json" -> "JSON Structured Document (.$ext)"
            ext == "xml" -> "XML Structured Document (.$ext)"
            ext == "md" -> "Markdown Document (.$ext)"
            else -> "Text / Code File (.$ext)"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF090C19),
            border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8)))),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13182C))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val iconEmoji = remember(category, ext) {
                            when {
                                category == UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION -> "📽️"
                                category == UniversalFileEngine.FileCategory.WORD_DOCUMENT -> "📝"
                                category == UniversalFileEngine.FileCategory.PDF_DOCUMENT -> "📄"
                                ext == "json" -> "📦"
                                ext == "xml" -> "🏷️"
                                ext == "md" -> "📑"
                                listOf("png", "jpg", "jpeg", "webp").contains(ext) -> "🖼️"
                                else -> "📃"
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(iconEmoji, fontSize = 18.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$typeLabel • $sizeFormatted",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Share externally
                        IconButton(
                            onClick = { FileViewHelper.shareFile(context, file) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close Dialog
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF262D4A), thickness = 1.dp)

                // Document Content Viewer based on category
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(Color(0xFF070913))
                ) {
                    when {
                        listOf("png", "jpg", "jpeg", "webp", "bmp").contains(ext) -> {
                            InAppImageViewer(file = file)
                        }
                        category == UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION -> {
                            InAppPptxViewer(file = file)
                        }
                        category == UniversalFileEngine.FileCategory.WORD_DOCUMENT -> {
                            InAppDocxViewer(file = file)
                        }
                        category == UniversalFileEngine.FileCategory.PDF_DOCUMENT -> {
                            InAppPdfViewer(file = file)
                        }
                        ext == "json" -> {
                            InAppJsonViewer(file = file)
                        }
                        ext == "xml" -> {
                            InAppXmlViewer(file = file)
                        }
                        else -> {
                            InAppTextViewer(file = file)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InAppImageViewer(file: File) {
    val bitmap = remember(file) {
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not render image preview.", color = Color(0xFFEF4444), fontSize = 13.sp)
        }
    }
}

// ==========================================
// 1. PowerPoint (.pptx) Viewer (Authentic 16:9 Presentation Stage)
// ==========================================

@Composable
fun InAppPptxViewer(file: File) {
    val structuredSlides = remember(file) { UniversalFileEngine.extractPptxSlides(file) }
    
    val fallbackSlides = remember(file, structuredSlides) {
        if (structuredSlides.isEmpty()) {
            val result = UniversalFileEngine.readPptxText(file)
            val content = if (result is com.orbital.file.FileOperationResult.Success) result.content.orEmpty() else ""
            val slideBlocks = content.split(Regex("--- Slide \\d+ ---\n?")).filter { it.isNotBlank() }
            slideBlocks.mapIndexed { idx, sText ->
                val lines = sText.lines().filter { it.isNotBlank() }
                val title = lines.firstOrNull() ?: "Slide ${idx + 1}"
                val bullets = if (lines.size > 1) lines.drop(1) else emptyList()
                PptSlideData(slideNumber = idx + 1, title = title, bullets = bullets, rawText = sText)
            }
        } else {
            emptyList()
        }
    }

    val slides = if (structuredSlides.isNotEmpty()) structuredSlides else fallbackSlides
    var currentSlideIndex by remember { mutableIntStateOf(0) }

    if (slides.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📽️", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No presentation slides found in file.", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        }
        return
    }

    val currentSlide = slides.getOrElse(currentSlideIndex) { slides[0] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070913))
    ) {
        // Presentation Navigation Stage Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101426))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Slide Counter & Switcher
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4F46E5).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(0xFF6366F1))
                ) {
                    Text(
                        text = "Slide ${currentSlideIndex + 1} of ${slides.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = { if (currentSlideIndex > 0) currentSlideIndex-- },
                    enabled = currentSlideIndex > 0,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Slide",
                        tint = if (currentSlideIndex > 0) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { if (currentSlideIndex < slides.size - 1) currentSlideIndex++ },
                    enabled = currentSlideIndex < slides.size - 1,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Slide",
                        tint = if (currentSlideIndex < slides.size - 1) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Text(
                    text = "16:9 Widescreen Deck",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

        // Main Scrollable Presentation Canvas & Details Body
        val mainScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(mainScrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. True 16:9 Widescreen Slide Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8))), RoundedCornerShape(16.dp))
                    .shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                WidescreenSlideCanvas(slide = currentSlide, totalSlides = slides.size)
            }

            // 2. Slide Thumbnails Filmstrip
            Text(
                text = "SLIDE DECK THUMBNAILS (${slides.size})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(slides) { idx, slide ->
                    val isSelected = idx == currentSlideIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF12162A),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) Color(0xFF818CF8) else Color(0xFF262D4A)
                        ),
                        modifier = Modifier
                            .clickable { currentSlideIndex = idx }
                            .width(130.dp)
                            .aspectRatio(16f / 9f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text("📽️", fontSize = 8.sp)
                            }
                            Text(
                                text = slide.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }

            // 3. Slide Breakdown & Speaker Content Section
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111528),
                border = BorderStroke(1.dp, Color(0xFF232A46)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SLIDE ${currentSlideIndex + 1} OVERVIEW & NOTES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${currentSlide.bullets.size} Key Point(s)",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

                    if (currentSlide.bullets.isNotEmpty()) {
                        currentSlide.bullets.forEachIndexed { bIdx, bText ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF4F46E5).copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, Color(0xFF6366F1)),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "${bIdx + 1}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA5B4FC),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = bText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else if (currentSlide.rawText.isNotBlank()) {
                        FormattedMarkdownContent(
                            content = currentSlide.rawText,
                            textColor = Color(0xFFCBD5E1)
                        )
                    } else {
                        Text(
                            text = "No additional presenter notes for this slide.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WidescreenSlideCanvas(
    slide: PptSlideData,
    totalSlides: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A1D36), Color(0xFF0E1122), Color(0xFF0A0D1B))
                )
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Slide Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF4F46E5)
                ) {
                    Text(
                        text = "SLIDE ${slide.slideNumber}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📽️", fontSize = 10.sp)
                    Text("Orbital Deck", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                }
            }

            // Slide Center Title & Bullets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = slide.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                // Render first 2-3 prominent bullets directly on 16:9 canvas
                val displayBullets = slide.bullets.take(2)
                if (displayBullets.isNotEmpty()) {
                    displayBullets.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8))
                            )
                            Text(
                                text = b,
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Slide Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Orbital AI Presentation", fontSize = 8.sp, color = Color(0xFF64748B))
                Text("${slide.slideNumber} / $totalSlides", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
            }
        }
    }
}

// ==========================================
// 2. Word (.docx) Document Viewer
// ==========================================

@Composable
fun InAppDocxViewer(file: File) {
    val paragraphs = remember(file) { UniversalFileEngine.extractDocxParagraphs(file) }
    
    val fallbackText = remember(file, paragraphs) {
        if (paragraphs.isEmpty()) {
            val result = UniversalFileEngine.readDocxText(file)
            if (result is com.orbital.file.FileOperationResult.Success) result.content.orEmpty() else ""
        } else ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Document Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13172A))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2563EB).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFF60A5FA))
            ) {
                Text(
                    text = "📝 A4 Document Reader",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            val count = if (paragraphs.isNotEmpty()) paragraphs.size else fallbackText.lines().filter { it.isNotBlank() }.size
            Text(
                text = "$count Section(s)",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }

        HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF13182C),
                    border = BorderStroke(1.dp, Color(0xFF2E385D)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (paragraphs.isNotEmpty()) {
                            paragraphs.forEach { p ->
                                if (p.isHeading) {
                                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                                        Text(
                                            text = p.text,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF60A5FA),
                                            lineHeight = 24.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(color = Color(0xFF2563EB).copy(alpha = 0.4f), thickness = 1.dp)
                                    }
                                } else if (p.isBullet) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF38BDF8))
                                        )
                                        Text(
                                            text = p.text,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            color = Color(0xFFE2E8F0),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = p.text,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        } else if (fallbackText.isNotBlank()) {
                            FormattedMarkdownContent(
                                content = fallbackText,
                                textColor = Color(0xFFE2E8F0)
                            )
                        } else {
                            Text(
                                text = "Document has no readable text content.",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. JSON Structured Document Viewer
// ==========================================

@Composable
fun InAppJsonViewer(file: File) {
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }

    val rawContent = remember(file) {
        try {
            file.readText()
        } catch (e: Exception) {
            "Could not read JSON: ${e.message}"
        }
    }

    val formattedJson = remember(rawContent) {
        try {
            val jsonElement = org.json.JSONObject(rawContent)
            jsonElement.toString(2)
        } catch (_: Exception) {
            try {
                val jsonArray = org.json.JSONArray(rawContent)
                jsonArray.toString(2)
            } catch (_: Exception) {
                rawContent
            }
        }
    }

    val lines = remember(formattedJson) { formattedJson.lines() }

    val filteredLines = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) lines.mapIndexed { idx, line -> Pair(idx + 1, line) }
        else {
            lines.mapIndexed { idx, line -> Pair(idx + 1, line) }
                .filter { it.second.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13172A))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF10B981).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFF34D399))
            ) {
                Text(
                    text = "📦 JSON Document • ${lines.size} Lines",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(formattedJson)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy JSON",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search Filter
        Surface(
            color = Color(0xFF0E1222),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Filter JSON keys / values...", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        innerTextField()
                    }
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(filteredLines) { (lineNum, lineText) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (lineNum % 2 == 0) Color(0xFF0D0F1C) else Color(0xFF101322))
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "$lineNum",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(36.dp)
                            .padding(end = 8.dp)
                    )

                    // Render highlighted JSON line
                    val annotated = remember(lineText) {
                        buildAnnotatedString {
                            val keyMatch = Regex("""("[^"]+")\s*:""").find(lineText)
                            if (keyMatch != null) {
                                val keyStr = keyMatch.groupValues[1]
                                val prefix = lineText.substring(0, keyMatch.range.first)
                                val rest = lineText.substring(keyMatch.range.last + 1)
                                append(prefix)
                                pushStyle(SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold))
                                append(keyStr)
                                pop()
                                append(":")
                                pushStyle(SpanStyle(color = Color(0xFFFDE047)))
                                append(rest)
                                pop()
                            } else {
                                pushStyle(SpanStyle(color = Color(0xFFE2E8F0)))
                                append(lineText)
                                pop()
                            }
                        }
                    }

                    Text(
                        text = annotated,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. XML Structured Document Viewer
// ==========================================

@Composable
fun InAppXmlViewer(file: File) {
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }

    val content = remember(file) {
        try {
            file.readText()
        } catch (e: Exception) {
            "Could not read XML: ${e.message}"
        }
    }

    val lines = remember(content) { content.lines() }

    val filteredLines = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) lines.mapIndexed { idx, line -> Pair(idx + 1, line) }
        else {
            lines.mapIndexed { idx, line -> Pair(idx + 1, line) }
                .filter { it.second.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13172A))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFFFBBF24))
            ) {
                Text(
                    text = "🏷️ XML Document • ${lines.size} Lines",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(content)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy XML",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search Filter
        Surface(
            color = Color(0xFF0E1222),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Filter XML tags / attributes...", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        innerTextField()
                    }
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(filteredLines) { (lineNum, lineText) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (lineNum % 2 == 0) Color(0xFF0D0F1C) else Color(0xFF101322))
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "$lineNum",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(36.dp)
                            .padding(end = 8.dp)
                    )

                    val annotated = remember(lineText) {
                        buildAnnotatedString {
                            var cursor = 0
                            val tagRegex = Regex("""(</?[a-zA-Z0-9_\-:]+)|(/?>)""")
                            tagRegex.findAll(lineText).forEach { match ->
                                if (match.range.first > cursor) {
                                    append(lineText.substring(cursor, match.range.first))
                                }
                                pushStyle(SpanStyle(color = Color(0xFFF472B6), fontWeight = FontWeight.SemiBold))
                                append(match.value)
                                pop()
                                cursor = match.range.last + 1
                            }
                            if (cursor < lineText.length) {
                                append(lineText.substring(cursor))
                            }
                        }
                    }

                    Text(
                        text = annotated,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. PDF (.pdf) Document Viewer
// ==========================================

@Composable
fun InAppPdfViewer(file: File) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val pages = remember(file) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val width = (page.width * 2.0f).toInt()
                val height = (page.height * 2.0f).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            pfd.close()
        } catch (_: Exception) {}
        bitmaps
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13172A))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFDC2626).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFFF87171))
            ) {
                Text(
                    text = "📄 ${pages.size} PDF Page(s)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF87171),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { if (zoomScale > 0.6f) zoomScale -= 0.2f },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(
                    onClick = { if (zoomScale < 2.0f) zoomScale += 0.2f },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

        if (pages.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(pages) { index, pageBitmap ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(zoomScale.coerceIn(0.5f, 1.0f))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Image(
                                bitmap = pageBitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Page ${index + 1} of ${pages.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            val textResult = remember(file) { UniversalFileEngine.readPdfText(file) }
            val textContent = if (textResult is com.orbital.file.FileOperationResult.Success) textResult.content.orEmpty() else ""
            LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                item {
                    FormattedMarkdownContent(
                        content = textContent.ifBlank { "No readable text content found in PDF." },
                        textColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. Generic Text & Code File Viewer
// ==========================================

@Composable
fun InAppTextViewer(file: File) {
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }

    val content = remember(file) {
        try {
            file.readText()
        } catch (e: Exception) {
            "Could not read text: ${e.message}"
        }
    }

    val lines = remember(content) { content.lines() }

    val filteredLines = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) lines.mapIndexed { idx, line -> Pair(idx + 1, line) }
        else {
            lines.mapIndexed { idx, line -> Pair(idx + 1, line) }
                .filter { it.second.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13172A))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${lines.size} Lines • .${file.extension}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )

            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(content)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Content",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search Filter
        Surface(
            color = Color(0xFF0E1222),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Filter lines...", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        innerTextField()
                    }
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E2540), thickness = 0.8.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(filteredLines) { (lineNum, lineText) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (lineNum % 2 == 0) Color(0xFF0D0F1C) else Color(0xFF101322))
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "$lineNum",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(36.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = lineText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
