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

        val isPdf = name.endsWith(".pdf", ignoreCase = true) || mimeType.contains("pdf", ignoreCase = true)

        if (isPdf) {
            readPdf(context, uri, name, sizeBytes)
        } else {
            readTextDocument(context, uri, name, sizeBytes)
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

                // For vision/multimodal AI, encode the first page preview to base64
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

        // Extract complete text content from PDF streams and format with HybridDocumentPipeline
        var extractedPdfText = ""
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractedPdfText = PdfTextExtractor.extractText(stream)
            }
        } catch (_: Exception) {}

        val finalContent = if (extractedPdfText.isNotBlank()) {
            com.orbital.media.parser.HybridDocumentPipeline().processRawText(name, extractedPdfText).fullMarkdown
        } else {
            "📄 PDF Document: $name\n• Pages: $pageCount\n• Size: ${formatSize(sizeBytes)}\n• Text could not be extracted directly from binary stream."
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

    private fun readTextDocument(context: Context, uri: Uri, name: String, sizeBytes: Long): AttachedMedia {
        var inputStream: InputStream? = null
        val text = try {
            inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        } catch (_: Exception) {
            ""
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }

        val parsedMarkdown = if (text.isNotBlank()) {
            com.orbital.media.parser.HybridDocumentPipeline().processRawText(name, text).fullMarkdown
        } else ""

        val truncatedText = if (parsedMarkdown.length > 25000) {
            parsedMarkdown.take(25000) + "\n\n... [Truncated: Document contains ${parsedMarkdown.length} characters]"
        } else parsedMarkdown

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
