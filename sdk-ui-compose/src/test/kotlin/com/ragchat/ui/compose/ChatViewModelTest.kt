package com.ragchat.ui.compose

import com.ragchat.api.chat.ChatEvent
import com.ragchat.api.chat.ChatManager
import com.ragchat.api.chat.ChatOptions
import com.ragchat.api.model.Answer
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.Citation
import com.ragchat.api.model.Locality
import com.ragchat.ui.compose.state.MessageDeliveryStatus
import com.ragchat.ui.compose.state.MessageRole
import com.ragchat.ui.compose.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSendMessageAndStreamingTokens() =
        runTest {
            val fakeManager =
                object : ChatManager {
                    override fun ask(
                        query: String,
                        options: ChatOptions,
                    ): Flow<ChatEvent> =
                        flow {
                            emit(ChatEvent.Token("Hello "))
                            emit(ChatEvent.Token("World!"))
                            emit(
                                ChatEvent.Done(
                                    answer =
                                        Answer(
                                            text = "Hello World!",
                                            citations = listOf(Citation("doc_1", "chunk_1", "chunk 1", 1)),
                                            confidence = 0.95f,
                                            modelUsed = "gemini-nano",
                                            locality = Locality.LOCAL,
                                        ),
                                ),
                            )
                        }

                    override suspend fun getHistory(
                        workspaceId: String,
                        sessionId: String,
                    ): List<ChatMessage> = emptyList()

                    override suspend fun clearHistory(
                        workspaceId: String,
                        sessionId: String,
                    ) {
                        // No-op for test
                    }
                }

            val viewModel = ChatViewModel(fakeManager)
            viewModel.sendMessage("Hi")

            assertEquals(2, viewModel.uiState.value.messages.size)
            assertTrue(viewModel.uiState.value.isGenerating)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertEquals(2, state.messages.size)

            val userMessage = state.messages[0]
            assertEquals(MessageRole.USER, userMessage.role)
            assertEquals("Hi", userMessage.content)

            val assistantMessage = state.messages[1]
            assertEquals(MessageRole.ASSISTANT, assistantMessage.role)
            assertEquals("Hello World!", assistantMessage.content)
            assertEquals(1, assistantMessage.citations.size)
            assertEquals(MessageDeliveryStatus.COMPLETE, assistantMessage.status)
        }

    @Test
    fun testStopGenerating() =
        runTest {
            val fakeManager =
                object : ChatManager {
                    override fun ask(
                        query: String,
                        options: ChatOptions,
                    ): Flow<ChatEvent> =
                        flow {
                            emit(ChatEvent.Token("Chunk 1"))
                            kotlinx.coroutines.delay(10_000)
                            emit(ChatEvent.Token("Chunk 2"))
                        }

                    override suspend fun getHistory(
                        workspaceId: String,
                        sessionId: String,
                    ): List<ChatMessage> = emptyList()

                    override suspend fun clearHistory(
                        workspaceId: String,
                        sessionId: String,
                    ) {
                        // No-op for test
                    }
                }

            val viewModel = ChatViewModel(fakeManager)
            viewModel.sendMessage("Question")

            testDispatcher.scheduler.advanceTimeBy(100)
            assertTrue(viewModel.uiState.value.isGenerating)

            viewModel.stopGenerating()
            assertFalse(viewModel.uiState.value.isGenerating)

            val assistantMessage =
                viewModel.uiState.value.messages
                    .last()
            assertEquals(MessageDeliveryStatus.COMPLETE, assistantMessage.status)
        }

    @Test
    fun testSelectCitationAndFeedback() =
        runTest {
            val fakeManager =
                object : ChatManager {
                    override fun ask(
                        query: String,
                        options: ChatOptions,
                    ): Flow<ChatEvent> = flow {}

                    override suspend fun getHistory(
                        workspaceId: String,
                        sessionId: String,
                    ): List<ChatMessage> = emptyList()

                    override suspend fun clearHistory(
                        workspaceId: String,
                        sessionId: String,
                    ) {
                        // No-op for test
                    }
                }

            val viewModel = ChatViewModel(fakeManager)
            val citation = Citation("doc_1", "chunk_1", "Text", 2)
            viewModel.selectCitation(citation)

            assertEquals(citation, viewModel.uiState.value.activeCitation)

            viewModel.selectCitation(null)
            assertEquals(null, viewModel.uiState.value.activeCitation)
        }
}
