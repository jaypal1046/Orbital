package com.orbital.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MobileSkillRegistryTest {

    private lateinit var registry: MobileSkillRegistry

    @Before
    fun setUp() {
        registry = MobileSkillRegistry()
    }

    @Test
    fun testDefaultSkillsAreLoaded() {
        val skills = registry.skills.value
        assertNotNull("Skills list should not be null", skills)
        assertTrue("Should load default skills", skills.isNotEmpty())
        assertEquals("Should have 5 default skills", 5, skills.size)
    }

    @Test
    fun testDefaultSkillsIncludeCoreCapabilities() {
        val skillIds = registry.skills.value.map { it.id }
        assertTrue("Must include device automation", skillIds.contains("device-automation"))
        assertTrue("Must include screen vision", skillIds.contains("screen-vision"))
        assertTrue("Must include system controller", skillIds.contains("system-controller"))
        assertTrue("Must include smart messaging", skillIds.contains("smart-messaging"))
        assertTrue("Must include web research", skillIds.contains("web-research"))
    }

    @Test
    fun testToggleSkillState() {
        val skillId = "device-automation"
        val initialSkill = registry.skills.value.first { it.id == skillId }
        val initialState = initialSkill.isEnabled

        // Toggle state
        registry.toggleSkill(skillId)
        val updatedSkill = registry.skills.value.first { it.id == skillId }
        assertEquals("Skill state should be inverted", !initialState, updatedSkill.isEnabled)

        // Toggle back
        registry.toggleSkill(skillId)
        val revertedSkill = registry.skills.value.first { it.id == skillId }
        assertEquals("Skill state should revert", initialState, revertedSkill.isEnabled)
    }

    @Test
    fun testActiveSkillsPromptGeneration() {
        val prompt = registry.getActiveSkillsPrompt()
        assertNotNull("Prompt string should not be null", prompt)
        assertTrue("Prompt should mention Active Mobile Skills", prompt.contains("[Active Mobile Skills]"))
        assertTrue("Prompt should mention Android Device Automator", prompt.contains("Android Device Automator"))
    }

    @Test
    fun testEmptyPromptWhenAllSkillsDisabled() {
        // Disable all skills
        registry.skills.value.forEach { skill ->
            if (skill.isEnabled) {
                registry.toggleSkill(skill.id)
            }
        }

        val prompt = registry.getActiveSkillsPrompt()
        assertEquals("Prompt should be empty when no skills are active", "", prompt)
    }
}
