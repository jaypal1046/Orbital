package com.orbital.decision.jev

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class OpenJevRequestPayload(
    val context: String,
    val query: String,
    val type: String, // "binary", "choice", "score"
    val options: List<String>? = null,
    val temperature: Float = 0.5f
)

@Serializable
data class OpenJevResponsePayload(
    val result: String,
    val confidence: Float,
    val scores: Map<String, Float>? = null,
    val latencyMs: Long = 0,
    val model: String = "openjev-vllm"
)

class JevRemoteClient(
    private val baseUrl: String?,
    private val apiKey: String? = null,
    private val localFallback: JevDecisionEngine = JevLocalFallbackEngine(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : JevDecisionEngine {

    companion object {
        private const val TAG = "JevRemoteClient"
    }

    override suspend fun decideBinary(
        context: String,
        question: String,
        confidenceThreshold: Float
    ): BooleanDecision = withContext(Dispatchers.IO) {
        if (baseUrl.isNullOrBlank()) {
            return@withContext localFallback.decideBinary(context, question, confidenceThreshold)
        }

        try {
            val payload = OpenJevRequestPayload(
                context = context,
                query = question,
                type = "binary"
            )
            val response = executeHttpRequest(payload)
            val isAffirmative = response.result.equals("yes", ignoreCase = true) ||
                    response.result.equals("true", ignoreCase = true)

            BooleanDecision(
                value = isAffirmative && response.confidence >= confidenceThreshold,
                confidence = response.confidence,
                metadata = DecisionMetadata(
                    latencyMs = response.latencyMs,
                    modelIdentifier = response.model,
                    isLocalExecution = false
                ),
                reasoningHint = "Evaluated via OpenJev server"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Remote OpenJEV binary request failed (${e.message}), failing over to local engine.")
            localFallback.decideBinary(context, question, confidenceThreshold)
        }
    }

    override suspend fun <T : Any> decideChoice(
        context: String,
        prompt: String,
        candidates: List<T>,
        keyExtractor: (T) -> String,
        confidenceThreshold: Float
    ): ChoiceDecision<T> = withContext(Dispatchers.IO) {
        if (baseUrl.isNullOrBlank()) {
            return@withContext localFallback.decideChoice(context, prompt, candidates, keyExtractor, confidenceThreshold)
        }

        try {
            require(candidates.isNotEmpty()) { "Candidate list cannot be empty" }
            val optionStrings = candidates.map(keyExtractor)
            val payload = OpenJevRequestPayload(
                context = context,
                query = prompt,
                type = "choice",
                options = optionStrings
            )
            val response = executeHttpRequest(payload)
            val selectedIdx = optionStrings.indexOf(response.result).takeIf { it >= 0 } ?: 0
            val distribution = candidates.associateWith { item ->
                response.scores?.get(keyExtractor(item)) ?: 0.0f
            }

            ChoiceDecision(
                value = candidates[selectedIdx],
                selectedIndex = selectedIdx,
                distribution = distribution,
                confidence = response.confidence,
                metadata = DecisionMetadata(
                    latencyMs = response.latencyMs,
                    modelIdentifier = response.model,
                    isLocalExecution = false
                ),
                reasoningHint = "Evaluated via OpenJev server"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Remote OpenJEV choice request failed (${e.message}), failing over to local engine.")
            localFallback.decideChoice(context, prompt, candidates, keyExtractor, confidenceThreshold)
        }
    }

    override suspend fun scoreRelevance(
        context: String,
        candidate: String
    ): ScoreDecision = withContext(Dispatchers.IO) {
        if (baseUrl.isNullOrBlank()) {
            return@withContext localFallback.scoreRelevance(context, candidate)
        }

        try {
            val payload = OpenJevRequestPayload(
                context = context,
                query = candidate,
                type = "score"
            )
            val response = executeHttpRequest(payload)
            ScoreDecision(
                value = response.confidence,
                confidence = response.confidence,
                metadata = DecisionMetadata(
                    latencyMs = response.latencyMs,
                    modelIdentifier = response.model,
                    isLocalExecution = false
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Remote OpenJEV score request failed (${e.message}), failing over to local engine.")
            localFallback.scoreRelevance(context, candidate)
        }
    }

    private fun executeHttpRequest(payload: OpenJevRequestPayload): OpenJevResponsePayload {
        val start = System.currentTimeMillis()
        val body = json.encodeToString(OpenJevRequestPayload.serializer(), payload)
            .toRequestBody("application/json".toMediaType())

        val reqBuilder = Request.Builder()
            .url("${baseUrl!!.trimEnd('/')}/v1/systemone")
            .post(body)

        apiKey?.let {
            if (it.isNotBlank()) reqBuilder.addHeader("Authorization", "Bearer $it")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw IllegalStateException("OpenJEV request failed with HTTP ${res.code}")
            }
            val resBody = res.body?.string().orEmpty()
            val parsed = json.decodeFromString(OpenJevResponsePayload.serializer(), resBody)
            return parsed.copy(latencyMs = System.currentTimeMillis() - start)
        }
    }
}
