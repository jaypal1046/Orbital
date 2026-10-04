package com.orbital.skills

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class DeclarativeSkill(
    val id: String,
    val name: String,
    val description: String,
    val triggers: List<String> = emptyList(),
    val instructions: String,
    val sourcePath: String? = null
)

object DeclarativeSkillEngine {

    fun parseSkillMarkdown(content: String, sourcePath: String? = null): DeclarativeSkill? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("---")) {
            // No frontmatter, treat whole content as markdown instructions with title
            val lines = trimmed.lines()
            val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: "Custom Skill"
            return DeclarativeSkill(
                id = title.lowercase().replace(Regex("[^a-z0-9_]"), "_"),
                name = title,
                description = lines.getOrNull(1)?.trim() ?: title,
                triggers = emptyList(),
                instructions = trimmed,
                sourcePath = sourcePath
            )
        }

        val parts = trimmed.split("---", limit = 3)
        if (parts.size < 3) return null

        val frontmatter = parts[1].trim()
        val markdownBody = parts[2].trim()

        var name = "Custom Skill"
        var description = ""
        val triggers = mutableListOf<String>()

        frontmatter.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("name:") -> name = line.substringAfter("name:").trim().trim('"', '\'')
                line.startsWith("description:") -> description = line.substringAfter("description:").trim().trim('"', '\'')
                line.startsWith("triggers:") -> {
                    val rawTriggers = line.substringAfter("triggers:").trim()
                    if (rawTriggers.startsWith("[") && rawTriggers.endsWith("]")) {
                        val items = rawTriggers.removeSurrounding("[", "]").split(",")
                        triggers.addAll(items.map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() })
                    }
                }
                line.startsWith("- ") -> {
                    val item = line.removePrefix("- ").trim().trim('"', '\'')
                    if (item.isNotBlank()) triggers.add(item)
                }
            }
        }

        val id = name.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return DeclarativeSkill(
            id = id,
            name = name,
            description = description.ifBlank { name },
            triggers = triggers,
            instructions = markdownBody,
            sourcePath = sourcePath
        )
    }

    fun loadSkillsFromDirectory(skillsDir: File): List<DeclarativeSkill> {
        if (!skillsDir.exists() || !skillsDir.isDirectory) return emptyList()
        val skills = mutableListOf<DeclarativeSkill>()

        skillsDir.walkTopDown().filter { it.isFile && (it.name.equals("SKILL.md", ignoreCase = true) || it.extension.equals("md", ignoreCase = true)) }.forEach { file ->
            try {
                val parsed = parseSkillMarkdown(file.readText(), file.absolutePath)
                if (parsed != null) {
                    skills.add(parsed)
                }
            } catch (_: Exception) {}
        }
        return skills
    }

    fun findRelevantSkills(skills: List<DeclarativeSkill>, userPrompt: String): List<DeclarativeSkill> {
        val cleanPrompt = userPrompt.lowercase().trim()
        return skills.filter { skill ->
            val triggerMatch = skill.triggers.any { cleanPrompt.contains(it.lowercase()) }
            val nameMatch = cleanPrompt.contains(skill.name.lowercase())
            val keywordMatch = skill.id.split("_").filter { it.length > 3 }.any { cleanPrompt.contains(it) }
            triggerMatch || nameMatch || keywordMatch
        }
    }

    fun buildSkillPromptSection(relevantSkills: List<DeclarativeSkill>): String {
        if (relevantSkills.isEmpty()) return ""
        return buildString {
            append("\n### Active Procedural Skills (${relevantSkills.size}):\n")
            relevantSkills.forEachIndexed { idx, skill ->
                append("#### ${idx + 1}. Skill: ${skill.name}\n")
                append("> ${skill.description}\n\n")
                append(skill.instructions)
                append("\n\n---\n")
            }
        }
    }
}
