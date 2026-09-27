package com.orbital.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormattedMarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Split content by code blocks (``` ... ```)
    val parts = remember(content) { parseMarkdownBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parts.forEach { part ->
            when (part) {
                is MarkdownBlock.CodeBlock -> {
                    // Code block card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0E17),
                        border = BorderStroke(1.dp, Color(0xFF232A44)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Header with language and copy button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161A2B))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = part.language.ifBlank { "code" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFA78BFA)
                                )
                                Row(
                                    modifier = Modifier.clickable {
                                        clipboardManager.setText(AnnotatedString(part.code))
                                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Copy code",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Code content
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = part.code,
                                    color = Color(0xFF67E8F9),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.TextBlock -> {
                    val annotated = remember(part.text) { parseMarkdownText(part.text, textColor) }
                    Text(
                        text = annotated,
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeFenceRegex = Regex("```([a-zA-Z0-9_-]*)\\n([\\s\\S]*?)```")

    var lastIndex = 0
    val matches = codeFenceRegex.findAll(raw)

    for (match in matches) {
        val range = match.range
        if (range.first > lastIndex) {
            val text = raw.substring(lastIndex, range.first).trim()
            if (text.isNotEmpty()) {
                blocks.add(MarkdownBlock.TextBlock(text))
            }
        }
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(language, code))
        lastIndex = range.last + 1
    }

    if (lastIndex < raw.length) {
        val text = raw.substring(lastIndex).trim()
        if (text.isNotEmpty()) {
            blocks.add(MarkdownBlock.TextBlock(text))
        }
    }

    if (blocks.isEmpty() && raw.isNotEmpty()) {
        blocks.add(MarkdownBlock.TextBlock(raw))
    }

    return blocks
}

/**
 * Parses markdown inline styles like **bold**, *italic*, `inline code`, headers, and bullet points.
 */
fun parseMarkdownText(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            var trimmedLine = line.trim()

            // Header checking (###, ##, #)
            when {
                trimmedLine.startsWith("### ") -> {
                    val headerText = trimmedLine.removePrefix("### ")
                    val start = length
                    append(headerText)
                    addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFA78BFA)
                        ),
                        start,
                        length
                    )
                }
                trimmedLine.startsWith("## ") -> {
                    val headerText = trimmedLine.removePrefix("## ")
                    val start = length
                    append(headerText)
                    addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        ),
                        start,
                        length
                    )
                }
                trimmedLine.startsWith("# ") -> {
                    val headerText = trimmedLine.removePrefix("# ")
                    val start = length
                    append(headerText)
                    addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        ),
                        start,
                        length
                    )
                }
                trimmedLine.startsWith("* ") || trimmedLine.startsWith("- ") || trimmedLine.startsWith("• ") -> {
                    // Bullet point
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

private fun AnnotatedString.Builder.appendInlineFormatted(line: String, defaultColor: Color) {
    // Regex matching **bold**, *italic*, and `code`
    val inlinePattern = Regex("(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)")
    var lastIndex = 0
    val matches = inlinePattern.findAll(line)

    for (match in matches) {
        val matchStart = match.range.first
        if (matchStart > lastIndex) {
            val plainText = line.substring(lastIndex, matchStart)
            val start = length
            append(plainText)
            addStyle(SpanStyle(color = defaultColor), start, length)
        }

        val fullMatch = match.value
        when {
            // **bold**
            fullMatch.startsWith("**") && fullMatch.endsWith("**") && fullMatch.length >= 4 -> {
                val boldText = fullMatch.substring(2, fullMatch.length - 2)
                val start = length
                append(boldText)
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    start,
                    length
                )
            }
            // `code`
            fullMatch.startsWith("`") && fullMatch.endsWith("`") && fullMatch.length >= 2 -> {
                val codeText = fullMatch.substring(1, fullMatch.length - 1)
                val start = length
                append(codeText)
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF1E243A),
                        color = Color(0xFF67E8F9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    length
                )
            }
            // *italic*
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

    if (lastIndex < line.length) {
        val remaining = line.substring(lastIndex)
        val start = length
        append(remaining)
        addStyle(SpanStyle(color = defaultColor), start, length)
    }
}
