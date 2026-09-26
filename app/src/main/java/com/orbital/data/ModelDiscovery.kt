package com.orbital.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles dynamic fetching and catalog parsing of live LLM models from 24+ provider endpoints.
 */
class ModelDiscovery(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "ModelDiscovery"
    }

    private val discoveredProviderModels = ConcurrentHashMap<ProviderType, List<String>>()
    private val discoveredCatalogModels = ConcurrentHashMap<ProviderType, List<CatalogModel>>()

    fun getAvailableModels(type: ProviderType): List<String> {
        return discoveredCatalogModels[type]?.map { it.modelId }
            ?: discoveredProviderModels[type]
            ?: ProviderRegistry.getInfo(type).models
    }

    fun getAvailableCatalogModels(type: ProviderType): List<CatalogModel> {
        return discoveredCatalogModels[type] ?: ProviderRegistry.getInfo(type).catalogModels
    }

    fun discoverModels(type: ProviderType, key: String, onResult: (List<String>) -> Unit) {
        discoverCatalogModels(type, key) { catalogList ->
            onResult(catalogList.map { it.modelId })
        }
    }

    fun discoverCatalogModels(
        type: ProviderType,
        key: String,
        onResult: (List<CatalogModel>) -> Unit
    ) {
        val cleanKey = key.trim()
        val info = ProviderRegistry.getInfo(type)
        val isKeyless = type == ProviderType.KILO || type == ProviderType.OVH || type == ProviderType.POLLINATIONS || type == ProviderType.AIHORDE

        if (cleanKey.isBlank() && !isKeyless) {
            onResult(getAvailableCatalogModels(type))
            return
        }

        val modelsUrl = getDiscoveryEndpoint(type, cleanKey)
        val requestBuilder = Request.Builder().url(modelsUrl)

        if (info.isGemini) {
            requestBuilder.addHeader("x-goog-api-key", cleanKey)
        } else if (cleanKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $cleanKey")
        }

        okHttpClient.newCall(requestBuilder.build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.w(TAG, "Dynamic model discovery failed for $type: ${e.message}")
                onResult(getAvailableCatalogModels(type))
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val jsonObj = JSONObject(bodyStr)
                        val catalogList = mutableListOf<CatalogModel>()

                        if (info.isGemini) {
                            val modelsArr = jsonObj.optJSONArray("models")
                            if (modelsArr != null) {
                                for (i in 0 until modelsArr.length()) {
                                    val m = modelsArr.getJSONObject(i)
                                    val id = m.optString("name").replace("models/", "")
                                    val rawName = m.optString("displayName").ifBlank { id }
                                    val methods = m.optJSONArray("supportedGenerationMethods")
                                    val supportsGenerate = (0 until (methods?.length() ?: 0)).any { methods?.getString(it) == "generateContent" }
                                    if (supportsGenerate) {
                                        val inputTokens = m.optLong("inputTokenLimit", 1000000L)
                                        val ctx = inferContextWindow(id, inputTokens)
                                        val size = inferSizeLabel(id)
                                        catalogList.add(CatalogModel(id, rawName, size, ctx))
                                    }
                                }
                            }
                        } else {
                            val dataArr = jsonObj.optJSONArray("data") ?: jsonObj.optJSONArray("models")
                            if (dataArr != null) {
                                for (i in 0 until dataArr.length()) {
                                    val item = dataArr.getJSONObject(i)
                                    val id = item.optString("id").ifBlank { item.optString("name") }
                                    if (id.isNotBlank()) {
                                        val displayName = formatModelDisplayName(id, type)
                                        val size = inferSizeLabel(id)
                                        val ctx = inferContextWindow(id, item.optLong("context_window", 0L).takeIf { it > 0 })
                                        catalogList.add(CatalogModel(id, displayName, size, ctx))
                                    }
                                }
                            }
                        }

                        if (catalogList.isNotEmpty()) {
                            discoveredCatalogModels[type] = catalogList
                            discoveredProviderModels[type] = catalogList.map { it.modelId }
                            onResult(catalogList)
                            return
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse discovered models for $type", e)
                } finally {
                    response.close()
                }
                onResult(getAvailableCatalogModels(type))
            }
        })
    }

    private fun getDiscoveryEndpoint(type: ProviderType, cleanKey: String): String {
        return when (type) {
            ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
            ProviderType.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/models"
            ProviderType.GROQ -> "https://api.groq.com/openai/v1/models"
            ProviderType.CEREBRAS -> "https://api.cerebras.ai/v1/models"
            ProviderType.MISTRAL -> "https://api.mistral.ai/v1/models"
            ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/models"
            ProviderType.GITHUB_MODELS -> "https://models.inference.ai.azure.com/models"
            ProviderType.ALPHAOX -> "https://api.oxalpha.io/v1/models"
            ProviderType.ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/models"
            ProviderType.HUGGINGFACE -> "https://router.huggingface.co/v1/models"
            ProviderType.CLOUDFLARE -> "https://api.cloudflare.com/client/v4/accounts"
            ProviderType.COHERE -> "https://api.cohere.com/v2/models"
            ProviderType.OLLAMA -> "http://127.0.0.1:11434/v1/models"
            ProviderType.POLLINATIONS -> "https://gen.pollinations.ai/v1/models"
            ProviderType.KILO -> "https://api.kilo.ai/api/gateway/models"
            ProviderType.OVH -> "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/models"
            ProviderType.LLM7 -> "https://api.llm7.io/v1/models"
            ProviderType.AGNES -> "https://apihub.agnes-ai.com/v1/models"
            ProviderType.ROUTEWAY -> "https://api.routeway.ai/v1/models"
            ProviderType.SAIL -> "https://api.sailresearch.com/v1/models"
            ProviderType.RADEON -> "https://developer.amd.com.cn/radeon/api/v1/models"
            ProviderType.MODELSCOPE -> "https://api-inference.modelscope.cn/v1/models"
            ProviderType.AIHORDE -> "https://aihorde.net/api/v2/workers"
            ProviderType.CUSTOM -> "http://127.0.0.1:3001/v1/models"
        }
    }

    fun formatModelDisplayName(rawId: String, platform: ProviderType): String {
        val clean = rawId.replace("models/", "")
            .replace("meta/", "")
            .replace("nvidia/", "")
            .replace("google/", "")
            .replace("mistralai/", "")
            .replace("deepseek-ai/", "")
        val words = clean.split("-", "_", "/").joinToString(" ") { word ->
            when (word.lowercase()) {
                "it" -> "IT"
                "nv" -> "(NV)"
                "ctx" -> "ctx"
                "r1" -> "R1"
                "v3" -> "V3"
                "gpt" -> "GPT"
                "oss" -> "OSS"
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }
        val suffix = when (platform) {
            ProviderType.NVIDIA_NIM -> " (NV)"
            ProviderType.GITHUB_MODELS -> " (GitHub)"
            ProviderType.OPENROUTER -> if (rawId.contains(":free")) " (Free)" else ""
            else -> ""
        }
        return if (words.endsWith("(NV)") || words.endsWith("(GitHub)") || words.endsWith("(Free)") || suffix.isEmpty()) words else "$words$suffix".trim()
    }

    fun inferSizeLabel(rawId: String): String {
        val lower = rawId.lowercase()
        return when {
            lower.contains("frontier") || lower.contains("3.6") || lower.contains("3.7") || lower.contains("3.8") ||
                    lower.contains("gpt-5") || lower.contains("r1") || lower.contains("480b") || lower.contains("340b") ||
                    lower.contains("pro") -> "Frontier"
            lower.contains("70b") || lower.contains("90b") || lower.contains("120b") || lower.contains("large") ||
                    lower.contains("235b") || lower.contains("31b") || lower.contains("27b") -> "Large"
            lower.contains("8b") || lower.contains("7b") || lower.contains("small") || lower.contains("guard") ||
                    lower.contains("safety") || lower.contains("11b") -> "Small"
            else -> "Medium"
        }
    }

    fun inferContextWindow(rawId: String, inputTokens: Long? = null): String {
        if (inputTokens != null && inputTokens > 0) {
            return when {
                inputTokens >= 1_000_000 -> "${inputTokens / 1_000_000}M ctx"
                inputTokens >= 1_000 -> "${inputTokens / 1_000}K ctx"
                else -> "$inputTokens ctx"
            }
        }
        val lower = rawId.lowercase()
        return when {
            lower.contains("1m") || lower.contains("flash") || lower.contains("pro") || lower.contains("ox-alpha") -> "1M ctx"
            lower.contains("2m") -> "2M ctx"
            lower.contains("262k") || lower.contains("256k") || lower.contains("gemma-4") || lower.contains("nemotron-3") -> "262K ctx"
            lower.contains("131k") || lower.contains("128k") || lower.contains("vision") || lower.contains("llama-3") || lower.contains("guard") -> "131K ctx"
            lower.contains("32k") || lower.contains("33k") -> "33K ctx"
            lower.contains("16k") -> "16K ctx"
            else -> "128K ctx"
        }
    }
}
