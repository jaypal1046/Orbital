package com.orbital.updater

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.orbital.skills.MobileSkill
import com.orbital.skills.MobileSkillRegistry
import com.orbital.skills.SkillCategory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DynamicOtaConfigStoreTest {

    private lateinit var context: Context
    private lateinit var otaStore: DynamicOtaConfigStore
    private lateinit var skillRegistry: MobileSkillRegistry

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        otaStore = DynamicOtaConfigStore(context)
        skillRegistry = MobileSkillRegistry()
    }

    @After
    fun tearDown() {
        otaStore.clearPatch()
    }

    @Test
    fun `default patch version is zero and active config is default`() {
        assertThat(otaStore.getCurrentPatchVersion()).isEqualTo(0)
        assertThat(otaStore.getActiveConfig().customSystemPrompt).isNull()
        assertThat(otaStore.getActiveConfig().dynamicRules).isEmpty()
    }

    @Test
    fun `injectDirectPatch updates active config and state flow instantly`() {
        val testPatch = OtaHotPatchConfig(
            patchVersion = 42,
            description = "Test Emergency Hot Patch",
            customSystemPrompt = "You are an optimized autonomous mobile assistant.",
            dynamicRules = listOf("Rule 1: Always verify button state", "Rule 2: Dismiss popups fast"),
            dynamicSkills = listOf(
                OtaSkillDto(
                    id = "ota-live-weather",
                    name = "Live Weather Radar",
                    categoryName = "SYSTEM",
                    icon = "🌦️",
                    description = "Instant weather checks and forecasts"
                )
            ),
            modelRoutingOverrides = mapOf("reasoning" to "gemini-2.0-flash"),
            featureFlags = mapOf("fast_mode" to true)
        )

        otaStore.injectDirectPatch(testPatch)

        assertThat(otaStore.getCurrentPatchVersion()).isEqualTo(42)
        val active = otaStore.getActiveConfig()
        assertThat(active.description).isEqualTo("Test Emergency Hot Patch")
        assertThat(active.customSystemPrompt).contains("autonomous mobile assistant")
        assertThat(active.dynamicRules).hasSize(2)
        assertThat(active.featureFlags["fast_mode"]).isTrue()
        assertThat(otaStore.activeConfigFlow.value.patchVersion).isEqualTo(42)

        // Verify dynamic OTA skill injection into skill registry
        skillRegistry.injectOtaSkills(active.dynamicSkills.map { it.toMobileSkill() })
        val registered = skillRegistry.skills.value
        assertThat(registered.any { it.id == "ota-live-weather" }).isTrue()
        assertThat(skillRegistry.getActiveSkillsPrompt()).contains("Live Weather Radar")
    }

    @Test
    fun `clearPatch resets config to initial state`() {
        otaStore.injectDirectPatch(OtaHotPatchConfig(patchVersion = 10, description = "Temp"))
        assertThat(otaStore.getCurrentPatchVersion()).isEqualTo(10)

        otaStore.clearPatch()
        assertThat(otaStore.getCurrentPatchVersion()).isEqualTo(0)
        assertThat(otaStore.getActiveConfig().patchVersion).isEqualTo(0)
    }
}
