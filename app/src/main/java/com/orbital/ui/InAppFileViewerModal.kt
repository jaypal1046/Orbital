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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbital.file.FileViewHelper
import com.orbital.file.UniversalFileEngine
import java.io.File

@Composable
fun InAppFileViewerModal(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val category = remember(file) { UniversalFileEngine.detectCategory(file.name) }
    val sizeFormatted = remember(file.length()) {
        val bytes = file.length()
        if (bytes < 1024) "${bytes} B"
        else if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0)
        else "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    val typeLabel = remember(category, file.extension) {
        when (category) {
            UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION -> "PowerPoint Presentation (.${file.extension})"
            UniversalFileEngine.FileCategory.WORD_DOCUMENT -> "Word Document (.${file.extension})"
            UniversalFileEngine.FileCategory.SPREADSHEET_EXCEL -> "Excel Spreadsheet (.${file.extension})"
            UniversalFileEngine.FileCategory.SPREADSHEET_CSV -> "CSV Data Table (.${file.extension})"
            UniversalFileEngine.FileCategory.PDF_DOCUMENT -> "PDF Document (.${file.extension})"
            UniversalFileEngine.FileCategory.TEXT_OR_CODE -> "Code / Text File (.${file.extension})"
            else -> "File Viewer (.${file.extension})"
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
                .padding(top = 28.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0D0F1D),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8)))),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF15192E))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val iconEmoji = remember(category) {
                            when (category) {
                                UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION -> "📽️"
                                UniversalFileEngine.FileCategory.WORD_DOCUMENT -> "📝"
                                UniversalFileEngine.FileCategory.SPREADSHEET_EXCEL,
                                UniversalFileEngine.FileCategory.SPREADSHEET_CSV -> "📊"
                                UniversalFileEngine.FileCategory.PDF_DOCUMENT -> "📄"
                                else -> if (listOf("png", "jpg", "jpeg", "webp").contains(file.extension.lowercase())) "🖼️" else "📃"
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF202646)),
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
                                maxLines = 1
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
                                modifier = Modifier.size(17.dp)
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
                        .background(Color(0xFF090B14))
                ) {
                    when {
                        listOf("png", "jpg", "jpeg", "webp", "bmp").contains(file.extension.lowercase()) -> {
                            InAppImageViewer(file = file)
                        }
                        category == UniversalFileEngine.FileCategory.PDF_DOCUMENT -> {
                            InAppPdfViewer(file = file)
                        }
                        category == UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION -> {
                            InAppPptxViewer(file = file)
                        }
                        category == UniversalFileEngine.FileCategory.SPREADSHEET_CSV || category == UniversalFileEngine.FileCategory.SPREADSHEET_EXCEL -> {
                            InAppSpreadsheetViewer(file = file, isXlsx = category == UniversalFileEngine.FileCategory.SPREADSHEET_EXCEL)
                        }
                        category == UniversalFileEngine.FileCategory.WORD_DOCUMENT -> {
                            InAppDocxViewer(file = file)
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

@Composable
fun InAppPdfViewer(file: File) {
    val context = LocalContext.current
    val pages = remember(file) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val width = (page.width * 1.5f).toInt()
                val height = (page.height * 1.5f).toInt()
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

    if (pages.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(pages) { index, pageBitmap ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Image(
                            bitmap = pageBitmap.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Page ${index + 1} of ${pages.size}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    } else {
        // Text fallback
        val textResult = remember(file) { UniversalFileEngine.readPdfText(file) }
        val textContent = if (textResult is com.orbital.file.FileOperationResult.Success) textResult.content.orEmpty() else ""
        LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            item {
                Text(
                    text = textContent.ifBlank { "No text could be extracted from PDF." },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun InAppPptxViewer(file: File) {
    val result = remember(file) { UniversalFileEngine.readPptxText(file) }
    val content = if (result is com.orbital.file.FileOperationResult.Success) result.content.orEmpty() else ""

    val slides = remember(content) {
        val slideBlocks = content.split(Regex("--- Slide \\d+ ---\n?"))
        val slideTitles = Regex("--- Slide (\\d+) ---").findAll(content).map { it.groupValues[1] }.toList()
        slideBlocks.filter { it.isNotBlank() }.mapIndexed { index, text ->
            val num = if (index < slideTitles.size) slideTitles[index] else "${index + 1}"
            Pair(num, text.trim())
        }
    }

    if (slides.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(slides) { (slideNum, slideContent) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF16192E),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC)))),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF4F46E5)
                            ) {
                                Text(
                                    text = "SLIDE $slideNum",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text("📽️ Presentation", fontSize = 11.sp, color = Color(0xFFA5B4FC))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FormattedMarkdownContent(
                            content = slideContent,
                            textColor = Color(0xFFF1F5F9)
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No slides found in presentation.", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
    }
}

@Composable
fun InAppDocxViewer(file: File) {
    val result = remember(file) { UniversalFileEngine.readDocxText(file) }
    val content = if (result is com.orbital.file.FileOperationResult.Success) result.content.orEmpty() else ""

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131728),
                border = BorderStroke(1.dp, Color(0xFF273154)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Document Reader",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF60A5FA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FormattedMarkdownContent(
                        content = content.ifBlank { "Document is empty." },
                        textColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
fun InAppSpreadsheetViewer(file: File, isXlsx: Boolean) {
    val rawResult = remember(file, isXlsx) {
        if (isXlsx) UniversalFileEngine.readXlsx(file) else UniversalFileEngine.readCsv(file)
    }

    val lines = remember(rawResult) {
        if (rawResult is com.orbital.file.FileOperationResult.Success) {
            rawResult.content.orEmpty().lines().filter { it.isNotBlank() }
        } else emptyList()
    }

    val rows = remember(lines, file) {
        val parsed = mutableListOf<List<String>>()
        val delimiter = if (file.extension.equals("tsv", ignoreCase = true)) "\t" else if (file.extension.equals("csv", ignoreCase = true)) "," else " | "
        
        try {
            if (file.extension.equals("csv", ignoreCase = true) || file.extension.equals("tsv", ignoreCase = true)) {
                val fileLines = file.readLines()
                fileLines.forEach { l ->
                    val cells = if (delimiter == ",") l.split(",").map { it.trim().removeSurrounding("\"") } else l.split("\t")
                    parsed.add(cells)
                }
            } else {
                lines.forEach { l ->
                    if (l.contains(" | ")) {
                        val rowStr = l.substringAfter(": ")
                        val cells = rowStr.split(" | ").map { it.trim() }
                        parsed.add(cells)
                    }
                }
            }
        } catch (_: Exception) {}
        parsed
    }

    if (rows.isNotEmpty()) {
        val horizontalScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                itemsIndexed(rows) { rowIndex, cellList ->
                    val isHeader = rowIndex == 0
                    Row(
                        modifier = Modifier
                            .background(
                                if (isHeader) Color(0xFF1E293B)
                                else if (rowIndex % 2 == 0) Color(0xFF0F172A)
                                else Color(0xFF131B30)
                            )
                            .border(0.5.dp, Color(0xFF334155))
                    ) {
                        // Row Number Column
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                                .border(0.5.dp, Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isHeader) "#" else "$rowIndex",
                                fontSize = 11.sp,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHeader) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }

                        cellList.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .border(0.5.dp, Color(0xFF334155)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cell,
                                    fontSize = 12.sp,
                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHeader) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No spreadsheet rows found.", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
    }
}

@Composable
fun InAppTextViewer(file: File) {
    val content = remember(file) {
        try {
            file.readText()
        } catch (e: Exception) {
            "Could not read text: ${e.message}"
        }
    }

    val lines = remember(content) { content.lines() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index % 2 == 0) Color(0xFF0F111D) else Color(0xFF131625))
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(36.dp)
                        .padding(end = 8.dp)
                )
                Text(
                    text = line,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
