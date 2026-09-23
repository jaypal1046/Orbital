package com.orbital.data

import android.util.Log
import com.squareup.okhttp.MediaType.Companion.toMediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import org.json.JSONObject
import java.io.IOException

class LlmRepository(private val okHttpClient: OkHttpClient = OkHttpClient()) {

    companion object {
        private const val TAG = "LlmRepository"
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
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
        messages: List<Message>,
        onChunk: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val json = JSONObject().apply {
            put("model", "default")
            put("messages", JSONObjectArray(messages))
            put("stream", true)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(apiEndpoint)
            .post(RequestBody.create(json.toString(), MEDIA_TYPE_JSON))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(request).enqueue(object : com.squareup.okhttp.Callback {
            override fun onFailure(call: com.squareup.okhttp.Call, e: IOException) {
                Log.e(TAG, "Stream failed", e)
                onError(e)
            }

            override fun onResponse(call: com.squareup.okhttp.Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }

                    response.body?.let { body ->
                        val reader = body.charStream()
                        val buffer = CharArray(8192)
                        var charsRead: Int
                        var lineBuilder = StringBuilder()

                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            val chunk = String(buffer, 0, charsRead)
                            lineBuilder.append(chunk)

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
                                        val content = jsonResponse
                                            .getJSONObject("choices")
                                            .getJSONArray(0)
                                            .getJSONObject(0)
                                            .getString("delta")
                                            .optString("content", "")
                                        if (content.isNotBlank()) {
                                            onChunk(content)
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
                    onError(e)
                } finally {
                    response.close()
                }
            }
        })
    }

    private data class Message(
        val role: String,
        val content: String
    )

    private fun JSONObjectArray(messages: List<Message>): org.json.JSONArray {
        return org.json.JSONArray(messages.map { JSONObject().apply {
            put("role", it.role)
            put("content", it.content)
        } })
    }
}