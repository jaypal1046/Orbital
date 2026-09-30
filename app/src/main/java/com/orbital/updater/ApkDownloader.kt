package com.orbital.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ApkDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    suspend fun downloadApk(url: String, fileName: String = "orbital_update.apk"): File? = withContext(Dispatchers.IO) {
        _downloadProgress.value = DownloadProgress.Downloading(0L, 0L, 0)
        try {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destinationFile = File(updateDir, fileName)
            if (destinationFile.exists()) destinationFile.delete()

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadProgress.value = DownloadProgress.Failed("HTTP ${response.code}: Failed to download APK")
                    return@withContext null
                }

                val body = response.body ?: run {
                    _downloadProgress.value = DownloadProgress.Failed("Empty response body from server")
                    return@withContext null
                }

                val totalBytes = body.contentLength()
                var bytesRead = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
                            _downloadProgress.value = DownloadProgress.Downloading(bytesRead, totalBytes, percent)
                        }
                        output.flush()
                    }
                }

                _downloadProgress.value = DownloadProgress.Completed(destinationFile.absolutePath)
                destinationFile
            }
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress.Failed(e.message ?: "Unknown download error")
            null
        }
    }

    fun reset() {
        _downloadProgress.value = DownloadProgress.Idle
    }
}
