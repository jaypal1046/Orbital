package com.orbital.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object DocumentReader {

    suspend fun readUri(context: Context, uri: Uri): AttachedMedia = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val name = getFileName(context, uri) ?: "document"
        val mimeType = contentResolver.getType(uri) ?: ""
        val sizeBytes = getFileSize(context, uri)

        val lowerName = name.lowercase()
        val isPdf = lowerName.endsWith(".pdf") || mimeType.contains("pdf", ignoreCase = true)
        val isSpreadsheet = lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv") || lowerName.endsWith(".tsv")
        val isWord = lowerName.endsWith(".docx") || lowerName.endsWith(".doc")
        val isPpt = lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt")

        when {
            isPdf -> readPdf(context, uri, name, sizeBytes)
            isSpreadsheet -> readSpreadsheet(context, uri, name, sizeBytes)
            isWord -> readWordDocument(context, uri, name, sizeBytes)
            isPpt -> readPptDocument(context, uri, name, sizeBytes)
            else -> readTextDocument(context, uri, name, sizeBytes)
        }
    }

    private fun readPdf(context: Context, uri: Uri, name: String, sizeBytes: Long): AttachedMedia {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var pageCount = 1
        var previewBitmap: Bitmap? = null
        var base64Img: String? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                if (pageCount > 0) {
                    val page = renderer.openPage(0)
                    val width = 512
                    val height = (512 * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(200)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    previewBitmap = bitmap
                }

                base64Img = previewBitmap?.let { bmp ->
                    val stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                }
            }
        } catch (_: Exception) {} finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }

        var extractedPdfText = ""
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractedPdfText = PdfTextExtractor.extractText(stream)
            }
        } catch (_: Exception) {}

        val parsedContent = if (extractedPdfText.isNotBlank()) {
            com.orbital.media.parser.HybridDocumentPipeline().processRawText(name, extractedPdfText).fullMarkdown
        } else {
            "📄 PDF Document: $name\n• Pages: $pageCount\n• Size: ${formatSize(sizeBytes)}\n• Text could not be extracted directly from binary stream."
        }

        val finReport = if (extractedPdfText.isNotBlank()) {
            com.orbital.finance.FinancialDataEngine.analyzeText(extractedPdfText)
        } else null

        val finalContent = if (finReport != null && finReport.isFinancialDocument) {
            "${finReport.summaryMarkdown}\n\n---\n#### 📄 Document Content:\n$parsedContent"
        } else {
            parsedContent
        }

        return AttachedMedia(
            name = name,
            type = AttachmentType.PDF,
            uri = uri,
            sizeBytes = sizeBytes,
            textContent = finalContent,
            base64Image = base64Img,
            previewBitmap = previewBitmap,
            pageCount = pageCount
        )
    }

    private fun readSpreadsheet(context: Context, uri: Uri, name: String, sizeBytes: Long): AttachedMedia {
        val lowerName = name.lowercase()
        val tempFile = java.io.File(context.cacheDir, "temp_sheet_${System.currentTimeMillis()}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")
        var extractedTableMarkdown = ""

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                val grid = com.orbital.file.UniversalFileEngine.extractSpreadsheetGrid(tempFile)
                if (grid.headers.isNotEmpty() || grid.rows.isNotEmpty()) {
                    extractedTableMarkdown = buildString {
                        append("### 📊 Spreadsheet: $name\n\n")
                        if (grid.headers.isNotEmpty()) {
                            append("| " + grid.headers.joinToString(" | ") + " |\n")
                            append("|" + grid.headers.joinToString("|") { "---" } + "|\n")
                        }
                        val sampleRows = grid.rows.take(200)
                        sampleRows.forEach { row ->
                            append("| " + row.joinToString(" | ") + " |\n")
                        }
                        if (grid.rows.size > 200) {
                            append("\n*... [Showing 200 of ${grid.rows.size} rows]*\n")
                        }
                    }
                } else {
                    // Fallback to text reader in UniversalFileEngine
                    val result = com.orbital.file.UniversalFileEngine.readXlsx(tempFile)
                    if (result is com.orbital.file.FileOperationResult.Success) {
                        extractedTableMarkdown = result.content ?: ""
                    }
                }
            }
        } catch (_: Exception) {} finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        if (extractedTableMarkdown.isBlank()) {
            extractedTableMarkdown = "📊 Spreadsheet: $name (${formatSize(sizeBytes)}) attached. (No readable cells found)."
        }

        val finReport = com.orbital.finance.FinancialDataEngine.analyzeText(extractedTableMarkdown)
        val finalContent = if (finReport.isFinancialDocument) {
            "${finReport.summaryMarkdown}\n\n---\n#### 📊 Extracted Sheet Content:\n$extractedTableMarkdown"
        } else {
            extractedTableMarkdown
        }

        return AttachedMedia(
            name = name,
            type = AttachmentType.DOCUMENT,
            uri = uri,
            sizeBytes = sizeBytes,
            textContent = finalContent
        )
    }

    private fun readWordDocument(context: Context, uri: Uri, name: String, sizeBytes: Long): AttachedMedia {
        val tempFile = java.io.File(context.cacheDir, "temp_doc_${System.currentTimeMillis()}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")
        var docMarkdown = ""

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                val paragraphs = com.orbital.file.UniversalFileEngine.extractDocxParagraphs(tempFile)
                if (paragraphs.isNotEmpty()) {
                    docMarkdown = paragraphs.joinToString("\n\n") { p ->
                        if (p.isHeading) "### ${p.text}" else if (p.isBullet) "• ${p.text}" else p.text
                    }
                } else {
                    val result = com.orbital.file.UniversalFileEngine.readDocxText(tempFile)
                    if (result is com.orbital.file.FileOperationResult.Success) {
                        docMarkdown = result.content ?: ""
                    }
                }
            }
        } catch (_: Exception) {} finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        if (docMarkdown.isBlank()) {
            docMarkdown = "📝 Word Document: $name (${formatSize(sizeBytes)}) attached."
        }

        return AttachedMedia(
            name = name,
            type = AttachmentType.DOCUMENT,
            uri = uri,
            sizeBytes = sizeBytes,
            textContent = docMarkdown
        )
    }

    private fun readPptDocument(context: Context, uri: Uri, name: String, sizeBytes: Long): AttachedMedia {
        val tempFile = java.io.File(context.cacheDir, "temp_ppt_${System.currentTimeMillis()}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")
        var pptMarkdown = ""

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                val result = com.orbital.file.UniversalFileEngine.readPptxText(tempFile)
                if (result is com.orbital.file.FileOperationResult.Success) {
                    pptMarkdown = result.content ?: ""
                }
            }
        } catch (_: Exception) {} finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        return AttachedMedia(
            name = name,
            type = AttachmentType.DOCUMENT,
            uri = uri,
            sizeBytes = sizeBytes,
            textContent = pptMarkdown.ifBlank { "📊 Presentation: $name (${formatSize(sizeBytes)}) attached." }
        )
    }

    private fun readTextDocument(context: Context, uri: Uri, name: String, sizeBytes: Long): AttachedMedia {
        var inputStream: InputStream? = null
        val rawBytes = try {
            inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.readBytes() ?: ByteArray(0)
        } catch (_: Exception) {
            ByteArray(0)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }

        // Binary check: If file contains null bytes or zip signature PK\x03\x04 and wasn't parsed as office doc
        val isBinary = rawBytes.take(512).any { it == 0.toByte() } ||
                (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte() && rawBytes[2] == 0x03.toByte() && rawBytes[3] == 0x04.toByte())

        if (isBinary) {
            return AttachedMedia(
                name = name,
                type = AttachmentType.DOCUMENT,
                uri = uri,
                sizeBytes = sizeBytes,
                textContent = "⚠️ Binary file format: '$name' (${formatSize(sizeBytes)}) attached. Unable to extract as plain text. Please upload as PDF, Excel (.xlsx, .csv), Word (.docx), or plain text."
            )
        }

        // Sanitize control characters (preserve tabs and newlines)
        val text = rawBytes.toString(Charsets.UTF_8).replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

        val parsedMarkdown = if (text.isNotBlank()) {
            com.orbital.media.parser.HybridDocumentPipeline().processRawText(name, text).fullMarkdown
        } else ""

        val finReport = if (text.isNotBlank()) {
            com.orbital.finance.FinancialDataEngine.analyzeText(text)
        } else null

        val withFinancialReport = if (finReport != null && finReport.isFinancialDocument) {
            "${finReport.summaryMarkdown}\n\n---\n#### 📝 Document Content:\n$parsedMarkdown"
        } else {
            parsedMarkdown
        }

        val truncatedText = if (withFinancialReport.length > 30000) {
            withFinancialReport.take(30000) + "\n\n... [Truncated: Document contains ${withFinancialReport.length} characters]"
        } else withFinancialReport

        return AttachedMedia(
            name = name,
            type = AttachmentType.DOCUMENT,
            uri = uri,
            sizeBytes = sizeBytes,
            textContent = truncatedText.ifBlank { "📝 Document: $name (${formatSize(sizeBytes)}) attached." }
        )
    }


    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1) return cursor.getLong(idx)
                }
            }
        }
        return 0L
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes > 0 -> "$bytes B"
        else -> ""
    }
}
