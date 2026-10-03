package com.orbital.data

import android.util.Log
import com.orbital.overlay.ConnectionStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Encapsulates OkHttp SSE streaming, payload construction for Gemini & OpenAI-compatible
 * endpoints, SSE chunk parsing, and self-healing error recovery.
 */
class StreamingClient(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "StreamingClient"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }

    private var connectionStatusCallback: ((ConnectionStatus) -> Unit)? = null

    fun setConnectionStatusCallback(callback: ((ConnectionStatus) -> Unit)?) {
        this.connectionStatusCallback = callback
    }

    /**
     * Executes an SSE streaming request to the given endpoint.
     */
    fun streamCompletion(
        apiEndpoint: String,
        apiKey: String,
        activeModel: String,
        messages: List<Map<String, Any>>,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit,
        onGeminiModelMigration: ((newModel: String, newEndpoint: String) -> Unit)? = null
    ) {
        connectionStatusCallback?.invoke(ConnectionStatus.CONNECTING)

        val isGemini = apiEndpoint.contains("generativelanguage.googleapis.com")
        val jsonPayload = if (isGemini) {
            buildGeminiPayload(messages)
        } else {
            buildOpenAiPayload(activeModel, messages)
        }

        val requestBuilder = Request.Builder()
        if (isGemini) {
            val urlWithKey = if (apiEndpoint.contains("key=")) apiEndpoint
            else if (apiEndpoint.contains("?")) "$apiEndpoint&key=$apiKey"
            else "$apiEndpoint?key=$apiKey"

            requestBuilder.url(urlWithKey)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
        } else {
            requestBuilder.url(apiEndpoint)
            if (apiKey.isNotBlank() && apiKey != "free" && apiKey != "0000000000") {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            if (apiEndpoint.contains("aihorde.net")) {
                requestBuilder.addHeader("apikey", if (apiKey.isBlank()) "0000000000" else apiKey)
                requestBuilder.addHeader("Client-Agent", "Orbital:1.0:android")
            }
            requestBuilder.addHeader("Content-Type", "application/json")
        }

        val request = requestBuilder.post(jsonPayload.toString().toRequestBody(MEDIA_TYPE_JSON)).build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Stream network failure", e)
                connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: response.message
                        Log.e(TAG, "API Error: ${response.code} - $errorBody")

                        // Self-healing: if Gemini model was retired (404), auto-migrate to latest active model
                        if (response.code == 404 && isGemini) {
                            val currentRequestedModel = apiEndpoint.substringAfter("models/").substringBefore(":stream")
                            val matches = Regex("models/([a-zA-Z0-9.-]+)").findAll(errorBody).map { it.groupValues[1] }.toList()
                            val recommended = matches.lastOrNull { it != currentRequestedModel && !it.contains("2.5") }
                                ?: if (currentRequestedModel != "gemini-3.6-flash") "gemini-3.6-flash" else "gemini-2.0-flash"

                            if (recommended != currentRequestedModel && onGeminiModelMigration != null) {
                                Log.i(TAG, "Self-healing: Auto-migrating Gemini from $currentRequestedModel to $recommended")
                                val newEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/$recommended:streamGenerateContent?alt=sse"
                                onGeminiModelMigration(recommended, newEndpoint)
                                return
                            }
                        }

                        connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                        onError(IOException("HTTP ${response.code}: $errorBody"))
                        return
                    }

                    connectionStatusCallback?.invoke(ConnectionStatus.CONNECTED)

                    response.body?.let { body ->
                        val lines = mutableListOf<String>()
                        var hadDataPrefix = false
                        val reader = body.charStream().buffered()
                        while (true) {
                            val line = reader.readLine() ?: break
                            lines.add(line)
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data:")) {
                                hadDataPrefix = true
                                val data = trimmed.removePrefix("data:").trim()
                                if (data == "[DONE]") {
                                    onComplete()
                                    return@onResponse
                                }
                                if (data.isNotBlank()) {
                                    parseChunk(data, onChunk)
                                }
                            }
                        }
                        if (!hadDataPrefix && lines.isNotEmpty()) {
                            val fullBody = lines.joinToString("\n").trim()
                            if (fullBody.isNotBlank()) {
                                parseFullResponse(fullBody, onChunk)
                            }
                        }
                        onComplete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stream read exception", e)
                    connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                    onError(e)
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun parseFullResponse(body: String, onChunk: (String) -> Unit) {
        try {
            val json = JSONObject(body)
            val content = when {
                json.has("candidates") -> {
                    json.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                }
                json.has("choices") -> {
                    val choice = json.optJSONArray("choices")?.optJSONObject(0)
                    choice?.optJSONObject("message")?.optString("content")
                        ?: choice?.optJSONObject("delta")?.optString("content")
                        ?: choice?.optString("text")
                }
                json.has("response") -> json.optString("response")
                json.has("text") -> json.optString("text")
                else -> body
            }
            if (!content.isNullOrBlank()) {
                onChunk(content)
            } else {
                onChunk(body)
            }
        } catch (_: Exception) {
            onChunk(body)
        }
    }

    private fun parseChunk(data: String, onChunk: (String) -> Unit) {
        try {
            val jsonResponse = JSONObject(data)
            if (jsonResponse.has("candidates")) {
                // Gemini SSE structure
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val text = parts.optJSONObject(i)?.optString("text")
                            if (!text.isNullOrBlank()) {
                                onChunk(text)
                            }
                        }
                    }
                }
            } else if (jsonResponse.has("choices")) {
                // OpenAI / Groq / Cerebras SSE structure
                val choices = jsonResponse.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val deltaObj = firstChoice.optJSONObject("delta")
                    val content = deltaObj?.optString("content")?.takeIf { it.isNotBlank() }
                        ?: deltaObj?.optString("text")?.takeIf { it.isNotBlank() }
                        ?: firstChoice.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                        ?: firstChoice.optString("text").takeIf { it.isNotBlank() }
                    if (!content.isNullOrBlank()) {
                        onChunk(content)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse chunk: $data", e)
        }
    }

    private fun buildGeminiPayload(messages: List<Map<String, Any>>): JSONObject {
        return JSONObject().apply {
            var systemInstructionText: String? = null
            val contentsArray = JSONArray()
            var lastRole: String? = null
            var lastPartsArray: JSONArray? = null

            for (msg in messages) {
                val role = msg["role"] as? String ?: "user"
                val text = msg["content"] as? String
                    ?: (msg["parts"] as? List<Map<String, String>>)?.firstOrNull()?.get("text")
                    ?: ""

                if (role.equals("system", ignoreCase = true)) {
                    systemInstructionText = if (systemInstructionText.isNullOrBlank()) text else "$systemInstructionText\n\n$text"
                } else {
                    val mappedRole = if (role == "assistant" || role == "model") "model" else "user"
                    val currentParts = lastPartsArray
                    if (mappedRole == lastRole && currentParts != null) {
                        // Append text to the previous turn to satisfy Gemini turn-alternation rule
                        currentParts.put(JSONObject().put("text", text))
                    } else {
                        val partsArray = JSONArray().put(JSONObject().put("text", text.ifBlank { "..." }))
                        contentsArray.put(JSONObject().put("role", mappedRole).put("parts", partsArray))
                        lastRole = mappedRole
                        lastPartsArray = partsArray
                    }
                }
            }

            if (!systemInstructionText.isNullOrBlank()) {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
                })
            }

            if (contentsArray.length() == 0) {
                val partsArray = JSONArray().put(JSONObject().put("text", "Hello"))
                contentsArray.put(JSONObject().put("role", "user").put("parts", partsArray))
            }

            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }
    }

    private fun buildOpenAiPayload(model: String, messages: List<Map<String, Any>>): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("messages", JSONArray(messages.map { msg ->
                JSONObject().apply {
                    put("role", msg["role"] as? String ?: "user")
                    val content = (msg["content"] as? String)
                        ?: (msg["parts"] as? List<Map<String, String>>)?.firstOrNull()?.get("text")
                        ?: ""
                    put("content", content)
                }
            }))
            put("stream", true)
            put("temperature", 0.7)
        }
    }
}
