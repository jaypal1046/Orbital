package com.orbital.bridge

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class BridgeSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `test ScreenStatePayload serialization and deserialization`() {
        val payload = ScreenStatePayload(
            currentPackage = "com.android.settings",
            currentActivity = ".SettingsActivity",
            screenWidth = 1080,
            screenHeight = 2400,
            nodes = listOf(
                ScreenNodeDto(
                    id = "android:id/title",
                    text = "Wi-Fi",
                    contentDescription = "Wi-Fi Settings",
                    className = "android.widget.TextView",
                    bounds = listOf(64, 320, 1016, 480),
                    isClickable = true,
                    isScrollable = false,
                    isEditable = false,
                    isEnabled = true
                )
            )
        )

        val encoded = json.encodeToString(ScreenStatePayload.serializer(), payload)
        assertThat(encoded).contains("com.android.settings")
        assertThat(encoded).contains("Wi-Fi")

        val decoded = json.decodeFromString(ScreenStatePayload.serializer(), encoded)
        assertThat(decoded.currentPackage).isEqualTo("com.android.settings")
        assertThat(decoded.nodes).hasSize(1)
        assertThat(decoded.nodes[0].text).isEqualTo("Wi-Fi")
        assertThat(decoded.nodes[0].bounds).isEqualTo(listOf(64, 320, 1016, 480))
    }

    @Test
    fun `test ActionPayload serialization for CLICK_NODE`() {
        val action = ActionPayload(
            actionId = "act-101",
            actionType = BridgeActionType.CLICK_NODE,
            targetText = "Network & internet",
            targetId = "com.android.settings:id/title"
        )

        val message = BridgeMessage(
            type = "EXECUTE_ACTION",
            action = action
        )

        val encoded = json.encodeToString(BridgeMessage.serializer(), message)
        assertThat(encoded).contains("EXECUTE_ACTION")
        assertThat(encoded).contains("CLICK_NODE")
        assertThat(encoded).contains("Network & internet")

        val decoded = json.decodeFromString(BridgeMessage.serializer(), encoded)
        assertThat(decoded.type).isEqualTo("EXECUTE_ACTION")
        assertThat(decoded.action?.actionId).isEqualTo("act-101")
        assertThat(decoded.action?.targetText).isEqualTo("Network & internet")
    }

    @Test
    fun `test ActionResultPayload serialization`() {
        val result = ActionResultPayload(
            actionId = "act-101",
            success = true,
            message = "Clicked element successfully",
            executionDurationMs = 38L
        )

        val message = BridgeMessage(
            type = "ACTION_RESULT",
            result = result
        )

        val encoded = json.encodeToString(BridgeMessage.serializer(), message)
        val decoded = json.decodeFromString(BridgeMessage.serializer(), encoded)

        assertThat(decoded.type).isEqualTo("ACTION_RESULT")
        assertThat(decoded.result?.success).isTrue()
        assertThat(decoded.result?.message).isEqualTo("Clicked element successfully")
        assertThat(decoded.result?.executionDurationMs).isEqualTo(38L)
    }
}
