package com.ragchat.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.ragchat.api.config.RagChatConfigBuilder
import com.ragchat.sdk.RagChat
import com.ragchat.ui.compose.ChatView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            RagChat.initialize(
                context = applicationContext,
                config = RagChatConfigBuilder().build(),
            )
        }
        setContent {
            ChatView(title = "RagChat Sample App")
        }
    }
}
