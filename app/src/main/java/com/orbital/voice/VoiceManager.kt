package com.orbital.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.orbital.overlay.OverlayService
import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages voice capabilities including ultra-fast Whisper STT and Text-To-Speech
 */
open class VoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private val whisperTranscriber = WhisperTranscriber(context)
    private val voiceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isListening = false
    private var isSpeaking = false
    private var isUsingWhisper = false
    private var voiceCallback: VoiceCallback? = null

    interface VoiceCallback {
        fun onSpeechRecognized(text: String)
        fun onSpeechError(error: String)
        fun onSpeechStart()
        fun onSpeechEnd()
        fun onSpeechPartialResult(text: String)
        fun onTtsStart()
        fun onTtsEnd()
    }

    init {
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isUsingWhisper) {
                        isListening = true
                        voiceCallback?.onSpeechStart()
                        updateOverlayStatus("listening")
                    }
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isUsingWhisper) {
                        isListening = false
                        voiceCallback?.onSpeechEnd()
                        updateOverlayStatus("idle")
                    }
                }

                override fun onError(error: Int) {
                    if (!isUsingWhisper) {
                        isListening = false
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            else -> "Unknown error"
                        }
                        voiceCallback?.onSpeechError(errorMessage)
                        updateOverlayStatus("error")
                        Log.e(TAG, "Speech recognition error: $errorMessage")
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!isUsingWhisper) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            voiceCallback?.onSpeechRecognized(matches[0])
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isUsingWhisper) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            voiceCallback?.onSpeechPartialResult(matches[0])
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Log.e(TAG, "Speech recognition not available")
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        voiceCallback?.onTtsEnd()
                        updateOverlayStatus("idle")
                    }
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        voiceCallback?.onTtsEnd()
                        updateOverlayStatus("idle")
                    }
                })
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    open fun setVoiceCallback(callback: VoiceCallback) {
        this.voiceCallback = callback
    }

    open fun startListening() {
        if (isListening) return

        // 1. Try Whisper recording first if an API key (Groq or OpenAI) is configured
        if (whisperTranscriber.isConfigured() && whisperTranscriber.startRecording()) {
            isUsingWhisper = true
            isListening = true
            voiceCallback?.onSpeechStart()
            updateOverlayStatus("listening")
            Log.d(TAG, "Started Whisper voice recording")
        } else if (speechRecognizer != null) {
            // 2. Seamless fallback to Android on-device/system SpeechRecognizer
            isUsingWhisper = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                speechRecognizer?.startListening(intent)
                Log.d(TAG, "Started system SpeechRecognizer fallback")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start system SpeechRecognizer", e)
                voiceCallback?.onSpeechError("Voice input unavailable. Please add a Groq API key in Settings for Whisper STT.")
            }
        } else {
            voiceCallback?.onSpeechError("Voice recognition unavailable. Please add a free Groq API key in Settings.")
        }
    }

    open fun stopListening() {
        if (!isListening) return
        isListening = false
        voiceCallback?.onSpeechEnd()
        updateOverlayStatus("idle")

        if (isUsingWhisper) {
            voiceScope.launch {
                val result = whisperTranscriber.stopRecordingAndTranscribe()
                if (result.isSuccess) {
                    val text = result.getOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        voiceCallback?.onSpeechRecognized(text)
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Whisper transcription failed"
                    Log.w(TAG, "Whisper STT error: $errorMsg")
                    voiceCallback?.onSpeechError(errorMsg)
                }
            }
        } else {
            speechRecognizer?.stopListening()
        }
    }

    open fun isListening(): Boolean = isListening

    open fun isUsingWhisper(): Boolean = isUsingWhisper

    open fun isWhisperConfigured(): Boolean = whisperTranscriber.isConfigured()

    open fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (textToSpeech != null && !isSpeaking) {
            isSpeaking = true
            voiceCallback?.onTtsStart()
            updateOverlayStatus("speaking")

            textToSpeech?.speak(text, queueMode, null, text)
        }
    }

    open fun shutdown() {
        whisperTranscriber.cancelRecording()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        isListening = false
        isSpeaking = false
    }

    private fun updateOverlayStatus(status: String) {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_VOICE_STATUS
            putExtra("status", status)
        }
        context.startService(intent)
    }
}