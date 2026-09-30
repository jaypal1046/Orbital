package com.orbital.media

import android.graphics.Bitmap
import android.net.Uri

enum class AttachmentType(val displayName: String, val icon: String) {
    PDF("PDF Document", "📄"),
    DOCUMENT("Text / Document", "📝"),
    IMAGE("Image / Photo", "🖼️"),
    SCREEN("Screen Capture", "📱")
}

data class AttachedMedia(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: AttachmentType,
    val uri: Uri? = null,
    val sizeBytes: Long = 0L,
    val textContent: String? = null,
    val base64Image: String? = null,
    val previewBitmap: Bitmap? = null,
    val pageCount: Int = 1
) {
    val formattedSize: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
            sizeBytes > 0 -> "$sizeBytes B"
            else -> ""
        }
}
