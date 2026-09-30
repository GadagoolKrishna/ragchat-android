package com.ragchat.api

import com.ragchat.api.config.ModelRoutingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RagChatConfigTest {
    @Test
    fun testDefaultBuilderConfig() {
        val config =
            RagChat.builder {
                routingMode = ModelRoutingMode.LOCAL_FIRST
                defaultTopK = 10
            }

        assertNotNull(config)
        assertEquals(ModelRoutingMode.LOCAL_FIRST, config.routingMode)
        assertEquals(10, config.defaultTopK)
        assertEquals(30_000L, config.requestTimeoutMs)
    }
}
