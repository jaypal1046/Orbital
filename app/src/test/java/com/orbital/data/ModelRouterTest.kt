package com.orbital.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelRouterTest {

    private lateinit var router: ModelRouter
    private lateinit var configStore: ProviderConfigStore

    @Before
    fun setup() {
        router = ModelRouter()
        configStore = ProviderConfigStore(secureStorage = null)
    }

    @Test
    fun defaultRoutingMode_isAuto() {
        assertEquals(RoutingMode.AUTO, router.getRoutingMode())
    }

    @Test
    fun setRoutingMode_updatesMode() {
        router.setRoutingMode(RoutingMode.FAST)
        assertEquals(RoutingMode.FAST, router.getRoutingMode())

        router.setRoutingMode(RoutingMode.FRONTIER)
        assertEquals(RoutingMode.FRONTIER, router.getRoutingMode())

        router.setRoutingMode(RoutingMode.PINNED)
        assertEquals(RoutingMode.PINNED, router.getRoutingMode())
    }

    @Test
    fun setCurrentProvider_updatesCurrentProvider() {
        assertNull(router.getCurrentProvider())
        router.setCurrentProvider(ProviderType.GROQ)
        assertEquals(ProviderType.GROQ, router.getCurrentProvider())
    }

    @Test
    fun cooldown_markAndCheckCooldown() {
        assertFalse(router.isProviderInCooldown(ProviderType.GROQ))

        router.markProviderCooldown(ProviderType.GROQ, 60_000L)
        assertTrue(router.isProviderInCooldown(ProviderType.GROQ))

        // Reset cooldowns
        router.resetCooldowns()
        assertFalse(router.isProviderInCooldown(ProviderType.GROQ))
    }

    @Test
    fun cooldown_expiredCooldownReturnsFalse() {
        // Negative duration means already expired
        router.markProviderCooldown(ProviderType.GROQ, -1000L)
        assertFalse(router.isProviderInCooldown(ProviderType.GROQ))
    }

    @Test
    fun selectActiveProvider_autoMode_selectsFirstEligibleProvider() {
        router.setRoutingMode(RoutingMode.AUTO)
        // Configure GROQ and GEMINI with keys
        configStore.updateProviderKey(ProviderType.GROQ, "gsk_test_groq_key")
        configStore.updateProviderKey(ProviderType.GEMINI, "gemini_test_key")

        val selected = router.selectActiveProvider(configStore)
        assertEquals(ProviderType.GEMINI, selected)
        assertEquals(ProviderType.GEMINI, router.getCurrentProvider())
    }

    @Test
    fun selectActiveProvider_fastMode_prioritizesFastTierProviders() {
        router.setRoutingMode(RoutingMode.FAST)
        // Configure Cerebras (top of fast tier) and OpenRouter
        configStore.updateProviderKey(ProviderType.OPENROUTER, "sk-or-test")
        configStore.updateProviderKey(ProviderType.CEREBRAS, "csk-test")

        val selected = router.selectActiveProvider(configStore)
        assertEquals(ProviderType.CEREBRAS, selected)
    }

    @Test
    fun selectActiveProvider_frontierMode_prioritizesFrontierTierProviders() {
        router.setRoutingMode(RoutingMode.FRONTIER)
        // Configure Cerebras and Nvidia Nim (Nvidia Nim is in frontier tier)
        configStore.updateProviderKey(ProviderType.CEREBRAS, "csk-test")
        configStore.updateProviderKey(ProviderType.NVIDIA_NIM, "nvapi-test")

        val selected = router.selectActiveProvider(configStore)
        assertEquals(ProviderType.NVIDIA_NIM, selected)
    }

    @Test
    fun selectActiveProvider_pinnedMode_respectsPinnedProvider() {
        router.setRoutingMode(RoutingMode.PINNED)
        router.setCurrentProvider(ProviderType.MISTRAL)
        configStore.updateProviderKey(ProviderType.MISTRAL, "mistral-key")
        configStore.updateProviderKey(ProviderType.GROQ, "groq-key")

        val selected = router.selectActiveProvider(configStore)
        assertEquals(ProviderType.MISTRAL, selected)
    }

    @Test
    fun selectActiveProvider_failoverWhenTopProviderInCooldown() {
        router.setRoutingMode(RoutingMode.AUTO)
        configStore.updateProviderKey(ProviderType.GROQ, "gsk_groq")
        configStore.updateProviderKey(ProviderType.GEMINI, "gemini_key")

        // Mark Groq in cooldown
        router.markProviderCooldown(ProviderType.GROQ, 60_000L)
        configStore.setProviderStatus(ProviderType.GROQ, ProviderState.IN_COOLDOWN)

        // Router should skip Groq and failover to Gemini
        val selected = router.selectActiveProvider(configStore)
        assertEquals(ProviderType.GEMINI, selected)
    }

    @Test
    fun selectActiveProvider_keylessProviderEligibleWithoutKey() {
        router.setRoutingMode(RoutingMode.AUTO)
        // Kilo is keyless
        val customConfig = RouterConfig(enabledProviders = listOf(ProviderType.KILO))
        val selected = router.selectActiveProvider(configStore, customConfig)
        assertEquals(ProviderType.KILO, selected)
    }

    @Test
    fun handleProviderError_rateLimit_triggersCooldown() {
        configStore.updateProviderKey(ProviderType.GROQ, "gsk_groq")

        // 429 Too Many Requests error
        val error429 = RuntimeException("HTTP 429: Rate limit exceeded")
        router.handleProviderError(ProviderType.GROQ, error429, configStore)

        assertTrue(router.isProviderInCooldown(ProviderType.GROQ))
        assertEquals(ProviderState.IN_COOLDOWN, configStore.getProviderStatus(ProviderType.GROQ))
    }

    @Test
    fun handleProviderError_quotaExhausted_triggersCooldown() {
        configStore.updateProviderKey(ProviderType.GEMINI, "gemini_key")

        val quotaError = RuntimeException("RESOURCE_EXHAUSTED: Quota exceeded for project")
        router.handleProviderError(ProviderType.GEMINI, quotaError, configStore)

        assertTrue(router.isProviderInCooldown(ProviderType.GEMINI))
        assertEquals(ProviderState.IN_COOLDOWN, configStore.getProviderStatus(ProviderType.GEMINI))
    }

    @Test
    fun handleProviderError_serverError_triggersCooldown() {
        configStore.updateProviderKey(ProviderType.CEREBRAS, "csk_cerebras")

        val server503Error = RuntimeException("HTTP 503 Service Unavailable")
        router.handleProviderError(ProviderType.CEREBRAS, server503Error, configStore)

        assertTrue(router.isProviderInCooldown(ProviderType.CEREBRAS))
        assertEquals(ProviderState.IN_COOLDOWN, configStore.getProviderStatus(ProviderType.CEREBRAS))
    }

    @Test
    fun handleProviderError_nonRateLimit_marksUnavailableWithoutCooldown() {
        configStore.updateProviderKey(ProviderType.GROQ, "invalid_key")

        val authError = RuntimeException("HTTP 401 Unauthorized - Invalid API Key")
        router.handleProviderError(ProviderType.GROQ, authError, configStore)

        assertFalse(router.isProviderInCooldown(ProviderType.GROQ))
        assertEquals(ProviderState.UNAVAILABLE, configStore.getProviderStatus(ProviderType.GROQ))
    }
}
