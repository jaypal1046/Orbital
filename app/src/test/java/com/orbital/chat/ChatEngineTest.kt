package com.orbital.chat

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.orbital.action.ActionResult
import com.orbital.action.DeviceAction
import com.orbital.action.DeviceActionExecutor
import com.orbital.data.ChatMessage
import com.orbital.data.LlmRepository
import com.orbital.data.ProviderType
import com.orbital.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ChatEngineTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeLlmRepository : LlmRepository() {
        var streamCompletionCalled = false
        var lastModel: String? = null
        var lastMessages: List<ChatMessage>? = null
        var onChunkCallback: ((String) -> Unit)? = null
        var onErrorCallback: ((Throwable) -> Unit)? = null

        override fun streamCompletion(
            model: String,
            messages: List<ChatMessage>,
            onChunk: (String) -> Unit,
            onComplete: () -> Unit,
            onError: (Throwable) -> Unit
        ) {
            streamCompletionCalled = true
            lastModel = model
            lastMessages = messages
            onChunkCallback = onChunk
            onErrorCallback = onError
        }

        override fun streamCompletion(
            model: String,
            messages: List<ChatMessage>,
            onChunk: (String) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            streamCompletion(model, messages, onChunk, {}, onError)
        }

        override fun getCurrentProviderType(): ProviderType? = ProviderType.GROQ
    }

    private class FakeVoiceManager(context: Context) : VoiceManager(context) {
        var spokenText: String? = null

        override fun speak(text: String, queueMode: Int) {
            spokenText = text
        }
    }

    private class FakeDeviceActionExecutor(context: Context) : DeviceActionExecutor(context) {
        var executedAction: DeviceAction? = null
        var returnResult: ActionResult = ActionResult.Success("Opened target app", "App launched successfully")

        override fun execute(action: DeviceAction): ActionResult {
            executedAction = action
            return returnResult
        }
    }

    private class FakeChatDao : com.orbital.data.db.ChatDao {
        val messages = mutableListOf<com.orbital.data.db.ChatMessageEntity>()

        override suspend fun insertMessage(message: com.orbital.data.db.ChatMessageEntity) {
            messages.add(message)
        }

        override suspend fun insertMessages(messages: List<com.orbital.data.db.ChatMessageEntity>) {
            this.messages.addAll(messages)
        }

        override suspend fun getMessagesSince(sinceTimestamp: Long): List<com.orbital.data.db.ChatMessageEntity> {
            return messages.filter { it.timestamp >= sinceTimestamp }
        }

        override fun observeMessagesSince(sinceTimestamp: Long): kotlinx.coroutines.flow.Flow<List<com.orbital.data.db.ChatMessageEntity>> {
            return kotlinx.coroutines.flow.flowOf(messages.filter { it.timestamp >= sinceTimestamp })
        }

        override suspend fun getAllMessages(): List<com.orbital.data.db.ChatMessageEntity> = messages.toList()

        override suspend fun getSessionMessages(sessionId: String): List<com.orbital.data.db.ChatMessageEntity> =
            messages.filter { it.sessionId == sessionId }

        override suspend fun renameSession(sessionId: String, title: String): Int {
            var changes = 0
            messages.replaceAll { message ->
                if (message.sessionId == sessionId) {
                    changes++
                    message.copy(sessionTitle = title)
                } else message
            }
            return changes
        }

        override suspend fun pruneOldMessages(cutoffTimestamp: Long): Int {
            val initial = messages.size
            messages.removeAll { it.timestamp < cutoffTimestamp }
            return initial - messages.size
        }

        override suspend fun clearAllMessages() {
            messages.clear()
        }
    }

    private lateinit var fakeLlmRepository: FakeLlmRepository
    private lateinit var fakeVoiceManager: FakeVoiceManager
    private lateinit var fakeDeviceActionExecutor: FakeDeviceActionExecutor
    private lateinit var chatHistoryRepository: com.orbital.data.db.ChatHistoryRepository
    private lateinit var chatEngine: DefaultChatEngine

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication().applicationContext

        fakeLlmRepository = FakeLlmRepository()
        fakeVoiceManager = FakeVoiceManager(context)
        fakeDeviceActionExecutor = FakeDeviceActionExecutor(context)
        chatHistoryRepository = com.orbital.data.db.ChatHistoryRepository(
            chatDao = FakeChatDao(),
            encryptionHelper = com.orbital.data.db.ChatEncryptionHelper()
        )

        chatEngine = DefaultChatEngine(
            context = context,
            llmRepository = fakeLlmRepository,
            voiceManager = fakeVoiceManager,
            deviceActionExecutor = fakeDeviceActionExecutor,
            chatHistoryRepository = chatHistoryRepository
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        try {
            Dispatchers.resetMain()
        } catch (_: Exception) {}
    }

    @Test
    fun initialValues_areEmptyOrFalse() {
        assertTrue(chatEngine.messages.value.isEmpty())
        assertEquals("", chatEngine.streamingContent.value)
        assertFalse(chatEngine.isStreaming.value)
        assertNull(chatEngine.activeProvider.value)
    }

    @Test
    fun sendMessage_addsUserMessageAndStartsStreaming() = runTest(testDispatcher) {
        val userPrompt = "Hello Orbital"

        chatEngine.sendMessage(userPrompt)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, chatEngine.messages.value.size)
        assertEquals("user", chatEngine.messages.value[0].role)
        assertEquals(userPrompt, chatEngine.messages.value[0].content)
        assertTrue(chatEngine.isStreaming.value)
        assertEquals("GROQ", chatEngine.activeProvider.value)

        assertTrue(fakeLlmRepository.streamCompletionCalled)
        assertEquals("auto", fakeLlmRepository.lastModel)
        assertNotNull(fakeLlmRepository.lastMessages)
        assertTrue(fakeLlmRepository.lastMessages!!.any { it.role == "system" })
        assertTrue(fakeLlmRepository.lastMessages!!.any { it.role == "user" && it.content == userPrompt })
    }

    @Test
    fun handleStreamCompletion_withAction_executesActionAndAppendsMessage() = runTest(testDispatcher) {
        val contentWithAction = "Opening the requested app for you.\n```action\n{\"action\": \"OPEN_APP\", \"target\": \"<installed-app>\"}\n```"
        chatEngine.streamingContent.value = contentWithAction
        chatEngine.isStreaming.value = true

        chatEngine.handleStreamCompletion()

        assertEquals(1, chatEngine.messages.value.size)
        val assistantMsg = chatEngine.messages.value[0]
        assertEquals("assistant", assistantMsg.role)
        assertTrue(assistantMsg.content?.contains("Opening the requested app for you") == true)
        assertEquals("⚡ Executed: Opened target app", assistantMsg.actionLabel)
        assertEquals("App launched successfully", assistantMsg.actionDetails)

        assertEquals("", chatEngine.streamingContent.value)
        assertFalse(chatEngine.isStreaming.value)
        assertNotNull(fakeDeviceActionExecutor.executedAction)
        assertEquals("OPEN_APP", fakeDeviceActionExecutor.executedAction?.action)
        assertEquals("<installed-app>", fakeDeviceActionExecutor.executedAction?.target)
    }

    @Test
    fun handleStreamCompletion_withoutAction_appendsPlainAssistantMessage() = runTest(testDispatcher) {
        val plainText = "The capital of France is Paris."
        chatEngine.streamingContent.value = plainText
        chatEngine.isStreaming.value = true

        chatEngine.handleStreamCompletion()

        assertEquals(1, chatEngine.messages.value.size)
        val assistantMsg = chatEngine.messages.value[0]
        assertEquals("assistant", assistantMsg.role)
        assertEquals(plainText, assistantMsg.content)
        assertNull(assistantMsg.actionLabel)
        assertNull(assistantMsg.actionDetails)

        assertEquals("", chatEngine.streamingContent.value)
        assertFalse(chatEngine.isStreaming.value)
        assertNull(fakeDeviceActionExecutor.executedAction)
    }

    @Test
    fun executeTTSAndActions_speaksLastAssistantMessage() = runTest(testDispatcher) {
        chatEngine.streamingContent.value = "Hello! How can I help you today?"
        chatEngine.handleStreamCompletion()

        chatEngine.executeTTSAndActions()

        assertEquals("Hello! How can I help you today?", fakeVoiceManager.spokenText)
    }

    @Test
    fun setCharacter_updatesCharacterPromptOnNextMessage() = runTest(testDispatcher) {
        chatEngine.setCharacter("lumina")
        chatEngine.sendMessage("Tell me a story")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeLlmRepository.streamCompletionCalled)
        val systemMessage = fakeLlmRepository.lastMessages?.firstOrNull { it.role == "system" }
        assertNotNull(systemMessage)
        assertTrue(systemMessage?.content?.contains("Lumina", ignoreCase = true) == true)
    }
}
