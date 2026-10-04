package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Rich, high-fidelity Markdown rendering component for Orbital / Aether chat.
 * Correctly renders:
 * - GFM Markdown Tables with horizontal scroll, cell padding, alternating row colors, header styling
 * - Syntax-highlighted Code Blocks with copy feedback and language tags
 * - Images (![alt](url)) via Coil with loading fallback, captions, and tap-to-expand
 * - Headings (H1 to H6) with proportionate scaling & accent tints
 * - Ordered & Unordered lists with stylish pill badges and bullet indicators
 * - Blockquotes / Callout cards with accent borders
 * - Horizontal rules / dividers
 * - Inline formatting (Bold, Italic, Bold-Italic, Strikethrough, Code pill, Clickable hyperlinks, <br> linebreaks)
 */
@Composable
fun FormattedMarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    if (content.isBlank()) return

    val blocks = remember(content) { parseMarkdownDocument(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.HeadingBlock -> {
                    MarkdownHeadingItem(block)
                }
                is MarkdownBlock.CodeBlock -> {
                    MarkdownCodeBlockCard(block)
                }
                is MarkdownBlock.TableBlock -> {
                    MarkdownTableCard(block)
                }
                is MarkdownBlock.ImageBlock -> {
                    MarkdownImageCard(block)
                }
                is MarkdownBlock.ListBlock -> {
                    MarkdownListGroup(block, textColor)
                }
                is MarkdownBlock.QuoteBlock -> {
                    MarkdownQuoteCard(block, textColor)
                }
                is MarkdownBlock.DividerBlock -> {
                    MarkdownDividerItem()
                }
                is MarkdownBlock.TextBlock -> {
                    MarkdownParagraphItem(block.text, textColor)
                }
            }
        }
    }
}

// ==========================================
// MARKDOWN AST / MODEL DEFINITIONS
// ==========================================

sealed class MarkdownBlock {
    data class HeadingBlock(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class TableBlock(
        val headers: List<String>,
        val alignments: List<TableCellAlignment>,
        val rows: List<List<String>>
    ) : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String) : MarkdownBlock()
    data class ListBlock(val items: List<MarkdownListItem>) : MarkdownBlock()
    data class QuoteBlock(val text: String) : MarkdownBlock()
    object DividerBlock : MarkdownBlock()
    data class TextBlock(val text: String) : MarkdownBlock()
}

enum class TableCellAlignment {
    START, CENTER, END
}

sealed class MarkdownListItem {
    data class Ordered(val numberStr: String, val text: String) : MarkdownListItem()
    data class Unordered(val bullet: String, val text: String) : MarkdownListItem()
    data class Task(val isChecked: Boolean, val text: String) : MarkdownListItem()
}

// ==========================================
// PARSER IMPLEMENTATION
// ==========================================

/**
 * Parses raw markdown into structured AST blocks.
 */
fun parseMarkdownDocument(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()

    // Extract code blocks (``` ... ```)
    val codeFenceRegex = Regex("```([a-zA-Z0-9_-]*)\\r?\\n?([\\s\\S]*?)```")
    var lastIndex = 0
    val matches = codeFenceRegex.findAll(raw)

    for (match in matches) {
        val matchStart = match.range.first
        if (matchStart > lastIndex) {
            val nonCodeText = raw.substring(lastIndex, matchStart)
            parseNonCodeSegments(nonCodeText, blocks)
        }
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(language, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex)
        parseNonCodeSegments(remaining, blocks)
    }

    if (blocks.isEmpty() && raw.isNotEmpty()) {
        blocks.add(MarkdownBlock.TextBlock(raw))
    }

    return blocks
}

/**
 * Parses non-code text segments into Headings, Tables, Images, Lists, Quotes, Dividers, and Paragraphs.
 */
