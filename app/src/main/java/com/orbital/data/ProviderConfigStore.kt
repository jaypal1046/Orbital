package com.orbital.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Handles in-memory & encrypted persistence of user API keys, provider configurations,
 * and custom selected models via SecureStorage.
 */
class ProviderConfigStore(
    private val secureStorage: SecureStorage? = null
) {
    private val providerConfigs = ConcurrentHashMap<ProviderType, ProviderConfig>()
    private val providerStatus = ConcurrentHashMap<ProviderType, ProviderState>()

    init {
        // Initialize provider statuses
        ProviderType.values().forEach { type ->
            providerStatus[type] = ProviderState.AVAILABLE
        }
        // Load persisted keys and states from secure storage
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

    fun configureProviders(configs: Map<ProviderType, ProviderConfig>) {
        providerConfigs.putAll(configs)
    }

    fun getProviderConfig(type: ProviderType): ProviderConfig? {
        return providerConfigs[type]
    }

    fun setProviderConfig(type: ProviderType, config: ProviderConfig) {
        providerConfigs[type] = config
    }

    fun getProviderStatus(type: ProviderType): ProviderState {
        return providerStatus[type] ?: ProviderState.UNAVAILABLE
    }

    fun setProviderStatus(type: ProviderType, state: ProviderState) {
        providerStatus[type] = state
    }

    fun resetStatuses() {
        providerStatus.clear()
        ProviderType.values().forEach { type ->
            providerStatus[type] = ProviderState.AVAILABLE
        }
    }

    fun isProviderEnabled(type: ProviderType): Boolean {
        return secureStorage?.isProviderEnabled(type.name) ?: true
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
            null -> if (isKeylessProvider(type)) "free" else ""
        }.trim()
    }

    fun updateProviderKey(type: ProviderType, key: String) {
        val cleanKey = key.trim()
        val savedModel = secureStorage?.getProviderSelectedModel(type.name)
        val defaultModel = savedModel ?: ProviderRegistry.getInfo(type).defaultModel

        val newConfig = createProviderConfig(type, cleanKey, defaultModel)
        providerConfigs[type] = newConfig
        providerStatus[type] = if (cleanKey.isNotBlank() || isKeylessProvider(type)) {
            ProviderState.AVAILABLE
        } else {
            ProviderState.UNAVAILABLE
        }
    }

    fun setProviderModel(type: ProviderType, model: String) {
        val key = getProviderKey(type)
        val newConfig = createProviderConfig(type, key, model)
        providerConfigs[type] = newConfig
        secureStorage?.saveProviderSelectedModel(type.name, model)
    }

    fun getSelectedModel(type: ProviderType): String {
        return secureStorage?.getProviderSelectedModel(type.name)
            ?: ProviderRegistry.getInfo(type).defaultModel
    }

    fun getEndpointFor(type: ProviderType): String {
        return when (val config = providerConfigs[type]) {
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
            null -> ProviderRegistry.getInfo(type).endpoint
        }
    }

    fun getActiveModelFor(type: ProviderType): String {
        val config = providerConfigs[type]
        return when (config) {
            is ProviderConfig.Groq -> config.model
            is ProviderConfig.Cerebras -> config.model
            is ProviderConfig.AlphaOx -> config.model
            is ProviderConfig.Gemini -> config.model
            is ProviderConfig.NvidiaNim -> config.model
            is ProviderConfig.Mistral -> config.model
            is ProviderConfig.OpenRouter -> config.model
            is ProviderConfig.GitHubModels -> config.model
            is ProviderConfig.Zhipu -> config.model
            is ProviderConfig.HuggingFace -> config.model
            is ProviderConfig.Cloudflare -> config.model
            is ProviderConfig.Cohere -> config.model
            is ProviderConfig.Ollama -> config.model
            is ProviderConfig.Pollinations -> config.model
            is ProviderConfig.Kilo -> config.model
            is ProviderConfig.Ovh -> config.model
            is ProviderConfig.AiHorde -> config.model
            is ProviderConfig.Llm7 -> config.model
            is ProviderConfig.Agnes -> config.model
            is ProviderConfig.Routeway -> config.model
            is ProviderConfig.Sail -> config.model
            is ProviderConfig.Radeon -> config.model
            is ProviderConfig.ModelScope -> config.model
            is ProviderConfig.Custom -> config.model
            null -> ProviderRegistry.getInfo(type).defaultModel
        }
    }

    fun isKeylessProvider(type: ProviderType): Boolean {
        return type == ProviderType.KILO ||
                type == ProviderType.OVH ||
                type == ProviderType.POLLINATIONS ||
                type == ProviderType.AIHORDE
    }

    fun allConfiguredTypes(): Set<ProviderType> = providerConfigs.keys

    private fun createProviderConfig(type: ProviderType, key: String, model: String): ProviderConfig {
        return when (type) {
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
    }
}
