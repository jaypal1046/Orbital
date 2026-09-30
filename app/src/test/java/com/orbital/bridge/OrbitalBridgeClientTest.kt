package com.orbital.bridge

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.orbital.action.DeviceActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OrbitalBridgeClientTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var actionDispatcher: BridgeActionDispatcher
    private lateinit var bridgeClient: OrbitalBridgeClient

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        val executor = mock(DeviceActionExecutor::class.java)
        actionDispatcher = BridgeActionDispatcher(context, executor)
        bridgeClient = OrbitalBridgeClient(context, actionDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is DISCONNECTED`() {
        assertThat(bridgeClient.connectionState.value).isEqualTo(BridgeConnectionState.DISCONNECTED)
        assertThat(bridgeClient.activeChannelCode.value).isNull()
    }

    @Test
    fun `disconnect cleans up state`() {
        bridgeClient.disconnect()
        assertThat(bridgeClient.connectionState.value).isEqualTo(BridgeConnectionState.DISCONNECTED)
        assertThat(bridgeClient.activeChannelCode.value).isNull()
    }

    @Test
    fun `logging updates eventLogs`() {
        bridgeClient.log("Test log event")
        assertThat(bridgeClient.eventLogs.value).isNotEmpty()
        assertThat(bridgeClient.eventLogs.value.last()).contains("Test log event")
    }

    @Test
    fun `action dispatcher handles INSPECT_SCREEN gracefully without service`() = runTest {
        val action = ActionPayload(
            actionId = "inspect-1",
            actionType = BridgeActionType.INSPECT_SCREEN
        )
        val result = actionDispatcher.dispatchAction(action)
        assertThat(result.success).isTrue()
        assertThat(result.updatedScreenState).isNotNull()
    }
}
