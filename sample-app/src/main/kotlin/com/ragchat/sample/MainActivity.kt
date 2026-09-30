package com.ragchat.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.ragchat.api.config.RagChatConfigBuilder
import com.ragchat.sdk.RagChat
import com.ragchat.ui.compose.ChatView
import com.ragchat.ui.compose.theme.RagChatTheme
import com.ragchat.ui.compose.viewmodel.ChatViewModel
import com.ragchat.ui.compose.viewmodel.ChatViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            chatManagerProvider = { RagChat.chat },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            RagChat.initialize(
                context = applicationContext,
                config = RagChatConfigBuilder().build(),
            )
        }
        setContent {
            RagChatTheme {
                ChatView(viewModel = chatViewModel)
            }
        }
    }
}
