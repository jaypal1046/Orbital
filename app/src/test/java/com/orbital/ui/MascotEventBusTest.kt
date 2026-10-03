package com.orbital.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MascotEventBusTest {

    @Test
    fun postEvent_tap_setsJumpState() = runTest {
        MascotEventBus.postEvent(MascotEvent.Tap)
        assertEquals(MascotState.JUMP, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_promptSent_setsThinkingState() = runTest {
        MascotEventBus.postEvent(MascotEvent.PromptSent("Open an installed app"))
        assertEquals(MascotState.THINKING, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_actionExecuting_setsWorkingState() = runTest {
        MascotEventBus.postEvent(MascotEvent.ActionExecuting("OPEN_APP"))
        assertEquals(MascotState.WORKING, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_actionSuccess_setsCelebratingState() = runTest {
        MascotEventBus.postEvent(MascotEvent.ActionSuccess("Opened target app"))
        assertEquals(MascotState.CELEBRATING, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_actionFailed_setsSadState() = runTest {
        MascotEventBus.postEvent(MascotEvent.ActionFailed("Could not open app"))
        assertEquals(MascotState.SAD, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_voiceListening_setsCuriousState() = runTest {
        MascotEventBus.postEvent(MascotEvent.VoiceListening)
        assertEquals(MascotState.CURIOUS, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_voiceSpeaking_setsHappyState() = runTest {
        MascotEventBus.postEvent(MascotEvent.VoiceSpeaking)
        assertEquals(MascotState.HAPPY, MascotEventBus.currentState.value)
    }

    @Test
    fun postEvent_resetToIdle_setsIdleState() = runTest {
        MascotEventBus.postEvent(MascotEvent.ResetToIdle)
        assertEquals(MascotState.IDLE, MascotEventBus.currentState.value)
    }
}
