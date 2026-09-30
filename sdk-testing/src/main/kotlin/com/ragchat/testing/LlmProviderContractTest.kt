package com.ragchat.testing

import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared abstract contract test suite verifying compliant behavior across all [LlmProvider] implementations.
 */
public abstract class LlmProviderContractTest {
    /**
     * Factory creating the [LlmProvider] under test.
     */
    public abstract fun createProvider(): LlmProvider

    /**
     * Verifies provider capabilities are valid and non-empty.
     */
    @Test
    public fun testCapabilitiesAreValid() {
        val provider = createProvider()
        try {
            assertNotNull("Provider ID must not be null", provider.id)
            assertTrue("Context window must be positive", provider.capabilities.contextWindow > 0)
            assertTrue("Max output tokens must be positive", provider.capabilities.maxOutputTokens > 0)
            assertNotNull("Locality must be defined", provider.capabilities.locality)
        } finally {
            provider.close()
        }
    }

    /**
     * Verifies availability status can be retrieved.
     */
    @Test
    public fun testAvailabilityReporting(): Unit =
        runTest {
            val provider = createProvider()
            try {
                val availability = provider.availability()
                assertNotNull("Availability must not be null", availability)
                assertTrue(
                    "Availability must be a valid enum member",
                    availability in
                        listOf(
                            Availability.AVAILABLE,
                            Availability.DOWNLOAD_REQUIRED,
                            Availability.HARDWARE_UNSUPPORTED,
                            Availability.UNAVAILABLE,
                        ),
                )
            } finally {
                provider.close()
            }
        }

    /**
     * Verifies token counting produces positive count.
     */
    @Test
    public fun testCountTokensReturnsPositive(): Unit =
        runTest {
            val provider = createProvider()
            try {
                val count = provider.countTokens("Hello world, this is a prompt test.")
                assertTrue("Token count should be greater than zero", count > 0)
            } finally {
                provider.close()
            }
        }

    /**
     * Verifies stream generation produces token events and finishes with Done.
     */
    @Test
    public fun testGenerateEmitsTokensAndCompletes(): Unit =
        runTest {
            val provider = createProvider()
            try {
                val request =
                    LlmRequest(
                        messages =
                            listOf(
                                ChatMessage(
                                    id = "msg-1",
                                    role = MessageRole.USER,
                                    content = "Explain quantum computing briefly.",
                                ),
                            ),
                        temperature = 0.5f,
                        maxTokens = 64,
                    )

                val events = provider.generate(request).toList()
                assertFalse("Events should not be empty", events.isEmpty())

                val hasDone = events.any { it is LlmEvent.Done }
                val hasTokenOrMetadata = events.any { it is LlmEvent.Token || it is LlmEvent.Metadata }
                assertTrue("Must emit tokens or metadata", hasTokenOrMetadata)
                assertTrue("Stream must complete with Done event", hasDone)
            } finally {
                provider.close()
            }
        }

    /**
     * Verifies close is idempotent.
     */
    @Test
    public fun testCloseReleasesResourcesCleanly() {
        val provider = createProvider()
        provider.close()
        provider.close()
    }
}
