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

/**
 * Manages voice capabilities including speech-to-text and text-to-speech
 */
class VoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isSpeaking = false
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
                    isListening = true
                    voiceCallback?.onSpeechStart()
                    updateOverlayStatus("listening")
                }

                override fun onBeginningOfSpeech() {
                    // No action needed
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // No action needed
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // No action needed
                }

                override fun onEndOfSpeech() {
                    isListening = false
                    voiceCallback?.onSpeechEnd()
                    updateOverlayStatus("idle")
                }

                override fun onError(error: Int) {
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

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        voiceCallback?.onSpeechRecognized(matches[0])
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        voiceCallback?.onSpeechPartialResult(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // No action needed
                }
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

    fun setVoiceCallback(callback: VoiceCallback) {
        this.voiceCallback = callback
    }

    fun startListening() {
        if (speechRecognizer != null && !isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            voiceCallback?.onSpeechEnd()
            updateOverlayStatus("idle")
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (textToSpeech != null && !isSpeaking) {
            isSpeaking = true
            voiceCallback?.onTtsStart()
            updateOverlayStatus("speaking")

            textToSpeech?.speak(text, queueMode, null, text)
        }
    }

    fun shutdown() {
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