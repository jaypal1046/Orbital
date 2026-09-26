package com.orbital.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.orbital.data.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Ultra-fast Whisper speech-to-text transcriber with Groq & OpenAI APIs,
 * delivering sub-second voice transcription for AI commands.
 */
class WhisperTranscriber(
    private val context: Context,
    private val secureStorage: SecureStorage = SecureStorage(context)
) {
    companion object {
        private const val TAG = "WhisperTranscriber"
        private val MEDIA_TYPE_M4A = "audio/m4a".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording = false

    fun startRecording(): Boolean {
        return try {
            val audioFile = File(context.cacheDir, "whisper_voice_${System.currentTimeMillis()}.m4a")
            currentAudioFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setAudioChannels(1) // Mono for optimal Whisper STT
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            Log.d(TAG, "Whisper recording started: ${audioFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Whisper audio recording", e)
            isRecording = false
            false
        }
    }

    fun isConfigured(): Boolean {
        val groqKey = secureStorage.getProviderApiKey("GROQ")
        val openAiKey = secureStorage.getProviderApiKey("OPENAI")
        val genericKey = secureStorage.getApiKey()
        return !groqKey.isNullOrBlank() || !openAiKey.isNullOrBlank() ||
                (genericKey?.startsWith("gsk_") == true) || (genericKey?.startsWith("sk-") == true)
    }

    suspend fun stopRecordingAndTranscribe(): Result<String> = withContext(Dispatchers.IO) {
        if (!isRecording) {
            return@withContext Result.failure(IllegalStateException("Not recording"))
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media recorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }

        val audioFile = currentAudioFile
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IOException("Audio file is empty or missing"))
        }

        val genericKey = secureStorage.getApiKey()

        // 1. Try Groq Whisper (Blistering speed <200ms with whisper-large-v3-turbo)
        var groqKey = secureStorage.getProviderApiKey("GROQ")
        if (groqKey.isNullOrBlank() && genericKey?.startsWith("gsk_") == true) {
            groqKey = genericKey
        }
        if (!groqKey.isNullOrBlank()) {
            val groqResult = transcribeWithGroq(audioFile, groqKey)
            if (groqResult.isSuccess) {
                audioFile.delete()
                return@withContext groqResult
            }
            Log.w(TAG, "Groq Whisper failed, trying fallback: ${groqResult.exceptionOrNull()?.message}")
        }

        // 2. Fallback to OpenAI Whisper (whisper-1)
        var openAiKey = secureStorage.getProviderApiKey("OPENAI")
        if (openAiKey.isNullOrBlank() && genericKey?.startsWith("sk-") == true) {
            openAiKey = genericKey
        }
        if (!openAiKey.isNullOrBlank()) {
            val openAiResult = transcribeWithOpenAI(audioFile, openAiKey)
            if (openAiResult.isSuccess) {
                audioFile.delete()
                return@withContext openAiResult
            }
            Log.w(TAG, "OpenAI Whisper failed: ${openAiResult.exceptionOrNull()?.message}")
        }

        audioFile.delete()
        Result.failure(IOException("No active Groq or OpenAI key configured for Whisper STT. Add a Groq API key in Settings."))
    }

    private fun transcribeWithGroq(file: File, apiKey: String): Result<String> {
        return try {
            val fileBody = file.asRequestBody(MEDIA_TYPE_M4A)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0.0")
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Groq Whisper API HTTP ${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val text = json.optString("text", "").trim()
                if (text.isNotBlank()) {
                    Log.d(TAG, "Groq Whisper STT successful: $text")
                    Result.success(text)
                } else {
                    Result.failure(IOException("Empty transcription returned by Groq Whisper"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq Whisper transcription failed", e)
            Result.failure(e)
        }
    }

    private fun transcribeWithOpenAI(file: File, apiKey: String): Result<String> {
        return try {
            val fileBody = file.asRequestBody(MEDIA_TYPE_M4A)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return Result.failure(IOException("OpenAI Whisper API HTTP ${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val text = json.optString("text", "").trim()
                if (text.isNotBlank()) {
                    Log.d(TAG, "OpenAI Whisper STT successful: $text")
                    Result.success(text)
                } else {
                    Result.failure(IOException("Empty transcription returned by OpenAI Whisper"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI Whisper transcription failed", e)
            Result.failure(e)
        }
    }

    fun isRecording(): Boolean = isRecording

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        currentAudioFile?.delete()
    }
}
