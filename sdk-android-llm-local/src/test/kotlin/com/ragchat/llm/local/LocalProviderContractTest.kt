package com.ragchat.llm.local

import android.content.Context
import android.content.ContextWrapper
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.MessageRole
import com.ragchat.llm.local.lifecycle.LocalLlmMemoryManager
import com.ragchat.llm.local.litert.GemmaLiteRtProvider
import com.ragchat.llm.local.nano.AiCoreAvailabilityChecker
import com.ragchat.llm.local.nano.GeminiNanoProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalProviderContractTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var fakeChecker: AiCoreAvailabilityChecker

    @Before
    fun setUp() {
        mockContext = ContextWrapper(null)

        fakeChecker =
            object : AiCoreAvailabilityChecker(mockContext) {
                override fun checkAvailability(): Availability = Availability.AVAILABLE
            }
    }

    @Test
    fun testGeminiNanoProviderContract() =
        runTest {
            val provider =
                GeminiNanoProvider(
                    context = mockContext,
                    availabilityChecker = fakeChecker,
                )

            assertEquals("gemini-nano", provider.id)
            assertTrue(provider.capabilities.streaming)
            assertEquals(Availability.AVAILABLE, provider.availability())

            val request =
                LlmRequest(
                    messages =
                        listOf(
                            ChatMessage(id = "1", role = MessageRole.USER, content = "Summarize RAG architecture"),
                        ),
                )

            val events = provider.generate(request).toList()
            assertTrue(events.isNotEmpty())
            assertTrue(events.any { it is LlmEvent.Token })
            assertTrue(events.any { it is LlmEvent.Metadata })
            assertTrue(events.any { it is LlmEvent.Done })

            val tokenCount = provider.countTokens("Hello world")
            assertTrue(tokenCount > 0)
            provider.close()
        }

    @Test
    fun testGemmaLiteRtProviderAvailabilityAndUnload() =
        runTest {
            val modelFile = tempFolder.newFile("gemma-2b-it.litertlm")
            modelFile.writeBytes(ByteArray(1024)) // Non-empty fake model

            val provider =
                GemmaLiteRtProvider(
                    context = mockContext,
                    modelPath = modelFile.absolutePath,
                    useGpu = false,
                )

            assertEquals("gemma-litert", provider.id)
            assertEquals(Availability.AVAILABLE, provider.availability())

            val memoryManager = LocalLlmMemoryManager()
            memoryManager.register(provider)
            memoryManager.onLowMemory()

            // Unload test
            provider.unload()
            provider.close()
        }

    @Test
    fun testGemmaMissingModelReportsDownloadRequired() =
        runTest {
            val missingFile = File(tempFolder.root, "non_existent.litertlm")
            val provider =
                GemmaLiteRtProvider(
                    context = mockContext,
                    modelPath = missingFile.absolutePath,
                    useGpu = false,
                )

            assertEquals(Availability.DOWNLOAD_REQUIRED, provider.availability())
            provider.close()
        }
}
