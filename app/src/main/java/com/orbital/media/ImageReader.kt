package com.orbital.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import kotlin.coroutines.resume
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageReader {

    suspend fun readUri(context: Context, uri: Uri): AttachedMedia = withContext(Dispatchers.IO) {
        val name = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
        val sizeBytes = getFileSize(context, uri)

        val bitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = 1024
                    val width = info.size.width
                    val height = info.size.height
                    if (width > maxDim || height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(width, height)
                        decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
                    }
                }
            } else {
                var stream: InputStream? = null
                try {
                    stream = context.contentResolver.openInputStream(uri)
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                    stream?.close()

                    val maxDim = 1024
                    var sampleSize = 1
                    while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                        sampleSize *= 2
                    }

                    stream = context.contentResolver.openInputStream(uri)
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } finally {
                    try { stream?.close() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            null
        }

        val base64 = bitmap?.let { bmp ->
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        // 1. On-Device Text & Table Extraction (Phone-Side OCR)
        val extractedText = if (bitmap != null) {
            extractTextOnDevice(bitmap)
        } else ""

        // 2. On-Device Financial Analysis (PAT, EBITDA, Sales, Balance Sheet)
        val financialReport = if (extractedText.isNotBlank()) {
            com.orbital.finance.FinancialDataEngine.analyzeText(extractedText)
        } else null

        val finalContent = buildString {
            if (financialReport != null && financialReport.isFinancialDocument) {
                append(financialReport.summaryMarkdown)
                append("\n\n---\n#### 📄 Extracted On-Device OCR Text:\n")
                append(extractedText)
            } else if (extractedText.isNotBlank()) {
                append("🖼️ **Image:** $name (Extracted On-Device via Vision OCR):\n\n")
                append(extractedText)
            } else {
                append("🖼️ Image: $name attached for visual analysis.")
            }
        }

        AttachedMedia(
            name = name,
            type = AttachmentType.IMAGE,
            uri = uri,
            sizeBytes = sizeBytes,
            textContent = finalContent,
            base64Image = base64,
            previewBitmap = bitmap
        )
    }

    suspend fun extractTextOnDevice(bitmap: Bitmap): String = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume("")
                }
        } catch (_: Throwable) {
            if (cont.isActive) cont.resume("")
        }
    }

    fun fromBitmap(bitmap: Bitmap, name: String = "screen_capture.jpg"): AttachedMedia {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        return AttachedMedia(
            name = name,
            type = AttachmentType.SCREEN,
            sizeBytes = bytes.size.toLong(),
            textContent = "📱 Live Screen Capture attached for analysis.",
            base64Image = base64,
            previewBitmap = bitmap
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
}
