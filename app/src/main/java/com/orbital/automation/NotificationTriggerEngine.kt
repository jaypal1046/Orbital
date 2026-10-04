package com.orbital.automation

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NotificationPayload(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NotificationTriggerRule(
    val id: String,
    val keywordOrRegex: String,
    val isRegex: Boolean = false,
    val targetPackage: String? = null,
    val description: String
)

data class TriggerMatch(
    val rule: NotificationTriggerRule,
    val notification: NotificationPayload,
    val extractedValue: String? = null
)

object NotificationTriggerEngine {

    private val rules = ConcurrentHashMap<String, NotificationTriggerRule>()
    private val _triggerEvents = MutableSharedFlow<TriggerMatch>(extraBufferCapacity = 64)
    val triggerEvents: SharedFlow<TriggerMatch> = _triggerEvents.asSharedFlow()

    fun registerRule(rule: NotificationTriggerRule) {
        rules[rule.id] = rule
    }

    fun removeRule(ruleId: String) {
        rules.remove(ruleId)
    }

    fun clearRules() {
        rules.clear()
    }

    fun getRules(): List<NotificationTriggerRule> = rules.values.toList()

    fun processNotification(notification: NotificationPayload): List<TriggerMatch> {
        val matches = mutableListOf<TriggerMatch>()
        val combinedContent = "${notification.title} ${notification.text}"

        rules.values.forEach { rule ->
            if (rule.targetPackage != null && rule.targetPackage != notification.packageName) {
                return@forEach
            }

            if (rule.isRegex) {
                val regex = Regex(rule.keywordOrRegex, RegexOption.IGNORE_CASE)
                val matchResult = regex.find(combinedContent)
                if (matchResult != null) {
                    val extracted = matchResult.groupValues.getOrNull(1) ?: matchResult.value
                    val match = TriggerMatch(rule, notification, extracted)
                    matches.add(match)
                    _triggerEvents.tryEmit(match)
                }
            } else {
                if (combinedContent.contains(rule.keywordOrRegex, ignoreCase = true)) {
                    val match = TriggerMatch(rule, notification, null)
                    matches.add(match)
                    _triggerEvents.tryEmit(match)
                }
            }
        }

        return matches
    }
}