private fun parseNonCodeSegments(segment: String, outBlocks: MutableList<MarkdownBlock>) {
    val lines = segment.lines()
    var lineIdx = 0

    val currentListItems = mutableListOf<MarkdownListItem>()
    val currentParagraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (currentParagraphLines.isNotEmpty()) {
            val text = currentParagraphLines.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                outBlocks.add(MarkdownBlock.TextBlock(text))
            }
            currentParagraphLines.clear()
        }
    }

    fun flushList() {
        if (currentListItems.isNotEmpty()) {
            outBlocks.add(MarkdownBlock.ListBlock(currentListItems.toList()))
            currentListItems.clear()
        }
    }

    fun flushAll() {
        flushParagraph()
        flushList()
    }

    while (lineIdx < lines.size) {
        val line = lines[lineIdx]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            flushAll()
            lineIdx++
            continue
        }

        // 1. Check Standalone Image: ![alt](url)
        val imageMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
        if (imageMatch != null) {
            flushAll()
            val alt = imageMatch.groupValues[1].trim()
            val url = imageMatch.groupValues[2].trim()
            outBlocks.add(MarkdownBlock.ImageBlock(alt, url))
            lineIdx++
            continue
        }

        // 2. Check Horizontal Divider: ---, ***, ___
        if (trimmed.matches(Regex("^(?:-{3,}|\\*{3,}|_{3,})$"))) {
            flushAll()
            outBlocks.add(MarkdownBlock.DividerBlock)
            lineIdx++
            continue
        }

        // 3. Check Headings: #, ##, ###, ####, #####, ######
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushAll()
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2].trim()
            outBlocks.add(MarkdownBlock.HeadingBlock(level, headingText))
            lineIdx++
            continue
        }

        // 4. Check Blockquote: > text
        if (trimmed.startsWith(">")) {
            flushAll()
            val quoteLines = mutableListOf<String>()
            while (lineIdx < lines.size && lines[lineIdx].trim().startsWith(">")) {
                quoteLines.add(lines[lineIdx].trim().removePrefix(">").trim())
                lineIdx++
            }
            outBlocks.add(MarkdownBlock.QuoteBlock(quoteLines.joinToString("\n")))
            continue
        }

        // 5. Check Markdown Table: starts with pipe or contains pipe delimiters with a separator row following
        if (isTableStart(lines, lineIdx)) {
            flushAll()
            val (tableBlock, consumedLines) = parseTableBlock(lines, lineIdx)
            if (tableBlock != null) {
                outBlocks.add(tableBlock)
                lineIdx += consumedLines
                continue
            }
        }

        // 6. Check Task List item: - [ ] or - [x]
        val taskMatch = Regex("^[-*+]\\s+\\[([ xX])\\]\\s+(.+)$").find(trimmed)
        if (taskMatch != null) {
            flushParagraph()
            val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
            val itemText = taskMatch.groupValues[2].trim()
            currentListItems.add(MarkdownListItem.Task(isChecked, itemText))
            lineIdx++
            continue
        }

        // 7. Check Ordered list item: 1. or 1) or [1] or 1️⃣
        val orderedMatch = Regex("^(\\d+[.)]|\\d+️⃣|\\[\\d+\\])\\s*(.+)$").find(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            val numStr = orderedMatch.groupValues[1].trim()
            val itemText = orderedMatch.groupValues[2].trim()
            currentListItems.add(MarkdownListItem.Ordered(numStr, itemText))
            lineIdx++
            continue
        }

        // 8. Check Unordered list item: * or - or + or •
        val bulletMatch = Regex("^([*•+-])\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            val bullet = bulletMatch.groupValues[1]
            val itemText = bulletMatch.groupValues[2].trim()
            currentListItems.add(MarkdownListItem.Unordered(bullet, itemText))
            lineIdx++
            continue
        }

        // 9. Regular text paragraph line
        flushList()
        currentParagraphLines.add(line)
        lineIdx++
    }

    flushAll()
}

/**
 * Checks if current index marks the start of a Markdown Table.
 */
