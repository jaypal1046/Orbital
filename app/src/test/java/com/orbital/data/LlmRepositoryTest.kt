package com.orbital.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LlmRepositoryTest {

    private lateinit var secureStorage: SecureStorage
    private lateinit var llmRepository: LlmRepository

    @Before
    fun setup() {
        // Use a mock context or test the logic directly
        // For now, we test the routing logic via the registry
    }

    @Test
    fun providerRegistry_allProvidersHaveRequiredFields() {
        val allProviders = ProviderRegistry.allProviders

        assertTrue("Should have at least 20 providers", allProviders.size >= 20)

        allProviders.forEach { provider ->
            assertNotNull("Provider ${provider.type} must have displayName", provider.displayName)
            assertTrue("Provider ${provider.type} must have catalogModels", provider.catalogModels.isNotEmpty())
            assertTrue("Provider ${provider.type} must have defaultModel", provider.defaultModel.isNotBlank())
            assertTrue("Provider ${provider.type} must have quotaDescription", provider.quotaDescription.isNotBlank())
            assertTrue("Provider ${provider.type} must have portalUrl", provider.portalUrl.isNotBlank())
            assertTrue("Provider ${provider.type} must have keyPlaceholder", provider.keyPlaceholder.isNotBlank())
            assertTrue("Provider ${provider.type} must have endpoint", provider.endpoint.isNotBlank())

            provider.catalogModels.forEach { model ->
                assertTrue("Model ${model.modelId} must have modelId", model.modelId.isNotBlank())
                assertTrue("Model ${model.modelId} must have displayName", model.displayName.isNotBlank())
                assertTrue("Model ${model.modelId} must have sizeLabel", model.sizeLabel.isNotBlank())
                assertTrue("Model ${model.modelId} must have contextWindow", model.contextWindow.isNotBlank())
            }
        }
    }

    @Test
    fun providerRegistry_getInfo_returnsCorrectProvider() {
        val groqInfo = ProviderRegistry.getInfo(ProviderType.GROQ)
        val geminiInfo = ProviderRegistry.getInfo(ProviderType.GEMINI)

        assertEquals(ProviderType.GROQ, groqInfo.type)
        assertEquals("Groq", groqInfo.displayName)
        assertEquals("llama-3.3-70b-versatile", groqInfo.defaultModel)

        assertEquals(ProviderType.GEMINI, geminiInfo.type)
        assertEquals("Google AI Studio", geminiInfo.displayName)
        assertEquals("gemini-3.6-flash", geminiInfo.defaultModel)
    }

    @Test
    fun providerRegistry_fastTierProviders_areSubsetOfAll() {
        val fastTier = ProviderConfig.fastTierProviders
        val allProviders = ProviderConfig.defaultProviderOrder

        assertTrue("Fast tier should have at least 4 providers", fastTier.size >= 4)
        fastTier.forEach { type ->
            assertTrue("Fast tier provider $type must be in default order", allProviders.contains(type))
        }
    }

    @Test
    fun providerRegistry_frontierTierProviders_areSubsetOfAll() {
        val frontierTier = ProviderConfig.frontierTierProviders
        val allProviders = ProviderConfig.defaultProviderOrder

        assertTrue("Frontier tier should have at least 6 providers", frontierTier.size >= 6)
        frontierTier.forEach { type ->
            assertTrue("Frontier tier provider $type must be in default order", allProviders.contains(type))
        }
    }

    @Test
    fun providerRegistry_noDuplicateTypes() {
        val allTypes = ProviderRegistry.allProviders.map { it.type }
        val uniqueTypes = allTypes.distinct()

        assertEquals("No duplicate provider types", allTypes.size, uniqueTypes.size)
    }

    @Test
    fun providerConfig_fastTierContainsExpectedProviders() {
        val fastTier = ProviderConfig.fastTierProviders

        assertTrue(fastTier.contains(ProviderType.CEREBRAS))
        assertTrue(fastTier.contains(ProviderType.GROQ))
        assertTrue(fastTier.contains(ProviderType.GEMINI))
        assertTrue(fastTier.contains(ProviderType.ALPHAOX))
    }

    @Test
    fun providerConfig_frontierTierContainsExpectedProviders() {
        val frontierTier = ProviderConfig.frontierTierProviders

        assertTrue(frontierTier.contains(ProviderType.GEMINI))
        assertTrue(frontierTier.contains(ProviderType.NVIDIA_NIM))
        assertTrue(frontierTier.contains(ProviderType.GITHUB_MODELS))
        assertTrue(frontierTier.contains(ProviderType.OPENROUTER))
        assertTrue(frontierTier.contains(ProviderType.MISTRAL))
        assertTrue(frontierTier.contains(ProviderType.ZHIPU))
        assertTrue(frontierTier.contains(ProviderType.HUGGINGFACE))
    }

    @Test
    fun providerConfig_defaultOrderHasExpectedProviders() {
        val defaultOrder = ProviderConfig.defaultProviderOrder

        assertTrue(defaultOrder.contains(ProviderType.GROQ))
        assertTrue(defaultOrder.contains(ProviderType.GEMINI))
        assertTrue(defaultOrder.contains(ProviderType.CEREBRAS))
        assertTrue(defaultOrder.contains(ProviderType.ALPHAOX))
        assertTrue(defaultOrder.contains(ProviderType.OPENROUTER))
        assertTrue(defaultOrder.contains(ProviderType.MISTRAL))
    }

    @Test
    fun providerState_enumValuesExist() {
        val states = ProviderState.values()

        assertEquals(3, states.size)
        assertTrue(states.contains(ProviderState.AVAILABLE))
        assertTrue(states.contains(ProviderState.UNAVAILABLE))
        assertTrue(states.contains(ProviderState.IN_COOLDOWN))
    }

    @Test
    fun routingMode_enumValuesExist() {
        val modes = RoutingMode.values()

        assertEquals(4, modes.size)
        assertTrue(modes.contains(RoutingMode.AUTO))
        assertTrue(modes.contains(RoutingMode.FAST))
        assertTrue(modes.contains(RoutingMode.FRONTIER))
        assertTrue(modes.contains(RoutingMode.PINNED))

        assertEquals("Auto Router", RoutingMode.AUTO.displayName)
        assertEquals("Fast Tier", RoutingMode.FAST.displayName)
        assertEquals("Frontier Tier", RoutingMode.FRONTIER.displayName)
        assertEquals("Pinned Provider", RoutingMode.PINNED.displayName)
    }

    @Test
    fun catalogModel_creation() {
        val model = CatalogModel("test-model", "Test Model", "Large", "128K ctx")

        assertEquals("test-model", model.modelId)
        assertEquals("Test Model", model.displayName)
        assertEquals("Large", model.sizeLabel)
        assertEquals("128K ctx", model.contextWindow)
    }

    @Test
    fun providerInfo_modelsListDerivedFromCatalog() {
        val info = ProviderRegistry.getInfo(ProviderType.GROQ)
        val models = info.models

        assertTrue(models.contains("llama-3.3-70b-versatile"))
        assertTrue(models.contains("llama-4-scout-17b-16e-instruct"))
        assertEquals(info.catalogModels.size, models.size)
    }
}