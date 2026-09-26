package com.orbital.data

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active provider selection, routing tiers (SPEED, QUALITY, BALANCED, CUSTOM / FAST, FRONTIER, AUTO, PINNED),
 * cooldown tracking on 429/5xx errors, and automatic silent failovers across 24+ providers.
 */
class ModelRouter {

    companion object {
        private const val TAG = "ModelRouter"
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }

    private var currentProvider: ProviderType? = null
    private var currentRoutingMode: RoutingMode = RoutingMode.AUTO
    private val providerCooldowns = ConcurrentHashMap<ProviderType, Long>()

    fun getRoutingMode(): RoutingMode = currentRoutingMode

    fun setRoutingMode(mode: RoutingMode) {
        currentRoutingMode = mode
    }

    fun getCurrentProvider(): ProviderType? = currentProvider

    fun setCurrentProvider(type: ProviderType) {
        currentProvider = type
    }

    fun markProviderCooldown(type: ProviderType, durationMs: Long = DEFAULT_COOLDOWN_MS) {
        providerCooldowns[type] = System.currentTimeMillis() + durationMs
    }

    fun isProviderInCooldown(type: ProviderType): Boolean {
        val cooldownTime = providerCooldowns[type] ?: return false
        val now = System.currentTimeMillis()
        if (now >= cooldownTime) {
            providerCooldowns.remove(type)
            return false
        }
        return true
    }

    fun resetCooldowns() {
        providerCooldowns.clear()
    }

    /**
     * Finds the next eligible provider according to the current RoutingMode and availability.
     */
    fun getNextAvailableProvider(
        configStore: ProviderConfigStore,
        routerConfig: RouterConfig = RouterConfig()
    ): ProviderType? {
        val candidateOrder = when (currentRoutingMode) {
            RoutingMode.FAST -> ProviderConfig.fastTierProviders + routerConfig.enabledProviders
            RoutingMode.FRONTIER -> ProviderConfig.frontierTierProviders + routerConfig.enabledProviders
            RoutingMode.PINNED -> currentProvider?.let { listOf(it) } ?: routerConfig.enabledProviders
            RoutingMode.AUTO -> routerConfig.enabledProviders
        }.distinct()

        for (type in candidateOrder) {
            val key = configStore.getProviderKey(type)
            val isEnabled = configStore.isProviderEnabled(type)
            val isKeyless = configStore.isKeylessProvider(type)
            val isAvailable = configStore.getProviderStatus(type) == ProviderState.AVAILABLE

            if (isEnabled &&
                (key.isNotBlank() || isKeyless || type == ProviderType.CUSTOM) &&
                isAvailable &&
                !isProviderInCooldown(type)
            ) {
                return type
            }
        }

        return null
    }

    /**
     * Resolves the provider that should serve the incoming completion request.
     */
    fun selectActiveProvider(
        configStore: ProviderConfigStore,
        routerConfig: RouterConfig = RouterConfig()
    ): ProviderType {
        if (currentRoutingMode == RoutingMode.PINNED && currentProvider != null && configStore.getProviderKey(currentProvider!!).isNotBlank()) {
            return currentProvider!!
        }

        val selected = getNextAvailableProvider(configStore, routerConfig)
            ?: currentProvider?.takeIf { configStore.getProviderKey(it).isNotBlank() }
            ?: ProviderRegistry.allProviders.firstOrNull { configStore.getProviderKey(it.type).isNotBlank() }?.type
            ?: ProviderType.GEMINI

        currentProvider = selected
        return selected
    }

    /**
     * Records a provider failure, sets cooldown if rate-limited (429/quota/5xx),
     * and updates the provider status.
     */
    fun handleProviderError(
        type: ProviderType,
        error: Throwable,
        configStore: ProviderConfigStore
    ) {
        Log.e(TAG, "Provider $type failed: ${error.message}", error)
        val errMsg = error.message ?: ""

        val isRateLimitOrOverload = errMsg.contains("429") ||
                errMsg.contains("quota", ignoreCase = true) ||
                errMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                errMsg.contains("timeout", ignoreCase = true) ||
                errMsg.contains("500") ||
                errMsg.contains("502") ||
                errMsg.contains("503") ||
                errMsg.contains("504")

        if (isRateLimitOrOverload) {
            markProviderCooldown(type, DEFAULT_COOLDOWN_MS)
            configStore.setProviderStatus(type, ProviderState.IN_COOLDOWN)
        } else {
            configStore.setProviderStatus(type, ProviderState.UNAVAILABLE)
        }
    }
}
