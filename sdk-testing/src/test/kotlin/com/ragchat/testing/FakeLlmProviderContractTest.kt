package com.ragchat.testing

import com.ragchat.api.llm.LlmProvider

class FakeLlmProviderContractTest : LlmProviderContractTest() {
    override fun createProvider(): LlmProvider = FakeLlmProvider()
}