private fun isTableStart(lines: List<String>, startIdx: Int): Boolean {
    if (startIdx >= lines.size) return false
    val headerLine = lines[startIdx].trim()
    if (!headerLine.contains("|")) return false

    // Look ahead for separator line: |---|---| or |:---|:---:|
    if (startIdx + 1 < lines.size) {
        val sepLine = lines[startIdx + 1].trim()
        if (sepLine.contains("|") && sepLine.contains("-")) {
            val validSeparator = sepLine.replace("|", "").replace(":", "").replace("-", "").replace(" ", "").isEmpty()
            if (validSeparator) return true
        }
    }

    return headerLine.startsWith("|") && headerLine.endsWith("|") && headerLine.count { it == '|' } >= 2
}

/**
 * Parses consecutive table lines into a TableBlock.
 */
private fun parseTableBlock(lines: List<String>, startIdx: Int): Pair<MarkdownBlock.TableBlock?, Int> {
    var idx = startIdx
    val tableLines = mutableListOf<String>()

    while (idx < lines.size) {
        val line = lines[idx].trim()
        if (line.isEmpty() || !line.contains("|")) break
        tableLines.add(line)
        idx++
    }

    if (tableLines.isEmpty()) return Pair(null, 0)

    val rawHeader = tableLines.first()
    val headers = splitTableCells(rawHeader)

    var rowStartIdx = 1
    val alignments = mutableListOf<TableCellAlignment>()

    // Check if second line is a separator row
    if (tableLines.size > 1) {
        val secondLine = tableLines[1]
        val isSeparator = secondLine.replace("|", "").replace(":", "").replace("-", "").replace(" ", "").isEmpty()
        if (isSeparator) {
            rowStartIdx = 2
            val sepCells = splitTableCells(secondLine)
            sepCells.forEach { cell ->
                val c = cell.trim()
                val align = when {
                    c.startsWith(":") && c.endsWith(":") -> TableCellAlignment.CENTER
                    c.endsWith(":") -> TableCellAlignment.END
                    else -> TableCellAlignment.START
                }
                alignments.add(align)
            }
        }
    }

    // Fill default alignments if needed
    while (alignments.size < headers.size) {
        alignments.add(TableCellAlignment.START)
    }

    val rows = mutableListOf<List<String>>()
    for (i in rowStartIdx until tableLines.size) {
        val rowCells = splitTableCells(tableLines[i])
        if (rowCells.isNotEmpty()) {
            rows.add(rowCells)
        }
    }

    return Pair(
        MarkdownBlock.TableBlock(headers, alignments, rows),
        tableLines.size
    )
}

/**
 * Splits a table row into individual cells while stripping leading/trailing pipes.
 */
fun splitTableCells(rowLine: String): List<String> {
    val trimmed = rowLine.trim()
    val rawTokens = trimmed.split("|")
    val cells = mutableListOf<String>()

    for (i in rawTokens.indices) {
        val token = rawTokens[i]
        // Skip leading empty token from starting pipe
        if (i == 0 && token.isEmpty() && trimmed.startsWith("|")) continue
        // Skip trailing empty token from ending pipe
        if (i == rawTokens.size - 1 && token.isEmpty() && trimmed.endsWith("|")) continue
        cells.add(token.trim())
    }
    return cells
}

// ==========================================
// UI COMPONENT RENDERERS
// ==========================================

/**
 * Renders a rich GitHub Flavored Markdown table in a sleek horizontally scrollable card.
 */
