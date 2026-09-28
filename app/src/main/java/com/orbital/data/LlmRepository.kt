package com.orbital.data

import android.util.Log
import com.orbital.overlay.ConnectionStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Slim, decoupled facade coordinating ProviderConfigStore, ModelRouter,
 * StreamingClient, and ModelDiscovery.
 *
 * Keeps full backward compatibility with all UI screens, services,
 * and the embedded Ktor /v1/chat/completions endpoint.
 */
open class LlmRepository(
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
    }

    // Modular sub-components
    val configStore = ProviderConfigStore(secureStorage)
    val modelRouter = ModelRouter()
    val streamingClient = StreamingClient(okHttpClient)
    val modelDiscovery = ModelDiscovery(okHttpClient)

    // Server & routing configurations
    private var serverConfig = ServerConfig()
    private var routerConfig = RouterConfig()

    // --- Provider Configuration & Storage Delegation ---

    open fun configureProviders(configs: Map<ProviderType, ProviderConfig>) {
        configStore.configureProviders(configs)
    }

    open fun updateServerConfig(config: ServerConfig) {
        this.serverConfig = config
    }

    open fun updateRouterConfig(config: RouterConfig) {
        this.routerConfig = config
    }

    open fun getProviderKey(type: ProviderType): String {
        return configStore.getProviderKey(type)
    }

    open fun updateProviderKey(type: ProviderType, key: String) {
        configStore.updateProviderKey(type, key)
    }

    open fun setProviderModel(type: ProviderType, model: String) {
        configStore.setProviderModel(type, model)
    }

    open fun getCurrentProviderConfig(): ProviderConfig? {
        val current = getCurrentProviderType()
            ?: modelRouter.getNextAvailableProvider(configStore, routerConfig)
            ?: configStore.allConfiguredTypes().firstOrNull { type ->
                getProviderKey(type).isNotBlank() || configStore.isKeylessProvider(type)
            }
            ?: configStore.allConfiguredTypes().firstOrNull()

        current?.let { modelRouter.setCurrentProvider(it) }
        return current?.let { configStore.getProviderConfig(it) }
    }

    open fun getCurrentProviderType(): ProviderType? {
        return modelRouter.getCurrentProvider()
    }

    open fun setCurrentProvider(type: ProviderType) {
        modelRouter.setCurrentProvider(type)
    }

    open fun getProviderStatus(type: ProviderType): ProviderState {
        return configStore.getProviderStatus(type)
    }

    open fun isProviderAvailable(type: ProviderType): Boolean {
        return configStore.getProviderStatus(type) == ProviderState.AVAILABLE
    }

    open fun resetProviders() {
        configStore.resetStatuses()
        modelRouter.resetCooldowns()
    }

    // --- Routing & Tier Management Delegation ---

    open fun getRoutingMode(): RoutingMode = modelRouter.getRoutingMode()

    open fun setRoutingMode(mode: RoutingMode) {
        modelRouter.setRoutingMode(mode)
    }

    open fun getNextAvailableProvider(): ProviderType? {
        return modelRouter.getNextAvailableProvider(configStore, routerConfig)
    }

    // --- Dynamic Model Discovery Delegation ---

    fun getAvailableModels(type: ProviderType): List<String> {
        return modelDiscovery.getAvailableModels(type)
    }

    fun getAvailableCatalogModels(type: ProviderType): List<CatalogModel> {
        return modelDiscovery.getAvailableCatalogModels(type)
    }

    fun discoverModels(type: ProviderType, key: String, onResult: (List<String>) -> Unit) {
        modelDiscovery.discoverModels(type, key, onResult)
    }

    fun discoverCatalogModels(type: ProviderType, key: String, onResult: (List<CatalogModel>) -> Unit) {
        modelDiscovery.discoverCatalogModels(type, key, onResult)
    }

    fun setConnectionStatusCallback(callback: ((ConnectionStatus) -> Unit)?) {
        streamingClient.setConnectionStatusCallback(callback)
    }

    // --- Test Provider & Health Pings ---

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
                    configStore.setProviderStatus(type, ProviderState.UNAVAILABLE)
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
                                setProviderModel(type, chosenModel)
                            }
                            configStore.setProviderStatus(type, ProviderState.AVAILABLE)
                            onResult?.invoke(true, null)
                        } else {
                            val errBody = response.body?.string() ?: response.message
                            configStore.setProviderStatus(type, ProviderState.UNAVAILABLE)
                            onResult?.invoke(false, "HTTP ${response.code}: $errBody")
                        }
                    } catch (e: Exception) {
                        configStore.setProviderStatus(type, ProviderState.UNAVAILABLE)
                        onResult?.invoke(false, e.message)
                    } finally {
                        response.close()
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
                configStore.setProviderStatus(type, ProviderState.AVAILABLE)
                onResult?.invoke(true, null)
            },
            onError = { error ->
                configStore.setProviderStatus(type, ProviderState.UNAVAILABLE)
                onResult?.invoke(false, error.message)
            }
        )
    }

    // --- Core High-Level Chat Streaming ---

    open fun streamCompletion(
        model: String,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit
    ) {
        val activeType = modelRouter.selectActiveProvider(configStore, routerConfig)
        val key = configStore.getProviderKey(activeType)
        val isKeyless = configStore.isKeylessProvider(activeType)

        if (key.isBlank() && !isKeyless && activeType != ProviderType.CUSTOM) {
            val configured = ProviderRegistry.allProviders.firstOrNull { configStore.getProviderKey(it.type).isNotBlank() }
            if (configured != null) {
                modelRouter.setCurrentProvider(configured.type)
                streamCompletion(model, messages, onChunk, onComplete, onError)
                return
            }
            onError(IOException("No API Key entered for ${activeType.name}. Please configure your API key in settings."))
            return
        }

        val endpoint = configStore.getEndpointFor(activeType)
        val activeModel = configStore.getActiveModelFor(activeType)
        val mappedMessages = messages.map { msg ->
            mapOf("role" to msg.role, "content" to (msg.content ?: ""))
        }

        streamingClient.streamCompletion(
            apiEndpoint = endpoint,
            apiKey = key,
            activeModel = activeModel,
            messages = mappedMessages,
            onChunk = onChunk,
            onComplete = onComplete,
            onError = { error ->
                val errMsg = error.message ?: ""
                val is503Overload = errMsg.contains("503") || errMsg.contains("high demand", ignoreCase = true)

                // Self-healing: If Groq 70B encounters a 503 high-demand spike, auto-failover to ultra-fast 8B model
                if (is503Overload && activeType == ProviderType.GROQ && !activeModel.contains("8b")) {
                    Log.i(TAG, "Self-healing: Groq model high demand (503), auto-failing over to llama-3.1-8b-instant")
                    setProviderModel(ProviderType.GROQ, "llama-3.1-8b-instant")
                    streamCompletion(model, messages, onChunk, onComplete, onError)
                    return@streamCompletion
                }

                modelRouter.handleProviderError(activeType, error, configStore)
                val next = modelRouter.getNextAvailableProvider(configStore, routerConfig)
                if (next != null && next != activeType) {
                    Log.i(TAG, "Silent Auto-Failover: Provider $activeType encountered error ($errMsg). Failing over to: $next")
                    modelRouter.setCurrentProvider(next)
                    streamCompletion(model, messages, onChunk, onComplete, onError)
                } else {
                    val friendlyMsg = when {
                        is503Overload -> "The AI model is experiencing temporary high demand (503). Please retry in a few seconds or switch models in Settings."
                        errMsg.contains("429") || errMsg.contains("quota", ignoreCase = true) || errMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                            "All configured AI providers are temporarily rate-limited. Please wait a moment or configure backup keys."
                        else -> "AI request failed: ${errMsg.take(160)}"
                    }
                    onError(IOException(friendlyMsg))
                }
            },
            onGeminiModelMigration = { newModel, _ ->
                setProviderModel(ProviderType.GEMINI, newModel)
                streamCompletion(model, messages, onChunk, onComplete, onError)
            }
        )
    }

    /**
     * Backward-compatible 4-parameter overload
     */
    open fun streamCompletion(
        model: String,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        streamCompletion(model, messages, onChunk, {}, onError)
    }

    /**
     * Direct streaming overload used by the embedded Ktor server on :3001
     * and external completions requests.
     */
    fun streamCompletion(
        apiEndpoint: String,
        apiKey: String,
        messages: List<Map<String, Any>>,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit
    ) {
        val activeModel = getCurrentProviderType()?.let { configStore.getActiveModelFor(it) }
            ?: "llama-3.3-70b-versatile"

        streamingClient.streamCompletion(
            apiEndpoint = apiEndpoint,
            apiKey = apiKey,
            activeModel = activeModel,
            messages = messages,
            onChunk = onChunk,
            onComplete = onComplete,
            onError = onError
        )
    }

    fun streamCompletion(
        apiEndpoint: String,
        apiKey: String,
        messages: List<Map<String, Any>>,
        onChunk: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        streamCompletion(apiEndpoint, apiKey, messages, onChunk, {}, onError)
    }
}
