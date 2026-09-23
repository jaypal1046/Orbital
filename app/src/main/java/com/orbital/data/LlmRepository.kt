package com.orbital.data

import android.util.Log
import com.orbital.overlay.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LlmRepository(
    private val secureStorage: SecureStorage? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    companion object {
        private const val TAG = "LlmRepository"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_TIMEOUT_MS = 30000L
        private const val COOLDOWN_MS = 60000L
    }

    private var connectionStatusCallback: ((ConnectionStatus) -> Unit)? = null

    // Provider state tracking
    private val providerStatus = ConcurrentHashMap<ProviderType, ProviderState>()
    private val providerCooldowns = ConcurrentHashMap<ProviderType, Long>()
    private var currentProvider: ProviderType? = null

    // Server configuration
    private var serverConfig = ServerConfig()
    private var routerConfig = RouterConfig()

    // Coroutine scope for background operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Provider configuration
    private var providerConfigs = mutableMapOf<ProviderType, ProviderConfig>()

    // Initialize with default providers
    init {
        // Initialize provider status
        ProviderType.values().forEach { type ->
            providerStatus[type] = ProviderState.AVAILABLE
        }
        secureStorage?.let { storage ->
            ProviderRegistry.allProviders.forEach { info ->
                val key = storage.getProviderApiKey(info.type.name) ?: ""
                val isEnabled = storage.isProviderEnabled(info.type.name)
                updateProviderKey(info.type, key)
                if (!isEnabled) {
                    providerStatus[info.type] = ProviderState.UNAVAILABLE
                }
            }
        }
    }

    /**
     * Configure the LLM repository with provider configurations
     * @param configs Map of provider types to their configurations
     */
    fun configureProviders(configs: Map<ProviderType, ProviderConfig>) {
        providerConfigs.putAll(configs)
    }

    /**
     * Update the server configuration
     * @param config New server configuration
     */
    fun updateServerConfig(config: ServerConfig) {
        serverConfig = config
    }

    /**
     * Update the router configuration
     * @param config New router configuration
     */
    fun updateRouterConfig(config: RouterConfig) {
        routerConfig = config
    }

    /**
     * Set the active provider type
     */
    fun setCurrentProvider(type: ProviderType) {
        this.currentProvider = type
    }

    private val discoveredProviderModels = ConcurrentHashMap<ProviderType, List<String>>()

    private val discoveredCatalogModels = ConcurrentHashMap<ProviderType, List<CatalogModel>>()

    /**
     * Get available models for a provider (discovered dynamically or defaults)
     */
    fun getAvailableModels(type: ProviderType): List<String> {
        return discoveredCatalogModels[type]?.map { it.modelId }
            ?: discoveredProviderModels[type]
            ?: ProviderRegistry.getInfo(type).models
    }

    /**
     * Get available catalog models with size, context and labels
     */
    fun getAvailableCatalogModels(type: ProviderType): List<CatalogModel> {
        return discoveredCatalogModels[type] ?: ProviderRegistry.getInfo(type).catalogModels
    }

    /**
     * Set the selected model for a specific provider
     */
    fun setProviderModel(type: ProviderType, model: String) {
        val key = secureStorage?.getProviderApiKey(type.name) ?: ""
        val newConfig = when (type) {
            ProviderType.GROQ -> ProviderConfig.Groq(key, model)
            ProviderType.CEREBRAS -> ProviderConfig.Cerebras(key, model)
            ProviderType.ALPHAOX -> ProviderConfig.AlphaOx(key, model)
            ProviderType.GEMINI -> {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
                ProviderConfig.Gemini(key, model, endpoint)
            }
            ProviderType.NVIDIA_NIM -> ProviderConfig.NvidiaNim(key, model)
            ProviderType.MISTRAL -> ProviderConfig.Mistral(key, model)
            ProviderType.OPENROUTER -> ProviderConfig.OpenRouter(key, model)
            ProviderType.GITHUB_MODELS -> ProviderConfig.GitHubModels(key, model)
            ProviderType.ZHIPU -> ProviderConfig.Zhipu(key, model)
            ProviderType.HUGGINGFACE -> ProviderConfig.HuggingFace(key, model)
            ProviderType.CLOUDFLARE -> ProviderConfig.Cloudflare(key, model)
            ProviderType.COHERE -> ProviderConfig.Cohere(key, model)
            ProviderType.OLLAMA -> ProviderConfig.Ollama(key, model)
            ProviderType.POLLINATIONS -> ProviderConfig.Pollinations(key, model)
            ProviderType.KILO -> ProviderConfig.Kilo(key, model)
            ProviderType.OVH -> ProviderConfig.Ovh(key, model)
            ProviderType.AIHORDE -> ProviderConfig.AiHorde(key, model)
            ProviderType.LLM7 -> ProviderConfig.Llm7(key, model)
            ProviderType.AGNES -> ProviderConfig.Agnes(key, model)
            ProviderType.ROUTEWAY -> ProviderConfig.Routeway(key, model)
            ProviderType.SAIL -> ProviderConfig.Sail(key, model)
            ProviderType.RADEON -> ProviderConfig.Radeon(key, model)
            ProviderType.MODELSCOPE -> ProviderConfig.ModelScope(key, model)
            ProviderType.CUSTOM -> ProviderConfig.Custom(key, model, "http://127.0.0.1:3001/v1/chat/completions")
        }
        providerConfigs[type] = newConfig
        secureStorage?.saveProviderSelectedModel(type.name, model)
    }

    /**
     * Dynamically discover all live models from the provider's API
     */
    fun discoverModels(type: ProviderType, key: String, onResult: (List<String>) -> Unit) {
        discoverCatalogModels(type, key) { catalogList ->
            onResult(catalogList.map { it.modelId })
        }
    }

    /**
     * Dynamically fetch live models from provider and parse them into CatalogModel
     */
    fun discoverCatalogModels(type: ProviderType, key: String, onResult: (List<CatalogModel>) -> Unit) {
        val cleanKey = key.trim()
        val info = ProviderRegistry.getInfo(type)
        if (cleanKey.isBlank() && type != ProviderType.KILO && type != ProviderType.OVH && type != ProviderType.POLLINATIONS) {
            onResult(getAvailableCatalogModels(type))
            return
        }

        val modelsUrl = when (type) {
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

        val requestBuilder = Request.Builder().url(modelsUrl)
        if (info.isGemini) {
            requestBuilder.addHeader("x-goog-api-key", cleanKey)
        } else {
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
                }
                onResult(getAvailableCatalogModels(type))
            }
        })
    }

    private fun formatModelDisplayName(rawId: String, platform: ProviderType): String {
        val clean = rawId.replace("models/", "").replace("meta/", "").replace("nvidia/", "").replace("google/", "").replace("mistralai/", "").replace("deepseek-ai/", "")
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

    private fun inferSizeLabel(rawId: String): String {
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

    private fun inferContextWindow(rawId: String, inputTokens: Long? = null): String {
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

    private var currentRoutingMode: RoutingMode = RoutingMode.AUTO

    fun getRoutingMode(): RoutingMode = currentRoutingMode

    fun setRoutingMode(mode: RoutingMode) {
        currentRoutingMode = mode
    }

    /**
     * Dynamically update the key for a provider
     */
    fun updateProviderKey(type: ProviderType, key: String) {
        val cleanKey = key.trim()
        val savedModel = secureStorage?.getProviderSelectedModel(type.name)
        val defaultModel = savedModel ?: ProviderRegistry.getInfo(type).defaultModel

        val newConfig = when (type) {
            ProviderType.GROQ -> ProviderConfig.Groq(cleanKey, defaultModel)
            ProviderType.CEREBRAS -> ProviderConfig.Cerebras(cleanKey, defaultModel)
            ProviderType.ALPHAOX -> ProviderConfig.AlphaOx(cleanKey, defaultModel)
            ProviderType.GEMINI -> {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$defaultModel:streamGenerateContent?alt=sse"
                ProviderConfig.Gemini(cleanKey, defaultModel, endpoint)
            }
            ProviderType.NVIDIA_NIM -> ProviderConfig.NvidiaNim(cleanKey, defaultModel)
            ProviderType.MISTRAL -> ProviderConfig.Mistral(cleanKey, defaultModel)
            ProviderType.OPENROUTER -> ProviderConfig.OpenRouter(cleanKey, defaultModel)
            ProviderType.GITHUB_MODELS -> ProviderConfig.GitHubModels(cleanKey, defaultModel)
            ProviderType.ZHIPU -> ProviderConfig.Zhipu(cleanKey, defaultModel)
            ProviderType.HUGGINGFACE -> ProviderConfig.HuggingFace(cleanKey, defaultModel)
            ProviderType.CLOUDFLARE -> ProviderConfig.Cloudflare(cleanKey, defaultModel)
            ProviderType.COHERE -> ProviderConfig.Cohere(cleanKey, defaultModel)
            ProviderType.OLLAMA -> ProviderConfig.Ollama(cleanKey, defaultModel)
            ProviderType.POLLINATIONS -> ProviderConfig.Pollinations(cleanKey, defaultModel)
            ProviderType.KILO -> ProviderConfig.Kilo(cleanKey, defaultModel)
            ProviderType.OVH -> ProviderConfig.Ovh(cleanKey, defaultModel)
            ProviderType.AIHORDE -> ProviderConfig.AiHorde(cleanKey, defaultModel)
            ProviderType.LLM7 -> ProviderConfig.Llm7(cleanKey, defaultModel)
            ProviderType.AGNES -> ProviderConfig.Agnes(cleanKey, defaultModel)
            ProviderType.ROUTEWAY -> ProviderConfig.Routeway(cleanKey, defaultModel)
            ProviderType.SAIL -> ProviderConfig.Sail(cleanKey, defaultModel)
            ProviderType.RADEON -> ProviderConfig.Radeon(cleanKey, defaultModel)
            ProviderType.MODELSCOPE -> ProviderConfig.ModelScope(cleanKey, defaultModel)
            ProviderType.CUSTOM -> ProviderConfig.Custom(cleanKey, defaultModel, "http://127.0.0.1:3001/v1/chat/completions")
        }
        providerConfigs[type] = newConfig
        providerStatus[type] = if (cleanKey.isNotBlank() || type == ProviderType.KILO || type == ProviderType.OVH || type == ProviderType.POLLINATIONS || type == ProviderType.AIHORDE) ProviderState.AVAILABLE else ProviderState.UNAVAILABLE
    }

    /**
     * Test health of a single provider with a quick ping & auto-model discovery
     */
    fun testProvider(type: ProviderType, key: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanKey = key.trim()
        val info = ProviderRegistry.getInfo(type)

        if (info.isGemini) {
            val listModelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
            val request = Request.Builder()
                .url(listModelsUrl)
                .addHeader("x-goog-api-key", cleanKey)
                .build()

            okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    providerStatus[type] = ProviderState.UNAVAILABLE
                    onResult?.invoke(false, e.message)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val jsonObj = JSONObject(bodyStr)
                            val modelsArr = jsonObj.optJSONArray("models")
                            if (modelsArr != null && modelsArr.length() > 0) {
                                val available = mutableListOf<String>()
                                for (i in 0 until modelsArr.length()) {
                                    val m = modelsArr.getJSONObject(i)
                                    val mName = m.optString("name").replace("models/", "")
                                    val methods = m.optJSONArray("supportedGenerationMethods")
                                    val supportsGenerate = (0 until (methods?.length() ?: 0)).any { methods?.getString(it) == "generateContent" }
                                    if (supportsGenerate && !mName.contains("2.5")) {
                                        available.add(mName)
                                    }
                                }
                                val chosenModel = when {
                                    available.any { it == "gemini-3.6-flash" } -> "gemini-3.6-flash"
                                    available.any { it == "gemini-2.0-flash" } -> "gemini-2.0-flash"
                                    available.any { it == "gemini-1.5-flash" } -> "gemini-1.5-flash"
                                    available.any { it.contains("3.6") } -> available.first { it.contains("3.6") }
                                    available.any { it.contains("flash") } -> available.first { it.contains("flash") }
                                    available.isNotEmpty() -> available.first()
                                    else -> "gemini-3.6-flash"
                                }
                                val newEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/$chosenModel:streamGenerateContent?alt=sse"
                                providerConfigs[type] = ProviderConfig.Gemini(cleanKey, chosenModel, newEndpoint)
                                setProviderModel(type, chosenModel)
                            }
                            providerStatus[type] = ProviderState.AVAILABLE
                            onResult?.invoke(true, null)
                        } else {
                            val errBody = response.body?.string() ?: response.message
                            providerStatus[type] = ProviderState.UNAVAILABLE
                            onResult?.invoke(false, "HTTP ${response.code}: $errBody")
                        }
                    } catch (e: Exception) {
                        providerStatus[type] = ProviderState.UNAVAILABLE
                        onResult?.invoke(false, e.message)
                    }
                }
            })
            return
        }

        val messages = listOf(mapOf("role" to "user", "content" to "hi"))

        streamCompletion(
            apiEndpoint = info.endpoint,
            apiKey = cleanKey.ifBlank { "free" },
            messages = messages,
            onChunk = { _ ->
                providerStatus[type] = ProviderState.AVAILABLE
                onResult?.invoke(true, null)
            },
            onError = { error ->
                providerStatus[type] = ProviderState.UNAVAILABLE
                onResult?.invoke(false, error.message)
            }
        )
    }

    /**
     * Get the current provider configuration
     * @return Current provider configuration or null if not set
     */
    fun getCurrentProviderConfig(): ProviderConfig? {
        if (currentProvider == null) {
            currentProvider = getNextAvailableProvider()
                ?: providerConfigs.keys.firstOrNull { type ->
                    getProviderKey(type).isNotBlank() || type == ProviderType.CUSTOM || type == ProviderType.KILO || type == ProviderType.OVH
                }
                ?: providerConfigs.keys.firstOrNull()
        }
        return currentProvider?.let { providerConfigs[it] }
    }

    /**
     * Get the current provider type
     * @return Current provider type or null if not set
     */
    fun getCurrentProviderType(): ProviderType? {
        return currentProvider
    }

    /**
     * Get the status of a specific provider
     * @param type Provider type to check
     * @return Provider status
     */
    fun getProviderStatus(type: ProviderType): ProviderState {
        return providerStatus[type] ?: ProviderState.UNAVAILABLE
    }

    /**
     * Check if a provider is available for use
     * @param type Provider type to check
     * @return true if provider is available
     */
    fun isProviderAvailable(type: ProviderType): Boolean {
        return getProviderStatus(type) == ProviderState.AVAILABLE
    }

    /**
     * Mark a provider as unavailable due to rate limiting
     * @param type Provider type to mark as unavailable
     */
    private fun markProviderUnavailable(type: ProviderType) {
        providerStatus[type] = ProviderState.UNAVAILABLE
        providerCooldowns[type] = System.currentTimeMillis() + COOLDOWN_MS
    }

    /**
     * Check if a provider is in cooldown
     * @param type Provider type to check
     * @return true if provider is in cooldown
     */
    private fun isProviderInCooldown(type: ProviderType): Boolean {
        val cooldownTime = providerCooldowns[type] ?: return false
        return System.currentTimeMillis() < cooldownTime
    }

    /**
     * Reset provider states and cooldowns
     */
    fun resetProviders() {
        providerStatus.clear()
        providerCooldowns.clear()
        ProviderType.values().forEach { type ->
            providerStatus[type] = ProviderState.AVAILABLE
        }
    }

    fun getProviderKey(type: ProviderType): String {
        val fromStorage = secureStorage?.getProviderApiKey(type.name)?.takeIf { it.isNotBlank() }
            ?: if (type == ProviderType.GEMINI) {
                secureStorage?.getProviderApiKey("gemini")
                    ?: secureStorage?.getProviderApiKey("google")
                    ?: secureStorage?.getApiKey()
            } else null

        if (!fromStorage.isNullOrBlank()) return fromStorage.trim()

        return when (val cfg = providerConfigs[type]) {
            is ProviderConfig.Groq -> cfg.apiKey
            is ProviderConfig.Cerebras -> cfg.apiKey
            is ProviderConfig.AlphaOx -> cfg.apiKey
            is ProviderConfig.Gemini -> cfg.apiKey
            is ProviderConfig.NvidiaNim -> cfg.apiKey
            is ProviderConfig.Mistral -> cfg.apiKey
            is ProviderConfig.OpenRouter -> cfg.apiKey
            is ProviderConfig.GitHubModels -> cfg.apiKey
            is ProviderConfig.Zhipu -> cfg.apiKey
            is ProviderConfig.HuggingFace -> cfg.apiKey
            is ProviderConfig.Cloudflare -> cfg.apiKey
            is ProviderConfig.Cohere -> cfg.apiKey
            is ProviderConfig.Ollama -> cfg.apiKey
            is ProviderConfig.Pollinations -> cfg.apiKey
            is ProviderConfig.Kilo -> cfg.apiKey
            is ProviderConfig.Ovh -> cfg.apiKey
            is ProviderConfig.AiHorde -> cfg.apiKey
            is ProviderConfig.Llm7 -> cfg.apiKey
            is ProviderConfig.Agnes -> cfg.apiKey
            is ProviderConfig.Routeway -> cfg.apiKey
            is ProviderConfig.Sail -> cfg.apiKey
            is ProviderConfig.Radeon -> cfg.apiKey
            is ProviderConfig.ModelScope -> cfg.apiKey
            is ProviderConfig.Custom -> cfg.apiKey
            null -> if (type == ProviderType.KILO || type == ProviderType.OVH || type == ProviderType.POLLINATIONS || type == ProviderType.AIHORDE) "free" else ""
        }.trim()
    }

    /**
     * Get the next available provider according to current RoutingMode
     */
    fun getNextAvailableProvider(): ProviderType? {
        val candidateOrder = when (currentRoutingMode) {
            RoutingMode.FAST -> ProviderConfig.fastTierProviders + routerConfig.enabledProviders
            RoutingMode.FRONTIER -> ProviderConfig.frontierTierProviders + routerConfig.enabledProviders
            RoutingMode.PINNED -> currentProvider?.let { listOf(it) } ?: routerConfig.enabledProviders
            RoutingMode.AUTO -> routerConfig.enabledProviders
        }.distinct()

        for (type in candidateOrder) {
            val key = getProviderKey(type)
            val isEnabled = secureStorage?.isProviderEnabled(type.name) ?: true
            val isKeyless = type == ProviderType.KILO || type == ProviderType.OVH || type == ProviderType.POLLINATIONS || type == ProviderType.AIHORDE
            if (isEnabled && (key.isNotBlank() || isKeyless || type == ProviderType.CUSTOM) && isProviderAvailable(type) && !isProviderInCooldown(type)) {
                return type
            }
        }

        return null
    }

    /**
     * Handle provider errors and manage failover
     * @param type Provider type that failed
     * @param error Error that occurred
     */
    private fun handleProviderError(type: ProviderType, error: Throwable) {
        Log.e(TAG, "Provider $type error: ${error.message}", error)
        val errMsg = error.message ?: ""

        // Set cooldown on 429 quota exhaustion, 5xx server errors, or timeouts
        if (errMsg.contains("429") || errMsg.contains("quota", ignoreCase = true) || errMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || errMsg.contains("timeout", ignoreCase = true) || errMsg.contains("50")) {
            providerCooldowns[type] = System.currentTimeMillis() + 60_000L
            providerStatus[type] = ProviderState.IN_COOLDOWN
        } else {
            providerStatus[type] = ProviderState.UNAVAILABLE
        }
    }

    /**
     * Sets a callback for connection status updates
     * @param callback The callback function to receive status updates
     */
    fun setConnectionStatusCallback(callback: (ConnectionStatus) -> Unit) {
        this.connectionStatusCallback = callback
    }

    /**
     * Sends a streaming request using ChatMessage objects and auto-selected provider
     */
    fun streamCompletion(
        model: String,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val activeType = if (currentRoutingMode == RoutingMode.PINNED && currentProvider != null && getProviderKey(currentProvider!!).isNotBlank()) {
            currentProvider!!
        } else {
            getNextAvailableProvider()
                ?: currentProvider?.takeIf { getProviderKey(it).isNotBlank() }
                ?: ProviderRegistry.allProviders.firstOrNull { getProviderKey(it.type).isNotBlank() }?.type
                ?: ProviderType.GEMINI
        }

        currentProvider = activeType
        val config = providerConfigs[activeType]
        val key = getProviderKey(activeType)
        val isKeyless = activeType == ProviderType.KILO || activeType == ProviderType.OVH || activeType == ProviderType.POLLINATIONS || activeType == ProviderType.AIHORDE

        if (key.isBlank() && !isKeyless && activeType != ProviderType.CUSTOM) {
            val configured = ProviderRegistry.allProviders.firstOrNull { getProviderKey(it.type).isNotBlank() }
            if (configured != null) {
                currentProvider = configured.type
                streamCompletion(model, messages, onChunk, onError)
                return
            }
            onError(IOException("No API Key entered for ${activeType.name}. Please enter your key in Keys & Providers."))
            return
        }

        val endpoint = when (config) {
            is ProviderConfig.Groq -> config.baseUrl
            is ProviderConfig.Cerebras -> config.baseUrl
            is ProviderConfig.AlphaOx -> config.baseUrl
            is ProviderConfig.Gemini -> config.baseUrl
            is ProviderConfig.NvidiaNim -> config.baseUrl
            is ProviderConfig.Mistral -> config.baseUrl
            is ProviderConfig.OpenRouter -> config.baseUrl
            is ProviderConfig.GitHubModels -> config.baseUrl
            is ProviderConfig.Zhipu -> config.baseUrl
            is ProviderConfig.HuggingFace -> config.baseUrl
            is ProviderConfig.Cloudflare -> config.baseUrl
            is ProviderConfig.Cohere -> config.baseUrl
            is ProviderConfig.Ollama -> config.baseUrl
            is ProviderConfig.Pollinations -> config.baseUrl
            is ProviderConfig.Kilo -> config.baseUrl
            is ProviderConfig.Ovh -> config.baseUrl
            is ProviderConfig.AiHorde -> config.baseUrl
            is ProviderConfig.Llm7 -> config.baseUrl
            is ProviderConfig.Agnes -> config.baseUrl
            is ProviderConfig.Routeway -> config.baseUrl
            is ProviderConfig.Sail -> config.baseUrl
            is ProviderConfig.Radeon -> config.baseUrl
            is ProviderConfig.ModelScope -> config.baseUrl
            is ProviderConfig.Custom -> config.baseUrl
            null -> ProviderRegistry.getInfo(activeType).endpoint
        }

        val mappedMessages = messages.map { msg ->
            mapOf("role" to msg.role, "content" to (msg.content ?: ""))
        }

        streamCompletion(
            endpoint,
            key,
            mappedMessages,
            onChunk,
            onError = { error ->
                handleProviderError(activeType, error)
                val next = getNextAvailableProvider()
                if (next != null && next != activeType) {
                    Log.i(TAG, "Silent Auto-Failover: Provider $activeType encountered issue. Retrying with: $next")
                    currentProvider = next
                    streamCompletion(model, messages, onChunk, onError)
                } else {
                    val friendlyMsg = if (error.message?.contains("429") == true || error.message?.contains("quota", ignoreCase = true) == true || error.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true) {
                        "All configured AI providers are temporarily rate-limited. Please wait a moment or configure additional provider keys."
                    } else {
                        "AI request failed: ${error.message?.take(120)}"
                    }
                    onError(IOException(friendlyMsg))
                }
            }
        )
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
            // Gemini API uses contents with parts and optional system_instruction
            JSONObject().apply {
                var systemInstructionText: String? = null
                val contentsArray = JSONArray()

                messages.forEach { msg ->
                    val role = msg["role"] as? String ?: "user"
                    val text = msg["content"] as? String 
                        ?: (msg["parts"] as? List<Map<String, String>>)?.firstOrNull()?.get("text") 
                        ?: ""
                    
                    if (role.equals("system", ignoreCase = true)) {
                        systemInstructionText = text
                    } else {
                        val mappedRole = if (role == "assistant" || role == "model") "model" else "user"
                        val partsArray = JSONArray().put(JSONObject().put("text", text))
                        contentsArray.put(JSONObject().put("role", mappedRole).put("parts", partsArray))
                    }
                }

                if (!systemInstructionText.isNullOrBlank()) {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
                    })
                }

                // If contentsArray is empty, add a default greeting so Gemini never receives empty contents
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
        } else {
            val activeModel = currentProvider?.let { providerConfigs[it]?.let { cfg ->
                when (cfg) {
                    is ProviderConfig.Groq -> cfg.model
                    is ProviderConfig.Cerebras -> cfg.model
                    is ProviderConfig.AlphaOx -> cfg.model
                    is ProviderConfig.NvidiaNim -> cfg.model
                    is ProviderConfig.Mistral -> cfg.model
                    is ProviderConfig.OpenRouter -> cfg.model
                    is ProviderConfig.GitHubModels -> cfg.model
                    is ProviderConfig.Zhipu -> cfg.model
                    is ProviderConfig.HuggingFace -> cfg.model
                    is ProviderConfig.Cloudflare -> cfg.model
                    is ProviderConfig.Cohere -> cfg.model
                    is ProviderConfig.Ollama -> cfg.model
                    is ProviderConfig.Pollinations -> cfg.model
                    is ProviderConfig.Kilo -> cfg.model
                    is ProviderConfig.Ovh -> cfg.model
                    is ProviderConfig.AiHorde -> cfg.model
                    is ProviderConfig.Llm7 -> cfg.model
                    is ProviderConfig.Agnes -> cfg.model
                    is ProviderConfig.Routeway -> cfg.model
                    is ProviderConfig.Sail -> cfg.model
                    is ProviderConfig.Radeon -> cfg.model
                    is ProviderConfig.ModelScope -> cfg.model
                    is ProviderConfig.Custom -> cfg.model
                    else -> null
                }
            }} ?: when {
                apiEndpoint.contains("groq.com") -> "llama-3.3-70b-versatile"
                apiEndpoint.contains("cerebras.ai") -> "llama3.1-70b"
                apiEndpoint.contains("oxalpha.io") -> "ox-alpha"
                apiEndpoint.contains("integrate.api.nvidia.com") -> "meta/llama-3.3-70b-instruct"
                apiEndpoint.contains("mistral.ai") -> "mistral-large-latest"
                apiEndpoint.contains("openrouter.ai") -> "deepseek/deepseek-v3.1:free"
                apiEndpoint.contains("models.inference.ai.azure.com") -> "openai/gpt-5"
                apiEndpoint.contains("bigmodel.cn") -> "glm-4.5-flash"
                apiEndpoint.contains("router.huggingface.co") -> "accounts/fireworks/models/llama-v3p3-70b-instruct"
                apiEndpoint.contains("gen.pollinations.ai") -> "openai"
                apiEndpoint.contains("kilo.ai") -> "kilo-auto:free"
                apiEndpoint.contains("endpoints.kepler.ai.cloud.ovh.net") -> "gpt-oss-120b"
                apiEndpoint.contains("llm7.io") -> "gpt-oss"
                apiEndpoint.contains("apihub.agnes-ai.com") -> "agnes-2.0-flash"
                apiEndpoint.contains("routeway.ai") -> "auto:free"
                else -> "llama-3.3-70b-versatile"
            }

            // Standard OpenAI-compatible format
            JSONObject().apply {
                put("model", activeModel)
                put("messages", messagesToJsonArray(messages))
                put("stream", true)
                put("temperature", 0.7)
            }
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
            if (apiKey.isNotBlank() && apiKey != "free" && apiKey != "0000000000" && !apiEndpoint.contains("kilo.ai") && !apiEndpoint.contains("ovh.net")) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            requestBuilder.addHeader("Content-Type", "application/json")
        }

        val request = requestBuilder.post(RequestBody.create(MEDIA_TYPE_JSON, json.toString())).build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Stream failed", e)
                connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: response.message
                        Log.e(TAG, "API Error: ${response.code} - $errorBody")

                        // Self-healing: if Gemini model was retired/deprecated (404), auto-migrate to latest recommended model once
                        if (response.code == 404 && isGemini) {
                            val currentRequestedModel = apiEndpoint.substringAfter("models/").substringBefore(":stream")
                            val matches = Regex("models/([a-zA-Z0-9.-]+)").findAll(errorBody).map { it.groupValues[1] }.toList()
                            val recommended = matches.lastOrNull { it != currentRequestedModel && !it.contains("2.5") }
                                ?: if (currentRequestedModel != "gemini-3.6-flash") "gemini-3.6-flash" else "gemini-2.0-flash"

                            if (recommended != currentRequestedModel) {
                                Log.i(TAG, "Self-healing: Auto-migrating Gemini from $currentRequestedModel to $recommended")
                                setProviderModel(ProviderType.GEMINI, recommended)
                                val newEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/$recommended:streamGenerateContent?alt=sse"
                                streamCompletion(newEndpoint, apiKey, messages, onChunk, onError)
                                return
                            }
                        }

                        connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
                        onError(IOException("HTTP ${response.code}: $errorBody"))
                        return
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
