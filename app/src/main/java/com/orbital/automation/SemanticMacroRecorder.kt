package com.orbital.automation

import com.orbital.skills.DeclarativeSkill
import java.util.UUID

data class SemanticActionRecord(
    val actionType: String,
    val targetLabel: String?,
    val targetViewId: String?,
    val inputText: String? = null,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis()
)

class SemanticMacroRecorder {

    private var isRecording = false
    private val recordedActions = mutableListOf<SemanticActionRecord>()
    private var recordingName: String = "Custom Macro"
    private var startPackage: String = ""

    fun startRecording(name: String = "Custom Macro", initialPackage: String = "") {
        isRecording = true
        recordingName = name
        startPackage = initialPackage
        recordedActions.clear()
    }

    fun recordTap(element: UIElement, packageName: String) {
        if (!isRecording) return
        val label = element.text.ifBlank { element.contentDescription ?: element.viewId ?: "" }
        recordedActions.add(
            SemanticActionRecord(
                actionType = "CLICK_ELEMENT",
                targetLabel = label,
                targetViewId = element.viewId,
                packageName = packageName
            )
        )
    }

    fun recordType(element: UIElement, text: String, packageName: String) {
        if (!isRecording) return
        val label = element.text.ifBlank { element.contentDescription ?: element.viewId ?: "" }
        recordedActions.add(
            SemanticActionRecord(
                actionType = "INPUT_TEXT",
                targetLabel = label,
                targetViewId = element.viewId,
                inputText = text,
                packageName = packageName
            )
        )
    }

    fun stopRecording(): List<SemanticActionRecord> {
        isRecording = false
        return recordedActions.toList()
    }

    fun synthesizeSkillMarkdown(description: String = "Automated macro recorded on device"): DeclarativeSkill {
        val skillId = recordingName.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val instructions = buildString {
            append("# Procedure for $recordingName\n\n")
            if (startPackage.isNotBlank()) {
                append("1. Launch or ensure foreground app is `$startPackage`.\n")
            }
            recordedActions.forEachIndexed { idx, act ->
                val stepNum = if (startPackage.isNotBlank()) idx + 2 else idx + 1
                when (act.actionType) {
                    "CLICK_ELEMENT" -> append("$stepNum. Click element with label \"${act.targetLabel}\".\n")
                    "INPUT_TEXT" -> append("$stepNum. Enter \"${act.inputText}\" into \"${act.targetLabel}\".\n")
                    else -> append("$stepNum. Execute ${act.actionType}.\n")
                }
            }
            append("\n---\n_Generated automatically by Orbital Semantic Macro Recorder._")
        }

        return DeclarativeSkill(
            id = skillId,
            name = recordingName,
            description = description,
            triggers = listOf(recordingName.lowercase()),
            instructions = instructions
        )
    }
}
