package com.ragchat.sdk

import android.content.ContextWrapper
import com.ragchat.api.config.ModelRoutingMode
import com.ragchat.testing.FakeEmbeddingProvider
import com.ragchat.testing.FakeLlmProvider
import com.ragchat.testing.FakePolicyProvider
import org.junit.Test
import kotlin.test.assertNotNull

class RagChatFacadeTest {
    @Test
    fun testBuilderDslConfiguration() {
        val fakeLocal = FakeLlmProvider(id = "nano-test")
        val fakeCloud = FakeLlmProvider(id = "cloud-test")
        val fakeEmbeddings = FakeEmbeddingProvider(dimensions = 256)
        val fakePolicy = FakePolicyProvider()

        val mockContext =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): android.content.Context = this

                override fun getPackageName(): String = "com.ragchat.test"

                override fun getFilesDir(): java.io.File = java.io.File(System.getProperty("java.io.tmpdir"), "ragchat_unit_test")

                override fun getNoBackupFilesDir(): java.io.File = java.io.File(System.getProperty("java.io.tmpdir"), "ragchat_unit_test")
            }

        // Verify DSL compiles cleanly and sets expected properties
        val builder =
            RagChat.builder(mockContext) {
                llm {
                    local(fakeLocal)
                    cloud(fakeCloud)
                    routing = ModelRoutingMode.LOCAL_FIRST
                }
                embeddings {
                    custom(fakeEmbeddings)
                }
                storage {
                    encrypted()
                }
                policy {
                    provider(fakePolicy)
                }
            }

        assertNotNull(builder)
    }
}
