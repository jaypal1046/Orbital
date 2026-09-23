package com.orbital.data

import android.util.Log
import com.orbital.overlay.ConnectionStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class LlmRepository(private val okHttpClient: OkHttpClient = OkHttpClient()) {

    companion object {
        private const val TAG = "LlmRepository"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }

    private var connectionStatusCallback: ((ConnectionStatus) -> Unit)? = null

    /**
     * Sets a callback for connection status updates
     * @param callback The callback function to receive status updates
     */
    fun setConnectionStatusCallback(callback: (ConnectionStatus) -> Unit) {
        this.connectionStatusCallback = callback
    }

    /**
     * Sends a streaming request to the LLM API
     * @param apiEndpoint The API endpoint URL
     * @param apiKey The API key for authentication
     * @param messages The conversation messages
     * @param onChunk Callback for each response chunk
     * @param onError Callback for errors
     */
    fun streamCompletion(
        apiEndpoint: String,
        apiKey: String,
        messages: List<Map<String, Any>>,
        onChunk: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        // Notify connecting status
        connectionStatusCallback?.invoke(ConnectionStatus.CONNECTING)

        val isGemini = apiEndpoint.contains("generativelanguage.googleapis.com")

        val json = if (isGemini) {
            // Gemini API uses a different format
            JSONObject().apply {
                put("contents", messages.map { msg ->
                    JSONObject().apply {
                        put("role", msg["role"] as? String ?: "user")
                        put("parts", JSONArray(
                            (msg["parts"] as? List<Map<String, String>>)?.map { part ->
                                JSONObject().apply { put("text", part["text"] ?: "") }
                            } ?: listOf(JSONObject().apply { put("text", msg["content"] as? String ?: "") })
                        ))
                    }
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 2048)
                })
                put("stream", true)
            }
        } else {
            // Standard OpenAI-compatible format
            JSONObject().apply {
                put("model", when (apiEndpoint) {
                    "https://api.openai.com/v1/chat/completions" -> "gpt-4o-mini"
                    "https://api.mistral.ai/v1/chat/completions" -> "mistral-small-latest"
                    "https://api.groq.com/openai/v1/chat/completions" -> "llama-3.3-70b-versatile"
                    "https://openrouter.ai/api/v1/chat/completions" -> "meta-llama/llama-3.3-70b-instruct"
                    "https://api.together.xyz/v1/chat/completions" -> "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"
                    "https://api.fireworks.ai/inference/v1/chat/completions" -> "accounts/fireworks/models/llama-v3p1-8b-instruct"
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent" -> "gemini-pro"
                    else -> "gpt-4o-mini"
                })
                put("messages", messagesToJsonArray(messages))
                put("stream", true)
                put("temperature", 0.7)
            }
        }

        val request = Request.Builder()
            .url(apiEndpoint)
            .post(RequestBody.create(MEDIA_TYPE_JSON, json.toString()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Stream failed", e)
                connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                        throw IOException("Unexpected code $response")
                    }

                    // Notify connected status
                    connectionStatusCallback?.invoke(ConnectionStatus.CONNECTED)

                    response.body?.let { body ->
                        val reader = body.charStream()
                        val buffer = CharArray(8192)
                        var lineBuilder = StringBuilder()

                        while (true) {
                            val charsRead = reader.read(buffer) ?: -1
                            if (charsRead == -1) break
                            lineBuilder.append(buffer, 0, charsRead)

                            // Process Server-Sent Events format
                            val lines = lineBuilder.toString().split("\n")
                            lineBuilder.setLength(0)

                            for (line in lines) {
                                if (line.startsWith("data: ")) {
                                    val data = line.substring(6)
                                    if (data == "[DONE]") {
                                        return@onResponse
                                    }
                                    try {
                                        val jsonResponse = JSONObject(data)
                                        if (jsonResponse.has("candidates")) {
                                            // Handle Gemini streaming format
                                            val candidates = jsonResponse.getJSONArray("candidates")
                                            if (candidates.length() > 0) {
                                                val firstCandidate = candidates.getJSONObject(0)
                                                val content = firstCandidate.optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text") ?: ""
                                                if (content.isNotBlank()) {
                                                    onChunk(content)
                                                }
                                            }
                                        } else if (jsonResponse.has("choices")) {
                                            // Handle standard OpenAI format
                                            val choices = jsonResponse.getJSONArray("choices")
                                            if (choices.length() > 0) {
                                                val firstChoice = choices.getJSONObject(0)
                                                val deltaObj = firstChoice.optJSONObject("delta")
                                                val content = deltaObj?.optString("content") ?: ""
                                                if (content.isNotBlank()) {
                                                    onChunk(content)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to parse chunk: $data", e)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error", e)
                    connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                    onError(e)
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun messagesToJsonArray(messages: List<Map<String, Any>>): JSONArray {
        return JSONArray(messages.map { msg ->
            JSONObject().apply {
                put("role", msg["role"] as? String ?: "user")
                if (msg.containsKey("content")) {
                    put("content", msg["content"] as String)
                } else if (msg.containsKey("parts")) {
                    // This case shouldn't be reached for standard format, but handle gracefully
                    put("content", (msg["parts"] as? List<Map<String, String>>)?.firstOrNull()?.get("text") ?: "")
                }
            }
        })
    }
}