@Composable
fun MarkdownTableCard(
    table: MarkdownBlock.TableBlock,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F1220),
        border = BorderStroke(1.dp, Color(0xFF262D4A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Table Header Bar with Copy action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181C2E))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Table (${table.rows.size} rows)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA78BFA)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val tableMarkdown = buildString {
                                append("| ").append(table.headers.joinToString(" | ")).append(" |\n")
                                append("| ").append(table.headers.joinToString(" | ") { "---" }).append(" |\n")
                                table.rows.forEach { row ->
                                    append("| ").append(row.joinToString(" | ")).append(" |\n")
                                }
                            }
                            clipboardManager.setText(AnnotatedString(tableMarkdown))
                            copied = true
                            Toast.makeText(context, "Table copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy table",
                        tint = if (copied) Color(0xFF34D399) else Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        fontSize = 11.sp,
                        color = if (copied) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF262D4A), thickness = 1.dp)

            // Horizontally Scrollable Table Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    // 1. Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1E2338), Color(0xFF232742))
                                )
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        table.headers.forEachIndexed { colIdx, headerText ->
                            val align = table.alignments.getOrElse(colIdx) { TableCellAlignment.START }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 280.dp)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = when (align) {
                                    TableCellAlignment.CENTER -> Alignment.Center
                                    TableCellAlignment.END -> Alignment.CenterEnd
                                    TableCellAlignment.START -> Alignment.CenterStart
                                }
                            ) {
                                val annotatedHeader = remember(headerText) {
                                    parseMarkdownText(headerText, Color(0xFFF1F5F9))
                                }
                                Text(
                                    text = annotatedHeader,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF8FAFC),
                                    textAlign = when (align) {
                                        TableCellAlignment.CENTER -> TextAlign.Center
                                        TableCellAlignment.END -> TextAlign.End
                                        TableCellAlignment.START -> TextAlign.Start
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF4C1D95).copy(alpha = 0.6f), thickness = 1.5.dp)

                    // 2. Data Rows
                    table.rows.forEachIndexed { rowIdx, rowCells ->
                        val isEven = rowIdx % 2 == 0
                        val rowBg = if (isEven) Color(0xFF121524) else Color(0xFF0C0E18)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            table.headers.indices.forEach { colIdx ->
                                val cellText = rowCells.getOrElse(colIdx) { "" }
                                val align = table.alignments.getOrElse(colIdx) { TableCellAlignment.START }

                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 100.dp, max = 280.dp)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = when (align) {
                                        TableCellAlignment.CENTER -> Alignment.TopCenter
                                        TableCellAlignment.END -> Alignment.TopEnd
                                        TableCellAlignment.START -> Alignment.TopStart
                                    }
                                ) {
                                    val annotatedCell = remember(cellText) {
                                        parseMarkdownText(cellText, Color(0xFFCBD5E1))
                                    }
                                    Text(
                                        text = annotatedCell,
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp,
                                        color = Color(0xFFCBD5E1),
                                        textAlign = when (align) {
                                            TableCellAlignment.CENTER -> TextAlign.Center
                                            TableCellAlignment.END -> TextAlign.End
                                            TableCellAlignment.START -> TextAlign.Start
                                        }
                                    )
                                }
                            }
                        }

                        if (rowIdx < table.rows.size - 1) {
                            HorizontalDivider(color = Color(0xFF1E2338), thickness = 0.7.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Syntax-highlighted Code Block with Copy action and language badge.
 */
@Composable
fun MarkdownCodeBlockCard(
    codeBlock: MarkdownBlock.CodeBlock,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0A0C14),
        border = BorderStroke(1.dp, Color(0xFF232A44)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141829))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = codeBlock.language.ifBlank { "code" }.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(codeBlock.code))
                            copied = true
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF34D399) else Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        fontSize = 11.sp,
                        color = if (copied) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF232A44), thickness = 0.8.dp)

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                val highlighted = remember(codeBlock.code, codeBlock.language) {
                    highlightSyntax(codeBlock.code, codeBlock.language)
                }
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Image card supporting network URLs, local files, and full-screen viewer intent.
 */
@Composable
fun MarkdownImageCard(
    imageBlock: MarkdownBlock.ImageBlock,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isError by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF131728),
        border = BorderStroke(1.dp, Color(0xFF262D4A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0E17))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(imageBlock.url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!isError) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageBlock.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = imageBlock.alt.ifBlank { "Image preview" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                        onError = { isError = true }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = imageBlock.alt.ifBlank { "Image unavailable" },
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            if (imageBlock.alt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = imageBlock.alt,
                        fontSize = 11.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open image",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(imageBlock.url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                    )
                }
            }
        }
    }
}

/**
 * Headings (H1 - H6) with proportionate styles and accents.
 */
