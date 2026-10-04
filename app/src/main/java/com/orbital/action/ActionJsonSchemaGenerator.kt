package com.orbital.action

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ActionJsonSchemaGenerator {

    private val prettyJson = Json { prettyPrint = true }

    /**
     * Generates standard OpenAPI / OpenAI / Gemini JSON Schema function definitions for all registered actions.
     */
    fun generateAllToolSchemas(actions: List<ActionDefinition> = ActionRegistry.allActions): String {
        val rootArray = buildJsonArray {
            actions.forEach { def ->
                addJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", def.type)
                        put("description", def.description)
                        putJsonObject("parameters") {
                            put("type", "object")
                            putJsonObject("properties") {
                                val allParams = (def.requiredParams + def.optionalParams).distinct()
                                allParams.forEach { param ->
                                    putJsonObject(param) {
                                        when (param) {
                                            "seconds", "hour", "minutes", "repeat_minutes", "startLine", "endLine", "start_line", "end_line", "row", "col" -> {
                                                put("type", "integer")
                                                put("description", "Numeric parameter for $param")
                                            }
                                            "enabled", "is_regex", "isRegex", "overwrite", "allow_multiple", "allowMultiple" -> {
                                                put("type", "boolean")
                                                put("description", "Boolean toggle for $param")
                                            }
                                            "row_data", "rowData" -> {
                                                put("type", "array")
                                                putJsonObject("items") {
                                                    put("type", "string")
                                                }
                                                put("description", "List of string cell values for row")
                                            }
                                            else -> {
                                                put("type", "string")
                                                put("description", "Parameter $param value")
                                            }
                                        }
                                    }
                                }
                            }
                            if (def.requiredParams.isNotEmpty()) {
                                putJsonArray("required") {
                                    def.requiredParams.forEach { add(it) }
                                }
                            }
                        }
                    }
                }
            }
        }

        return prettyJson.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), rootArray)
    }

    /**
     * Generates Anthropic Claude tool schema format.
     */
    fun generateClaudeToolSchemas(actions: List<ActionDefinition> = ActionRegistry.allActions): String {
        val rootArray = buildJsonArray {
            actions.forEach { def ->
                addJsonObject {
                    put("name", def.type)
                    put("description", def.description)
                    putJsonObject("input_schema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            val allParams = (def.requiredParams + def.optionalParams).distinct()
                            allParams.forEach { param ->
                                putJsonObject(param) {
                                    val typeStr = when (param) {
                                        "seconds", "hour", "minutes", "start_line", "end_line", "row", "col" -> "integer"
                                        "enabled", "overwrite", "is_regex" -> "boolean"
                                        else -> "string"
                                    }
                                    put("type", typeStr)
                                    put("description", "Specifies $param")
                                }
                            }
                        }
                        if (def.requiredParams.isNotEmpty()) {
                            putJsonArray("required") {
                                def.requiredParams.forEach { add(it) }
                            }
                        }
                    }
                }
            }
        }

        return prettyJson.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), rootArray)
    }
}
