package com.orbital.data

/**
 * Metadata for each model in the catalog matching FreeLLMAPI specification.
 */
data class CatalogModel(
    val modelId: String,
    val displayName: String,
    val sizeLabel: String = "Large", // "Frontier", "Large", "Medium"
    val contextWindow: String = "128K ctx"
)

/**
 * Registry containing detailed metadata for all free LLM providers,
 * their free quotas, key portals, and model specifications.
 */
data class ProviderInfo(
    val type: ProviderType,
    val displayName: String,
    val catalogModels: List<CatalogModel>,
    val defaultModel: String,
    val quotaDescription: String,
    val portalUrl: String,
    val keyPlaceholder: String,
    val endpoint: String,
    val isGemini: Boolean = false
) {
    val models: List<String> get() = catalogModels.map { it.modelId }
}

object ProviderRegistry {

    val allProviders = listOf(
        ProviderInfo(
            type = ProviderType.GEMINI,
            displayName = "Google AI Studio",
            catalogModels = listOf(
                CatalogModel("gemini-3.6-flash", "Gemini 3.6 Flash", "Frontier", "1M ctx"),
                CatalogModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Large", "1M ctx"),
                CatalogModel("gemini-1.5-flash", "Gemini 1.5 Flash", "Large", "1M ctx"),
                CatalogModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", "Medium", "1M ctx"),
                CatalogModel("gemini-3.6-pro", "Gemini 3.6 Pro", "Frontier", "2M ctx"),
                CatalogModel("gemini-1.5-pro", "Gemini 1.5 Pro", "Frontier", "2M ctx"),
                CatalogModel("gemma-2-9b-it", "Gemma 2 9B IT", "Medium", "8K ctx"),
                CatalogModel("gemma-2-27b-it", "Gemma 2 27B IT", "Large", "8K ctx")
            ),
            defaultModel = "gemini-3.6-flash",
            quotaDescription = "15 RPM · 1,500 RPD · 1M TPM (Free)",
            portalUrl = "https://aistudio.google.com/app/apikey",
            keyPlaceholder = "AIzaSy...",
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:streamGenerateContent?alt=sse",
            isGemini = true
        ),
        ProviderInfo(
            type = ProviderType.GROQ,
            displayName = "Groq",
            catalogModels = listOf(
                CatalogModel("llama-3.3-70b-versatile", "Llama 3.3 70B", "Large", "128K ctx"),
                CatalogModel("llama-4-scout-17b-16e-instruct", "Llama 4 Scout", "Medium", "128K ctx"),
                CatalogModel("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill 70B", "Frontier", "128K ctx"),
                CatalogModel("qwen-2.5-coder-32b", "Qwen 2.5 Coder 32B", "Large", "32K ctx"),
                CatalogModel("llama-3.1-8b-instant", "Llama 3.1 8B Instant", "Medium", "128K ctx"),
                CatalogModel("mixtral-8x7b-32768", "Mixtral 8x7B", "Medium", "32K ctx"),
                CatalogModel("gemma2-9b-it", "Gemma 2 9B IT", "Medium", "8K ctx")
            ),
            defaultModel = "llama-3.3-70b-versatile",
            quotaDescription = "30 RPM · 14,400 RPD (Free & Fast)",
            portalUrl = "https://console.groq.com/keys",
            keyPlaceholder = "gsk_...",
            endpoint = "https://api.groq.com/openai/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.CEREBRAS,
            displayName = "Cerebras",
            catalogModels = listOf(
                CatalogModel("llama3.1-70b", "Llama 3.1 70B", "Large", "128K ctx"),
                CatalogModel("llama-3.3-70b", "Llama 3.3 70B", "Large", "128K ctx"),
                CatalogModel("qwen-3-coder-480b", "Qwen3 Coder 480B", "Frontier", "131K ctx"),
                CatalogModel("llama-4-maverick-17b-128e-instruct", "Llama 4 Maverick", "Frontier", "131K ctx"),
                CatalogModel("qwen3-235b", "Qwen3 235B", "Large", "8K ctx"),
                CatalogModel("gpt-oss-120b", "GPT-OSS 120B", "Large", "131K ctx"),
                CatalogModel("llama3.1-8b", "Llama 3.1 8B", "Medium", "8K ctx")
            ),
            defaultModel = "llama3.1-70b",
            quotaDescription = "30 RPM · 1,800+ tokens/sec (Ultra Fast)",
            portalUrl = "https://cloud.cerebras.ai",
            keyPlaceholder = "csk-...",
            endpoint = "https://api.cerebras.ai/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.ALPHAOX,
            displayName = "AlphaOx (Ox Alpha)",
            catalogModels = listOf(
                CatalogModel("ox-alpha", "AlphaOx 1M Standard", "Frontier", "1M ctx"),
                CatalogModel("stealth/ox-alpha", "AlphaOx Stealth 1M", "Frontier", "1M ctx"),
                CatalogModel("glm-5.3-flash", "GLM 5.3 Flash", "Large", "128K ctx"),
                CatalogModel("glm-4-flash", "GLM 4 Flash", "Medium", "128K ctx")
            ),
            defaultModel = "ox-alpha",
            quotaDescription = "1M Token Context Window (Multimodal & Fast)",
            portalUrl = "https://oxalpha.io",
            keyPlaceholder = "ox-...",
            endpoint = "https://api.oxalpha.io/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.NVIDIA_NIM,
            displayName = "NVIDIA NIM",
            catalogModels = listOf(
                CatalogModel("meta/llama-3.3-70b-instruct", "Llama 3.3 70B (NV)", "Large", "128K ctx"),
                CatalogModel("meta/llama-3.1-70b-instruct", "Llama 3.1 70B (NV)", "Large", "128K ctx"),
                CatalogModel("deepseek-ai/deepseek-r1", "DeepSeek R1 (NV)", "Frontier", "128K ctx"),
                CatalogModel("nvidia/nemotron-4-340b-instruct", "Nemotron-4 340B", "Frontier", "128K ctx"),
                CatalogModel("mistralai/mistral-large-2-instruct", "Mistral Large 2 (NV)", "Large", "128K ctx")
            ),
            defaultModel = "meta/llama-3.3-70b-instruct",
            quotaDescription = "1,000 Free Credits · GPU Accelerated",
            portalUrl = "https://build.nvidia.com",
            keyPlaceholder = "nvapi-...",
            endpoint = "https://integrate.api.nvidia.com/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.MISTRAL,
            displayName = "Mistral AI",
            catalogModels = listOf(
                CatalogModel("mistral-large-latest", "Mistral Large 3", "Frontier", "128K ctx"),
                CatalogModel("magistral-medium-latest", "Magistral Medium", "Large", "40K ctx"),
                CatalogModel("codestral-latest", "Codestral", "Large", "32K ctx"),
                CatalogModel("mistral-small-latest", "Mistral Small", "Medium", "32K ctx"),
                CatalogModel("open-mistral-nemo", "Mistral Nemo", "Medium", "128K ctx")
            ),
            defaultModel = "mistral-large-latest",
            quotaDescription = "Free Tier · High Quality Models",
            portalUrl = "https://console.mistral.ai/api-keys",
            keyPlaceholder = "sk_...",
            endpoint = "https://api.mistral.ai/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.OPENROUTER,
            displayName = "OpenRouter",
            catalogModels = listOf(
                CatalogModel("deepseek/deepseek-v3.1:free", "DeepSeek V3.1 (Free)", "Frontier", "131K ctx"),
                CatalogModel("moonshotai/kimi-k2:free", "Kimi K2 (Free)", "Frontier", "131K ctx"),
                CatalogModel("qwen/qwen3-coder:free", "Qwen3 Coder (Free)", "Frontier", "256K ctx"),
                CatalogModel("z-ai/glm-4.5-air:free", "GLM 4.5 Air (Free)", "Large", "131K ctx"),
                CatalogModel("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", "Large", "128K ctx"),
                CatalogModel("deepseek/deepseek-r1:free", "DeepSeek R1 (Free)", "Frontier", "128K ctx")
            ),
            defaultModel = "deepseek/deepseek-v3.1:free",
            quotaDescription = "Multi-model aggregated free tier",
            portalUrl = "https://openrouter.ai/keys",
            keyPlaceholder = "sk-or-...",
            endpoint = "https://openrouter.ai/api/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.GITHUB_MODELS,
            displayName = "GitHub Models",
            catalogModels = listOf(
                CatalogModel("openai/gpt-5", "GPT-5 (GitHub)", "Frontier", "128K ctx"),
                CatalogModel("gpt-4o", "GPT-4o (GitHub)", "Frontier", "128K ctx"),
                CatalogModel("gpt-4o-mini", "GPT-4o Mini", "Medium", "128K ctx"),
                CatalogModel("o1-mini", "o1 Mini", "Frontier", "128K ctx"),
                CatalogModel("o3-mini", "o3 Mini", "Frontier", "200K ctx"),
                CatalogModel("meta-llama-3.1-70b-instruct", "Llama 3.1 70B", "Large", "128K ctx"),
                CatalogModel("Phi-3.5-mini-instruct", "Phi-3.5 Mini", "Medium", "128K ctx")
            ),
            defaultModel = "openai/gpt-5",
            quotaDescription = "Free with GitHub Personal Access Token",
            portalUrl = "https://github.com/settings/tokens",
            keyPlaceholder = "ghp_...",
            endpoint = "https://models.inference.ai.azure.com/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.ZHIPU,
            displayName = "Zhipu AI (Z.ai)",
            catalogModels = listOf(
                CatalogModel("glm-4.5-flash", "GLM 4.5 Flash", "Frontier", "128K ctx"),
                CatalogModel("glm-4-flash", "GLM 4 Flash", "Medium", "128K ctx"),
                CatalogModel("glm-4-plus", "GLM 4 Plus", "Frontier", "128K ctx"),
                CatalogModel("glm-4-air", "GLM 4 Air", "Large", "128K ctx"),
                CatalogModel("glm-4-long", "GLM 4 Long", "Frontier", "1M ctx")
            ),
            defaultModel = "glm-4.5-flash",
            quotaDescription = "Free Tier · 20 RPM · BigModel / Z.ai",
            portalUrl = "https://z.ai/manage-apikey/apikey-list",
            keyPlaceholder = "zhipu_...",
            endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.HUGGINGFACE,
            displayName = "HuggingFace Router",
            catalogModels = listOf(
                CatalogModel("accounts/fireworks/models/llama-v3p3-70b-instruct", "Llama 3.3 70B (Fireworks)", "Large", "128K ctx"),
                CatalogModel("meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B", "Large", "128K ctx"),
                CatalogModel("deepseek-ai/DeepSeek-R1", "DeepSeek R1 (HF)", "Frontier", "128K ctx"),
                CatalogModel("Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B", "Large", "32K ctx")
            ),
            defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
            quotaDescription = "Free router credits · High concurrency",
            portalUrl = "https://huggingface.co/settings/tokens",
            keyPlaceholder = "hf_...",
            endpoint = "https://router.huggingface.co/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.CLOUDFLARE,
            displayName = "Cloudflare Workers AI",
            catalogModels = listOf(
                CatalogModel("@cf/meta/llama-3.1-70b-instruct", "Llama 3.1 70B (CF)", "Large", "128K ctx"),
                CatalogModel("@cf/meta/llama-3.3-70b-instruct", "Llama 3.3 70B (CF)", "Large", "128K ctx"),
                CatalogModel("@cf/deepseek-ai/deepseek-r1-distill-qwen-32b", "DeepSeek R1 32B (CF)", "Large", "32K ctx"),
                CatalogModel("@cf/meta/llama-3.1-8b-instruct", "Llama 3.1 8B (CF)", "Medium", "128K ctx")
            ),
            defaultModel = "@cf/meta/llama-3.1-70b-instruct",
            quotaDescription = "10,000 Neurons / Day Free · Edge hosted",
            portalUrl = "https://dash.cloudflare.com",
            keyPlaceholder = "account_id:api_token",
            endpoint = "https://api.cloudflare.com/client/v4/accounts"
        ),
        ProviderInfo(
            type = ProviderType.COHERE,
            displayName = "Cohere",
            catalogModels = listOf(
                CatalogModel("command-r-plus-08-2024", "Command R+ (08-2024)", "Frontier", "128K ctx"),
                CatalogModel("command-r-08-2024", "Command R (08-2024)", "Large", "128K ctx"),
                CatalogModel("command-light", "Command Light", "Medium", "4K ctx")
            ),
            defaultModel = "command-r-plus-08-2024",
            quotaDescription = "Trial Tier · Advanced tool use & reasoning",
            portalUrl = "https://dashboard.cohere.com/api-keys",
            keyPlaceholder = "cohere_...",
            endpoint = "https://api.cohere.com/v2/chat"
        ),
        ProviderInfo(
            type = ProviderType.OLLAMA,
            displayName = "Ollama Cloud / Local",
            catalogModels = listOf(
                CatalogModel("llama3.3:70b", "Llama 3.3 70B (Ollama)", "Large", "128K ctx"),
                CatalogModel("qwen2.5-coder:32b", "Qwen 2.5 Coder 32B", "Large", "32K ctx"),
                CatalogModel("deepseek-r1:70b", "DeepSeek R1 70B (Ollama)", "Frontier", "128K ctx"),
                CatalogModel("mistral:7b", "Mistral 7B (Ollama)", "Medium", "32K ctx")
            ),
            defaultModel = "llama3.3:70b",
            quotaDescription = "Local / Cloud Session · Zero rate limits",
            portalUrl = "https://ollama.com/settings/keys",
            keyPlaceholder = "ollama_key or local",
            endpoint = "https://ollama.com/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.POLLINATIONS,
            displayName = "Pollinations",
            catalogModels = listOf(
                CatalogModel("openai", "OpenAI Standard", "Large", "128K ctx"),
                CatalogModel("mistral", "Mistral Large", "Large", "128K ctx"),
                CatalogModel("qwen", "Qwen 2.5", "Large", "128K ctx"),
                CatalogModel("claude-hybridspace", "Claude Hybrid", "Frontier", "128K ctx")
            ),
            defaultModel = "openai",
            quotaDescription = "Free Pollen Capacity · Multi-modal",
            portalUrl = "https://enter.pollinations.ai",
            keyPlaceholder = "free (keyless or token)",
            endpoint = "https://text.pollinations.ai/openai/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.KILO,
            displayName = "Kilo Gateway",
            catalogModels = listOf(
                CatalogModel("kilo-auto:free", "Kilo Auto (Free)", "Frontier", "128K ctx"),
                CatalogModel("deepseek-r1:free", "DeepSeek R1 (Kilo)", "Frontier", "128K ctx"),
                CatalogModel("llama-3.3-70b:free", "Llama 3.3 70B (Kilo)", "Large", "128K ctx")
            ),
            defaultModel = "kilo-auto:free",
            quotaDescription = "200 req/hr per IP · Anonymous keyless access",
            portalUrl = "https://app.kilo.ai",
            keyPlaceholder = "free (no key needed)",
            endpoint = "https://api.kilo.ai/api/gateway/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.OVH,
            displayName = "OVH AI Endpoints",
            catalogModels = listOf(
                CatalogModel("gpt-oss-120b", "GPT-OSS 120B (OVH)", "Large", "131K ctx"),
                CatalogModel("Meta-Llama-3_3-70B-Instruct", "Llama 3.3 70B (OVH)", "Large", "128K ctx")
            ),
            defaultModel = "gpt-oss-120b",
            quotaDescription = "Public Cloud Free Tier · Anonymous keyless",
            portalUrl = "https://endpoints.ai.cloud.ovh.net",
            keyPlaceholder = "free (no key needed)",
            endpoint = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.LLM7,
            displayName = "LLM7",
            catalogModels = listOf(
                CatalogModel("gpt-oss", "GPT-OSS (LLM7)", "Large", "128K ctx"),
                CatalogModel("llama-3.1-turbo", "Llama 3.1 Turbo", "Medium", "128K ctx"),
                CatalogModel("codestral", "Codestral (LLM7)", "Large", "32K ctx")
            ),
            defaultModel = "gpt-oss",
            quotaDescription = "100 req/hr Free · Anonymous supported",
            portalUrl = "https://llm7.io",
            keyPlaceholder = "llm7_...",
            endpoint = "https://api.llm7.io/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.AGNES,
            displayName = "Agnes AI",
            catalogModels = listOf(
                CatalogModel("agnes-2.0-flash", "Agnes 2.0 Flash", "Frontier", "128K ctx"),
                CatalogModel("agnes-1.5-pro", "Agnes 1.5 Pro", "Large", "128K ctx")
            ),
            defaultModel = "agnes-2.0-flash",
            quotaDescription = "Free Tier · LiteLLM/vLLM backend",
            portalUrl = "https://platform.agnes-ai.com",
            keyPlaceholder = "agnes_...",
            endpoint = "https://apihub.agnes-ai.com/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.ROUTEWAY,
            displayName = "Routeway",
            catalogModels = listOf(
                CatalogModel("auto:free", "Routeway Auto (Free)", "Frontier", "128K ctx"),
                CatalogModel("deepseek-v3:free", "DeepSeek V3 (Routeway)", "Frontier", "128K ctx"),
                CatalogModel("llama-3.3-70b:free", "Llama 3.3 70B (Routeway)", "Large", "128K ctx")
            ),
            defaultModel = "auto:free",
            quotaDescription = "Free Tier · Aggregated model pool",
            portalUrl = "https://routeway.ai",
            keyPlaceholder = "rw_...",
            endpoint = "https://api.routeway.ai/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.SAIL,
            displayName = "Sail Research",
            catalogModels = listOf(
                CatalogModel("sail-frontier", "Sail Frontier 1M", "Frontier", "1M ctx"),
                CatalogModel("sail-flash", "Sail Flash 128K", "Large", "128K ctx")
            ),
            defaultModel = "sail-frontier",
            quotaDescription = "$5 Monthly Free Credit · Responses API",
            portalUrl = "https://app.sailresearch.com",
            keyPlaceholder = "sail_...",
            endpoint = "https://api.sailresearch.com/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.RADEON,
            displayName = "AMD Radeon Cloud",
            catalogModels = listOf(
                CatalogModel("radeon-llama-3.3-70b", "Radeon Llama 3.3 70B", "Large", "128K ctx"),
                CatalogModel("radeon-qwen-2.5-72b", "Radeon Qwen 2.5 72B", "Large", "32K ctx")
            ),
            defaultModel = "radeon-llama-3.3-70b",
            quotaDescription = "TokenFactory Free Shared Models",
            portalUrl = "https://developer.amd.com.cn/radeon/tokenfactory",
            keyPlaceholder = "radeon_...",
            endpoint = "https://developer.amd.com.cn/radeon/api/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.MODELSCOPE,
            displayName = "ModelScope",
            catalogModels = listOf(
                CatalogModel("qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B (MS)", "Large", "32K ctx"),
                CatalogModel("deepseek-ai/DeepSeek-V3", "DeepSeek V3 (MS)", "Frontier", "128K ctx")
            ),
            defaultModel = "qwen/Qwen2.5-72B-Instruct",
            quotaDescription = "Aliyun ModelScope Free Tier",
            portalUrl = "https://modelscope.cn/my/myaccesstoken",
            keyPlaceholder = "ms_...",
            endpoint = "https://api-inference.modelscope.cn/v1/chat/completions"
        ),
        ProviderInfo(
            type = ProviderType.AIHORDE,
            displayName = "AI Horde",
            catalogModels = listOf(
                CatalogModel("aphrodite/llama-3-8b", "Llama 3 8B (Horde)", "Medium", "8K ctx"),
                CatalogModel("koboldcpp/mistral-7b", "Mistral 7B (Horde)", "Medium", "8K ctx")
            ),
            defaultModel = "aphrodite/llama-3-8b",
            quotaDescription = "Crowdsourced compute · Keyless / Anonymous",
            portalUrl = "https://aihorde.net/register",
            keyPlaceholder = "0000000000 (keyless)",
            endpoint = "https://aihorde.net/api/v2/generate/text/async"
        ),
        ProviderInfo(
            type = ProviderType.CUSTOM,
            displayName = "Custom / FreeLLMAPI",
            catalogModels = listOf(
                CatalogModel("auto", "Auto Router (Best available)", "Frontier", "128K ctx"),
                CatalogModel("auto:fast", "Auto Fast (Lowest latency)", "Medium", "128K ctx"),
                CatalogModel("auto:smart", "Auto Smart (Highest reasoning)", "Frontier", "1M ctx")
            ),
            defaultModel = "auto",
            quotaDescription = "Custom OpenAI-compatible proxy",
            portalUrl = "http://127.0.0.1:3001",
            keyPlaceholder = "freellmapi-...",
            endpoint = "http://127.0.0.1:3001/v1/chat/completions"
        )
    )

    fun getInfo(type: ProviderType): ProviderInfo {
        return allProviders.find { it.type == type } ?: allProviders.first()
    }
}