@Composable
fun MarkdownHeadingItem(
    heading: MarkdownBlock.HeadingBlock,
    modifier: Modifier = Modifier
) {
    val (fontSize, fontWeight, color) = when (heading.level) {
        1 -> Triple(20.sp, FontWeight.ExtraBold, Color.White)
        2 -> Triple(18.sp, FontWeight.Bold, Color.White)
        3 -> Triple(16.sp, FontWeight.Bold, Color(0xFFA78BFA))
        4 -> Triple(15.sp, FontWeight.SemiBold, Color(0xFFC4B5FD))
        5 -> Triple(14.sp, FontWeight.SemiBold, Color(0xFFE2E8F0))
        else -> Triple(13.5.sp, FontWeight.Medium, Color(0xFFCBD5E1))
    }

    val annotated = remember(heading.text) { parseMarkdownText(heading.text, color) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = annotated,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            lineHeight = (fontSize.value + 6).sp
        )
        if (heading.level <= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF8B5CF6), Color.Transparent)
                        )
                    )
            )
        }
    }
}

/**
 * Renders ordered, unordered, and task lists with clean alignment and badges.
 */
@Composable
fun MarkdownListGroup(
    listBlock: MarkdownBlock.ListBlock,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listBlock.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                when (item) {
                    is MarkdownListItem.Ordered -> {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2E1065),
                            border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.5f)),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = item.numberStr.replace(Regex("[.)\\[\\]]"), "").trim(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC084FC),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val annotated = remember(item.text) { parseMarkdownText(item.text, textColor) }
                        Text(
                            text = annotated,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is MarkdownListItem.Unordered -> {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA78BFA))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val annotated = remember(item.text) { parseMarkdownText(item.text, textColor) }
                        Text(
                            text = annotated,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is MarkdownListItem.Task -> {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.isChecked) Color(0xFF065F46) else Color(0xFF1E243A),
                            border = BorderStroke(1.dp, if (item.isChecked) Color(0xFF10B981) else Color(0xFF475569)),
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(16.dp)
                        ) {
                            if (item.isChecked) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val annotated = remember(item.text) { parseMarkdownText(item.text, textColor) }
                        Text(
                            text = annotated,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = if (item.isChecked) Color(0xFF94A3B8) else textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Blockquote / Callout Card.
 */
@Composable
fun MarkdownQuoteCard(
    quote: MarkdownBlock.QuoteBlock,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
        color = Color(0xFF16192B),
        border = BorderStroke(1.dp, Color(0xFF262D4A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF8B5CF6))
            )
            Spacer(modifier = Modifier.width(10.dp))
            val annotated = remember(quote.text) { parseMarkdownText(quote.text, Color(0xFFE2E8F0)) }
            Text(
                text = annotated,
                fontSize = 13.5.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 20.sp,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
    }
}

/**
 * Gradient horizontal divider.
 */
@Composable
fun MarkdownDividerItem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF8B5CF6).copy(alpha = 0.5f),
                        Color(0xFF38BDF8).copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Standard Paragraph with clickable links and inline styling.
 */
@Composable
fun MarkdownParagraphItem(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val annotated = remember(text, textColor) { parseMarkdownText(text, textColor) }

    ClickableText(
        text = annotated,
        style = TextStyle(
            color = textColor,
            fontSize = 14.5.sp,
            lineHeight = 22.sp
        ),
        modifier = modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
        }
    )
}

// ==========================================
// INLINE PARSING & SYNTAX HIGHLIGHTING
// ==========================================

/**
 * Parses markdown inline styles like **bold**, *italic*, `inline code`, [links](url), and ~~strikethrough~~.
 */
