package com.orbital.data

import kotlinx.serialization.Serializable

/**
 * LLM Provider configuration and routing data structures
 */
@Serializable
sealed class ProviderConfig {
    @Serializable
    data class Groq(
        val apiKey: String,
        val model: String = "llama-3.3-70b-versatile",
        val baseUrl: String = "https://api.groq.com/openai/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Gemini(
        val apiKey: String,
        val model: String = "gemini-3.6-flash",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:streamGenerateContent?alt=sse"
    ) : ProviderConfig()

    @Serializable
    data class Mistral(
        val apiKey: String,
        val model: String = "mistral-large-latest",
        val baseUrl: String = "https://api.mistral.ai/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class OpenRouter(
        val apiKey: String,
        val model: String = "deepseek/deepseek-v3.1:free",
        val baseUrl: String = "https://openrouter.ai/api/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Cerebras(
        val apiKey: String,
        val model: String = "llama3.1-70b",
        val baseUrl: String = "https://api.cerebras.ai/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class NvidiaNim(
        val apiKey: String,
        val model: String = "meta/llama-3.3-70b-instruct",
        val baseUrl: String = "https://integrate.api.nvidia.com/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class GitHubModels(
        val apiKey: String,
        val model: String = "openai/gpt-5",
        val baseUrl: String = "https://models.inference.ai.azure.com/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class AlphaOx(
        val apiKey: String,
        val model: String = "ox-alpha",
        val baseUrl: String = "https://api.oxalpha.io/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Zhipu(
        val apiKey: String,
        val model: String = "glm-4.5-flash",
        val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class HuggingFace(
        val apiKey: String,
        val model: String = "accounts/fireworks/models/llama-v3p3-70b-instruct",
        val baseUrl: String = "https://router.huggingface.co/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Cloudflare(
        val apiKey: String,
        val model: String = "@cf/meta/llama-3.1-70b-instruct",
        val baseUrl: String = "https://api.cloudflare.com/client/v4/accounts"
    ) : ProviderConfig()

    @Serializable
    data class Cohere(
        val apiKey: String,
        val model: String = "command-r-plus-08-2024",
        val baseUrl: String = "https://api.cohere.com/v2/chat"
    ) : ProviderConfig()

    @Serializable
    data class Ollama(
        val apiKey: String = "",
        val model: String = "llama3",
        val baseUrl: String = "http://127.0.0.1:11434/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Pollinations(
        val apiKey: String = "free",
        val model: String = "openai",
        val baseUrl: String = "https://text.pollinations.ai/openai/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Kilo(
        val apiKey: String = "free",
        val model: String = "kilo-auto:free",
        val baseUrl: String = "https://api.kilo.ai/api/gateway/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Ovh(
        val apiKey: String = "free",
        val model: String = "gpt-oss-120b",
        val baseUrl: String = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class AiHorde(
        val apiKey: String = "0000000000",
        val model: String = "aphrodite/llama-3-8b",
        val baseUrl: String = "https://aihorde.net/api/v2/generate/text/async"
    ) : ProviderConfig()

    @Serializable
    data class Llm7(
        val apiKey: String = "",
        val model: String = "gpt-oss",
        val baseUrl: String = "https://api.llm7.io/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Agnes(
        val apiKey: String,
        val model: String = "agnes-2.0-flash",
        val baseUrl: String = "https://apihub.agnes-ai.com/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Routeway(
        val apiKey: String,
        val model: String = "auto:free",
        val baseUrl: String = "https://api.routeway.ai/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Sail(
        val apiKey: String,
        val model: String = "sail-frontier",
        val baseUrl: String = "https://api.sailresearch.com/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Radeon(
        val apiKey: String,
        val model: String = "radeon-llama-3.3-70b",
        val baseUrl: String = "https://developer.amd.com.cn/radeon/api/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class ModelScope(
        val apiKey: String,
        val model: String = "qwen/Qwen2.5-72B-Instruct",
        val baseUrl: String = "https://api-inference.modelscope.cn/v1/chat/completions"
    ) : ProviderConfig()

    @Serializable
    data class Custom(
        val apiKey: String,
        val model: String,
        val baseUrl: String
    ) : ProviderConfig()

    companion object {
        val defaultProviderOrder = listOf(
            ProviderType.GEMINI,
            ProviderType.GROQ,
            ProviderType.CEREBRAS,
            ProviderType.POLLINATIONS,
            ProviderType.ALPHAOX,
            ProviderType.NVIDIA_NIM,
            ProviderType.MISTRAL,
            ProviderType.OPENROUTER,
            ProviderType.GITHUB_MODELS,
            ProviderType.ZHIPU,
            ProviderType.HUGGINGFACE,
            ProviderType.CLOUDFLARE,
            ProviderType.COHERE,
            ProviderType.OLLAMA,
            ProviderType.KILO,
            ProviderType.OVH,
            ProviderType.LLM7,
            ProviderType.AGNES,
            ProviderType.ROUTEWAY,
            ProviderType.SAIL,
            ProviderType.RADEON,
            ProviderType.MODELSCOPE
        )

        val fastTierProviders = listOf(
            ProviderType.CEREBRAS,
            ProviderType.GROQ,
            ProviderType.GEMINI,
            ProviderType.ALPHAOX
        )

        val frontierTierProviders = listOf(
            ProviderType.GEMINI,
            ProviderType.NVIDIA_NIM,
            ProviderType.GITHUB_MODELS,
            ProviderType.OPENROUTER,
            ProviderType.MISTRAL,
            ProviderType.ZHIPU,
            ProviderType.HUGGINGFACE
        )
    }
}

enum class ProviderType {
    GROQ,
    GEMINI,
    CEREBRAS,
    ALPHAOX,
    NVIDIA_NIM,
    MISTRAL,
    OPENROUTER,
    GITHUB_MODELS,
    ZHIPU,
    HUGGINGFACE,
    CLOUDFLARE,
    COHERE,
    OLLAMA,
    POLLINATIONS,
    KILO,
    OVH,
    LLM7,
    AGNES,
    ROUTEWAY,
    SAIL,
    RADEON,
    MODELSCOPE,
    AIHORDE,
    CUSTOM
}

enum class RoutingMode(val displayName: String, val subtitle: String, val emoji: String) {
    AUTO("Auto Router", "Intelligent failover across all active providers", "⚡"),
    FAST("Fast Tier", "Ultra-low latency inference (Cerebras & Groq)", "🚀"),
    FRONTIER("Frontier Tier", "Highest reasoning capability (Gemini Pro & DeepSeek)", "🧠"),
    PINNED("Pinned Provider", "Route strictly to your selected provider", "🎯")
}

enum class ActionApprovalMode(val displayName: String, val subtitle: String, val emoji: String) {
    ALWAYS_PROCEED("Always Proceed", "Autonomous execution without confirmation prompts (Antigravity mode)", "⚡"),
    REQUEST_FOR_ACTION("Request for Action", "Always ask for confirmation before executing any action", "🛡️"),
    AUTO_SAFE("Smart Safe", "Auto-run safe search/screen actions; confirm sensitive actions (SMS/Calls)", "⚖️")
}

enum class ProviderState {
    AVAILABLE,
    UNAVAILABLE,
    IN_COOLDOWN
}

/**
 * Request/Response models for OpenAI-compatible API
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    val max_tokens: Int? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String?,
    val actionLabel: String? = null,
    val actionDetails: String? = null,
    val steps: List<com.orbital.action.ExecutionStep>? = null,
    val executionDurationMs: Long = 0L
) {
    // For Gemini format compatibility
    @Serializable
    @kotlinx.serialization.Transient
    val parts: List<ChatPart>? = null
}

@Serializable
data class ChatPart(
    val text: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val choices: List<ChatChoice>,
    val model: String
)

@Serializable
data class ChatChoice(
    val index: Int,
    val delta: ChatDelta?,
    val message: ChatMessage?,
    val finish_reason: String?
)

@Serializable
data class ChatDelta(
    val content: String?,
    val role: String?
)

/**
 * Error response from providers
 */
@Serializable
data class ProviderErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String?,
    val param: String?,
    val code: String?
)

/**
 * Router configuration
 */
data class RouterConfig(
    val enabledProviders: List<ProviderType> = ProviderConfig.defaultProviderOrder,
    val failoverEnabled: Boolean = true,
    val cooldownMs: Long = 60000,
    val timeoutMs: Long = 30000
)

/**
 * Server mode configuration
 */
enum class ServerMode {
    NATIVE_ROUTER,      // Primary: In-app Kotlin multi-provider router
    EMBEDDED_SERVER,    // Secondary: Ktor embedded HTTP server on port 3001
    EXTERNAL_SERVER     // Tertiary: Custom external server URL
}

data class ServerConfig(
    val mode: ServerMode = ServerMode.NATIVE_ROUTER,
    val embeddedServerPort: Int = 3001,
    val externalServerUrl: String? = null,
    val enableEmbeddedServer: Boolean = false
)