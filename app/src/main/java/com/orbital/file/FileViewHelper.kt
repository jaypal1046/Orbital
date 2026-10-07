package com.orbital.file

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileViewHelper {

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "docx", "doc" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pptx", "ppt" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xlsx", "xls" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            "txt", "log", "md", "properties", "gradle", "kts", "kt", "java", "py", "sh" -> "text/plain"
            "json" -> "application/json"
            "xml", "html", "htm" -> "text/html"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
    }

    fun openFile(context: Context, file: File): Boolean {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareFile(context: Context, file: File): Boolean {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            val mimeType = getMimeType(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Resolves a file reference by checking absolute paths or relative paths in external/internal app storage.
     */
    fun findExistingFile(context: Context, text: String): File? {
        if (text.isBlank()) return null

        val directPatterns = listOf(
            Regex("""(?:Path|File|Saved to|Created|Written to|Updated|Location):\s*([^\s\n\r"']+)""", RegexOption.IGNORE_CASE),
            Regex("""file://([^\s\n\r"']+)""", RegexOption.IGNORE_CASE),
            Regex("""(/[a-zA-Z0-9_\-\.\/]+?\.[a-zA-Z0-9]{2,6})"""),
            Regex("""\b([a-zA-Z0-9_\-]+\.[a-zA-Z0-9]{2,6})\b""")
        )

        for (pattern in directPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val candidate = match.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
                if (candidate.isBlank()) continue

                // 1. Try as absolute path
                val file = File(candidate)
                if (file.exists() && file.isFile) return file

                // 2. Try in external files directory
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null) {
                    val extFile = File(extDir, candidate)
                    if (extFile.exists() && extFile.isFile) return extFile

                    val baseNameExt = File(extDir, file.name)
                    if (baseNameExt.exists() && baseNameExt.isFile) return baseNameExt
                }

                // 3. Try in internal files directory
                val intFile = File(context.filesDir, candidate)
                if (intFile.exists() && intFile.isFile) return intFile

                val baseNameInt = File(context.filesDir, file.name)
                if (baseNameInt.exists() && baseNameInt.isFile) return baseNameInt
            }
        }
        return null
    }
}