fun parseMarkdownText(text: String, defaultColor: Color): AnnotatedString {
    val cleanText = text
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    return buildAnnotatedString {
        val lines = cleanText.split("\n")
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()

            when {
                // Header checking (###### down to #)
                trimmedLine.startsWith("###### ") -> {
                    appendStyledHeader(trimmedLine.removePrefix("###### "), 13.5f, FontWeight.Medium, Color(0xFFCBD5E1))
                }
                trimmedLine.startsWith("##### ") -> {
                    appendStyledHeader(trimmedLine.removePrefix("##### "), 14f, FontWeight.SemiBold, Color(0xFFE2E8F0))
                }
                trimmedLine.startsWith("#### ") -> {
                    appendStyledHeader(trimmedLine.removePrefix("#### "), 15f, FontWeight.SemiBold, Color(0xFFC4B5FD))
                }
                trimmedLine.startsWith("### ") -> {
                    appendStyledHeader(trimmedLine.removePrefix("### "), 16f, FontWeight.Bold, Color(0xFFA78BFA))
                }
                trimmedLine.startsWith("## ") -> {
                    appendStyledHeader(trimmedLine.removePrefix("## "), 17f, FontWeight.Bold, Color.White)
                }
                trimmedLine.startsWith("# ") -> {
                    appendStyledHeader(trimmedLine.removePrefix("# "), 18f, FontWeight.Bold, Color.White)
                }
                trimmedLine.startsWith("* ") || trimmedLine.startsWith("- ") || trimmedLine.startsWith("• ") -> {
                    val bulletContent = when {
                        trimmedLine.startsWith("* ") -> trimmedLine.removePrefix("* ")
                        trimmedLine.startsWith("- ") -> trimmedLine.removePrefix("- ")
                        else -> trimmedLine.removePrefix("• ")
                    }
                    val bulletStart = length
                    append("• ")
                    addStyle(
                        SpanStyle(color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold),
                        bulletStart,
                        length
                    )
                    appendInlineFormatted(bulletContent, defaultColor)
                }
                else -> {
                    appendInlineFormatted(line, defaultColor)
                }
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendStyledHeader(
    text: String,
    fontSizeSp: Float,
    fontWeight: FontWeight,
    color: Color
) {
    val start = length
    append(text)
    addStyle(
        SpanStyle(
            fontWeight = fontWeight,
            fontSize = fontSizeSp.sp,
            color = color
        ),
        start,
        length
    )
}

/**
 * Formats inline bold, italic, strikethrough, inline code pills, and clickable links.
 */
fun AnnotatedString.Builder.appendInlineFormatted(text: String, defaultColor: Color) {
    val inlinePattern = Regex(
        "(\\[(.*?)\\]\\((.*?)\\))|" +
        "(\\*{3}(.*?)\\*{3})|" +
        "(\\*{2}(.*?)\\*{2})|" +
        "(\\*(.*?)\\*)|" +
        "(_{2}(.*?)_{2})|" +
        "(~~(.*?)~~)|" +
        "(`([^`]+)`)"
    )

    var lastIndex = 0
    val matches = inlinePattern.findAll(text)

    for (match in matches) {
        val matchStart = match.range.first
        if (matchStart > lastIndex) {
            val plainText = text.substring(lastIndex, matchStart)
            val start = length
            append(plainText)
            addStyle(SpanStyle(color = defaultColor), start, length)
        }

        val fullMatch = match.value
        when {
            // [Link Text](url)
            fullMatch.startsWith("[") && fullMatch.contains("](") && fullMatch.endsWith(")") -> {
                val linkMatch = Regex("\\[(.*?)\\]\\((.*?)\\)").find(fullMatch)
                if (linkMatch != null) {
                    val linkText = linkMatch.groupValues[1]
                    val url = linkMatch.groupValues[2]
                    val start = length
                    append(linkText)
                    addStringAnnotation(tag = "URL", annotation = url, start = start, end = length)
                    addStyle(
                        SpanStyle(
                            color = Color(0xFF60A5FA),
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        ),
                        start,
                        length
                    )
                }
            }
            // ***Bold Italic***
            fullMatch.startsWith("***") && fullMatch.endsWith("***") && fullMatch.length >= 6 -> {
                val inner = fullMatch.substring(3, fullMatch.length - 3)
                val start = length
                append(inner)
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color.White
                    ),
                    start,
                    length
                )
            }
            // **Bold** or __Bold__
            (fullMatch.startsWith("**") && fullMatch.endsWith("**") && fullMatch.length >= 4) ||
            (fullMatch.startsWith("__") && fullMatch.endsWith("__") && fullMatch.length >= 4) -> {
                val inner = fullMatch.substring(2, fullMatch.length - 2)
                val start = length
                append(inner)
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    start,
                    length
                )
            }
            // ~~Strikethrough~~
            fullMatch.startsWith("~~") && fullMatch.endsWith("~~") && fullMatch.length >= 4 -> {
                val inner = fullMatch.substring(2, fullMatch.length - 2)
                val start = length
                append(inner)
                addStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = Color(0xFF94A3B8)
                    ),
                    start,
                    length
                )
            }
            // `inline code`
            fullMatch.startsWith("`") && fullMatch.endsWith("`") && fullMatch.length >= 2 -> {
                val codeText = fullMatch.substring(1, fullMatch.length - 1)
                val start = length
                append(" $codeText ")
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF1E243A),
                        color = Color(0xFF67E8F9),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    length
                )
            }
            // *Italic*
            fullMatch.startsWith("*") && fullMatch.endsWith("*") && fullMatch.length >= 2 -> {
                val italicText = fullMatch.substring(1, fullMatch.length - 1)
                val start = length
                append(italicText)
                addStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFE2E8F0)
                    ),
                    start,
                    length
                )
            }
            else -> {
                val start = length
                append(fullMatch)
                addStyle(SpanStyle(color = defaultColor), start, length)
            }
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
        val start = length
        append(remaining)
        addStyle(SpanStyle(color = defaultColor), start, length)
    }
}

