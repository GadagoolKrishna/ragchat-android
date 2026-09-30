package com.ragchat.testing

import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.Locality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test fake implementation of [LlmProvider].
 */
public class FakeLlmProvider(
    override val id: String = "fake-llm",
    public val responseTokens: List<String> = listOf("Hello", " from", " FakeLlmProvider!"),
    public var currentAvailability: Availability = Availability.AVAILABLE,
    override val capabilities: LlmCapabilities =
        LlmCapabilities(
            contextWindow = 4096,
            maxOutputTokens = 1024,
            streaming = true,
            toolCalling = false,
            multimodal = false,
            locality = Locality.LOCAL,
        ),
) : LlmProvider {
    private val isClosed = AtomicBoolean(false)

    /**
     * Records requests received by this fake provider.
     */
    public val recordedRequests: MutableList<LlmRequest> = mutableListOf()

    override suspend fun availability(): Availability = currentAvailability

    override fun generate(request: LlmRequest): Flow<LlmEvent> =
        flow {
            check(!isClosed.get()) { "FakeLlmProvider is closed" }
            recordedRequests.add(request)
            for (token in responseTokens) {
                emit(LlmEvent.Token(token))
            }
            emit(LlmEvent.Metadata(promptTokens = 10, candidateTokens = responseTokens.size))
            emit(LlmEvent.Done)
        }

    override suspend fun countTokens(text: String): Int = text.split("\\s+".toRegex()).size

    override fun close() {
        isClosed.set(true)
    }
}
