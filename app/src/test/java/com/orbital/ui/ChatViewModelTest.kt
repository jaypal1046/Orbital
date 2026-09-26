package com.orbital.ui

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.orbital.action.ActionResult
import com.orbital.action.DeviceAction
import com.orbital.action.DeviceActionExecutor
import com.orbital.chat.ChatEngine
import com.orbital.data.ChatMessage
import com.orbital.data.LlmRepository
import com.orbital.data.ProviderType
import com.orbital.data.RoutingMode
import com.orbital.data.SecureStorage
import com.orbital.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeChatEngine(
        private val deviceActionExecutor: DeviceActionExecutor? = null
    ) : ChatEngine {
        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        override val streamingContent = MutableStateFlow("")
        override val isStreaming = MutableStateFlow(false)
        override val activeProvider = MutableStateFlow<String?>("GROQ")

        var lastSentMessage: String? = null
        var lastCharacter: String? = null

        override fun setCharacter(character: String) {
            lastCharacter = character
        }

        override suspend fun sendMessage(message: String) {
            lastSentMessage = message
            _messages.value = _messages.value + ChatMessage(role = "user", content = message)
        }

        override suspend fun handleStreamCompletion() {
            val content = streamingContent.value
            if (content.isNotBlank()) {
                val parsed = com.orbital.action.ActionParser.parse(content)
                var actionLabel: String? = null
                var actionDetails: String? = null
                val action = parsed.action
                if (action != null && deviceActionExecutor != null) {
                    val result = deviceActionExecutor.execute(action)
                    when (result) {
                        is ActionResult.Success -> {
                            actionLabel = "⚡ Executed: ${result.message}"
                            actionDetails = result.details
                        }
                        is ActionResult.Error -> {
                            actionLabel = "⚠️ Action Failed: ${result.errorMessage}"
                        }
                    }
                }
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant",
                    content = parsed.userDisplayText,
                    actionLabel = actionLabel,
                    actionDetails = actionDetails
                )
            }
            streamingContent.value = ""
            isStreaming.value = false
        }

        override suspend fun executeTTSAndActions() {}
    }

    private class FakeLlmRepository : LlmRepository() {
        private var routingMode: RoutingMode = RoutingMode.AUTO
        private var currentProvider: ProviderType? = ProviderType.GROQ

        override fun getRoutingMode(): RoutingMode = routingMode
        override fun setRoutingMode(mode: RoutingMode) {
            routingMode = mode
        }

        override fun getCurrentProviderType(): ProviderType? = currentProvider
        override fun setCurrentProvider(type: ProviderType) {
            currentProvider = type
        }
    }

    private class FakeVoiceManager(context: Context) : VoiceManager(context) {
        var startListeningCalled = false
        var stopListeningCalled = false
        var spokenText: String? = null
        var callback: VoiceCallback? = null

        override fun setVoiceCallback(callback: VoiceCallback) {
            this.callback = callback
        }

        override fun startListening() {
            startListeningCalled = true
        }

        override fun stopListening() {
            stopListeningCalled = true
        }

        override fun speak(text: String, queueMode: Int) {
            spokenText = text
        }

        override fun shutdown() {}
    }

    private class FakeDeviceActionExecutor(context: Context) : DeviceActionExecutor(context) {
        var executedAction: DeviceAction? = null
        var returnResult: ActionResult = ActionResult.Success("Opened Settings", "Settings opened")

        override fun execute(action: DeviceAction): ActionResult {
            executedAction = action
            return returnResult
        }
    }

    private class FakeSecureStorage(context: Context) : SecureStorage(context) {
        private var character: String = "aether"

        override fun getCharacter(): String = character
        override fun saveCharacter(character: String) {
            this.character = character
        }
    }

    private lateinit var fakeChatEngine: FakeChatEngine
    private lateinit var fakeLlmRepository: FakeLlmRepository
    private lateinit var fakeVoiceManager: FakeVoiceManager
    private lateinit var fakeDeviceActionExecutor: FakeDeviceActionExecutor
    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication().applicationContext

        fakeDeviceActionExecutor = FakeDeviceActionExecutor(context)
        fakeChatEngine = FakeChatEngine(fakeDeviceActionExecutor)
        fakeLlmRepository = FakeLlmRepository()
        fakeVoiceManager = FakeVoiceManager(context)
        fakeSecureStorage = FakeSecureStorage(context)

        viewModel = ChatViewModel(
            chatEngine = fakeChatEngine,
            llmRepository = fakeLlmRepository,
            voiceManager = fakeVoiceManager,
            deviceActionExecutor = fakeDeviceActionExecutor,
            secureStorage = fakeSecureStorage
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasGreetingAndDefaultConfigurations() {
        val messages = viewModel.messages.value
        assertEquals(1, messages.size)
        assertEquals("assistant", messages[0].role)
        assertTrue(messages[0].content.contains("aether", ignoreCase = true))

        assertEquals("", viewModel.inputText.value)
        assertFalse(viewModel.isStreaming.value)
        assertFalse(viewModel.isVoiceListening.value)
        assertEquals(RoutingMode.AUTO, viewModel.currentRoutingMode.value)
        assertEquals(ProviderType.GROQ, viewModel.selectedPinnedProvider.value)
        assertTrue(viewModel.quickSuggestions.value.isNotEmpty())
    }

    @Test
    fun sendMessage_updatesMessagesAndTriggersChatEngine() = runTest(testDispatcher) {
        val prompt = "Turn on WiFi"

        viewModel.sendMessage(prompt)
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[1].role)
        assertEquals("Turn on WiFi", messages[1].content)
        assertTrue(viewModel.isStreaming.value)
        assertEquals("GROQ", viewModel.activeServingProvider.value)

        assertEquals("Turn on WiFi", fakeChatEngine.lastSentMessage)
        assertEquals("aether", fakeChatEngine.lastCharacter)
    }

    @Test
    fun streamingFlow_finalizesResponseAndExecutesAction() = runTest(testDispatcher) {
        // 1. User sends message
        viewModel.sendMessage("Open Settings")
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. ChatEngine streams response with action block
        fakeChatEngine.streamingContent.value = "Opening settings now.\n```action\n{\"action\": \"OPEN_SETTING\", \"target\": \"settings\"}\n```"
        fakeChatEngine.isStreaming.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Opening settings now.\n```action\n{\"action\": \"OPEN_SETTING\", \"target\": \"settings\"}\n```", viewModel.currentStreamContent.value)

        // 3. ChatEngine finishes streaming and executes handleStreamCompletion
        fakeChatEngine.handleStreamCompletion()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Verify assistant message added and action executed
        val messages = viewModel.messages.value
        assertEquals(3, messages.size) // Greeting + User + Assistant
        val assistantMsg = messages[2]
        assertEquals("assistant", assistantMsg.role)
        assertTrue(assistantMsg.content.contains("Opening settings now"))
        assertEquals("⚡ Executed: Opened Settings", assistantMsg.actionLabel)
        assertEquals("Settings opened", assistantMsg.actionDetails)

        assertNotNull(fakeDeviceActionExecutor.executedAction)
        assertEquals("OPEN_SETTING", fakeDeviceActionExecutor.executedAction?.action)
        assertEquals("settings", fakeDeviceActionExecutor.executedAction?.target)
    }

    @Test
    fun onRoutingModeChanged_updatesStateAndRepository() {
        viewModel.onRoutingModeChanged(RoutingMode.FRONTIER)

        assertEquals(RoutingMode.FRONTIER, viewModel.currentRoutingMode.value)
        assertEquals(RoutingMode.FRONTIER, fakeLlmRepository.getRoutingMode())
    }

    @Test
    fun onPinnedProviderChanged_updatesStateAndRepository() {
        viewModel.onPinnedProviderChanged(ProviderType.GEMINI)

        assertEquals(ProviderType.GEMINI, viewModel.selectedPinnedProvider.value)
        assertEquals(ProviderType.GEMINI, fakeLlmRepository.getCurrentProviderType())
    }

    @Test
    fun toggleVoiceListening_startsAndStopsListening() {
        assertFalse(viewModel.isVoiceListening.value)

        // Toggle on
        viewModel.toggleVoiceListening()
        assertTrue(viewModel.isVoiceListening.value)
        assertTrue(fakeVoiceManager.startListeningCalled)

        // Toggle off
        viewModel.toggleVoiceListening()
        assertFalse(viewModel.isVoiceListening.value)
        assertTrue(fakeVoiceManager.stopListeningCalled)
    }

    @Test
    fun speak_delegatesToVoiceManager() {
        viewModel.speak("Hello world")
        assertEquals("Hello world", fakeVoiceManager.spokenText)
    }

    @Test
    fun onInputChange_updatesInputText() {
        viewModel.onInputChange("Testing input")
        assertEquals("Testing input", viewModel.inputText.value)
    }

    @Test
    fun onSendClick_withNonEmptyInput_sendsMessage() = runTest(testDispatcher) {
        viewModel.onInputChange("Hello Orbital")
        viewModel.onSendClick()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.inputText.value)
        assertEquals("Hello Orbital", fakeChatEngine.lastSentMessage)
    }

    @Test
    fun onSuggestionClick_cleansPromptAndSetsInput() {
        viewModel.onSuggestionClick("🔍 Search YouTube for: ")
        assertEquals("Search YouTube for:", viewModel.inputText.value)
    }

    @Test
    fun onAddStepToInput_chainsStepsWithAndThen() {
        viewModel.onInputChange("Open Camera")
        viewModel.onAddStepToInput("Take photo")

        assertEquals("Open Camera and then Take photo", viewModel.inputText.value)
    }

    @Test
    fun onQuickSuggestionClick_populatesInputField() {
        viewModel.onQuickSuggestionClick("✉️ Open Gmail")

        assertEquals("Open Gmail", viewModel.inputText.value)
    }

    @Test
    fun refreshCharacter_reloadsCharacterFromSecureStorage() {
        fakeSecureStorage.saveCharacter("lumina")
        viewModel.refreshCharacter()

        assertEquals("lumina", viewModel.currentCharacter.value)
        assertEquals("Lumina (AI Companion)", viewModel.characterName)
    }
}