/**
 * Basic syntax highlighter for code snippets in code blocks.
 */
fun highlightSyntax(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val keywords = setOf(
            "fun", "val", "var", "class", "interface", "object", "import", "package",
            "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
            "throw", "true", "false", "null", "def", "async", "await", "from", "as",
            "const", "let", "function", "export", "default", "SELECT", "FROM", "WHERE",
            "INSERT", "UPDATE", "DELETE", "JOIN", "TABLE", "CREATE", "DROP"
        )

        val tokenRegex = Regex(
            "(//.*|#.*)|" +                           // Comments (group 1)
            "(\"[^\"]*\"|'[^']*')|" +                  // Strings (group 2)
            "(\\b\\d+(?:\\.\\d+)?\\b)|" +              // Numbers (group 3)
            "(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)|" +        // Identifiers/Keywords (group 4)
            "([{}()\\[\\],;.:=+\\-*/<>!&|])"           // Operators & Punctuation (group 5)
        )

        var lastIndex = 0
        val matches = tokenRegex.findAll(code)

        for (match in matches) {
            val matchStart = match.range.first
            if (matchStart > lastIndex) {
                append(code.substring(lastIndex, matchStart))
            }

            val token = match.value
            val start = length
            append(token)

            when {
                match.groups[1] != null -> { // Comment
                    addStyle(SpanStyle(color = Color(0xFF64748B), fontStyle = FontStyle.Italic), start, length)
                }
                match.groups[2] != null -> { // String
                    addStyle(SpanStyle(color = Color(0xFF34D399)), start, length)
                }
                match.groups[3] != null -> { // Number
                    addStyle(SpanStyle(color = Color(0xFFF59E0B)), start, length)
                }
                match.groups[4] != null -> { // Word / Keyword
                    if (keywords.contains(token)) {
                        addStyle(SpanStyle(color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold), start, length)
                    } else {
                        addStyle(SpanStyle(color = Color(0xFF67E8F9)), start, length)
                    }
                }
                match.groups[5] != null -> { // Operator / Symbol
                    addStyle(SpanStyle(color = Color(0xFF94A3B8)), start, length)
                }
                else -> {
                    addStyle(SpanStyle(color = Color(0xFFE2E8F0)), start, length)
                }
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < code.length) {
            append(code.substring(lastIndex))
        }
    }
}
