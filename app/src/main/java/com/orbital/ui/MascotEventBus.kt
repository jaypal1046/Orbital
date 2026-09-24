package com.orbital.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MascotEvent {
    object Tap : MascotEvent()
    object LongPress : MascotEvent()
    data class PromptSent(val prompt: String) : MascotEvent()
    data class ActionExecuting(val actionName: String) : MascotEvent()
    data class ActionSuccess(val message: String) : MascotEvent()
    data class ActionFailed(val errorMessage: String) : MascotEvent()
    object VoiceListening : MascotEvent()
    object VoiceSpeaking : MascotEvent()
    object InactivityTimeout : MascotEvent()
    object WakeUp : MascotEvent()
    object ResetToIdle : MascotEvent()
}

object MascotEventBus {

    private val busScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _events = MutableSharedFlow<MascotEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MascotEvent> = _events.asSharedFlow()

    private val _currentState = MutableStateFlow(MascotState.IDLE)
    val currentState: StateFlow<MascotState> = _currentState.asStateFlow()

    private var cooldownJob: kotlinx.coroutines.Job? = null

    fun postEvent(event: MascotEvent) {
        _events.tryEmit(event)

        cooldownJob?.cancel()
        when (event) {
            is MascotEvent.Tap -> {
                _currentState.value = MascotState.JUMP
                cooldownJob = busScope.launch {
                    delay(800)
                    if (_currentState.value == MascotState.JUMP) {
                        _currentState.value = MascotState.HAPPY
                    }
                }
            }
            is MascotEvent.LongPress -> {
                _currentState.value = MascotState.CURIOUS
            }
            is MascotEvent.PromptSent -> {
                _currentState.value = MascotState.THINKING
            }
            is MascotEvent.ActionExecuting -> {
                _currentState.value = MascotState.WORKING
            }
            is MascotEvent.ActionSuccess -> {
                _currentState.value = MascotState.CELEBRATING
                cooldownJob = busScope.launch {
                    delay(3500)
                    if (_currentState.value == MascotState.CELEBRATING) {
                        _currentState.value = MascotState.HAPPY
                        delay(2000)
                        if (_currentState.value == MascotState.HAPPY) {
                            _currentState.value = MascotState.IDLE
                        }
                    }
                }
            }
            is MascotEvent.ActionFailed -> {
                _currentState.value = MascotState.SAD
                cooldownJob = busScope.launch {
                    delay(3000)
                    if (_currentState.value == MascotState.SAD) {
                        _currentState.value = MascotState.IDLE
                    }
                }
            }
            is MascotEvent.VoiceListening -> {
                _currentState.value = MascotState.CURIOUS
            }
            is MascotEvent.VoiceSpeaking -> {
                _currentState.value = MascotState.HAPPY
            }
            is MascotEvent.InactivityTimeout -> {
                _currentState.value = MascotState.SLEEPING
            }
            is MascotEvent.WakeUp -> {
                _currentState.value = MascotState.JUMP
                cooldownJob = busScope.launch {
                    delay(700)
                    _currentState.value = MascotState.IDLE
                }
            }
            is MascotEvent.ResetToIdle -> {
                _currentState.value = MascotState.IDLE
            }
        }
    }
}
