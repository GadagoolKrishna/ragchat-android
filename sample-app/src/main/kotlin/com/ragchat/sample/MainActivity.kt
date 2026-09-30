package com.ragchat.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ragchat.sdk.RagChat
import com.ragchat.ui.compose.ChatView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RagChat.initialize()
        setContent {
            ChatView(title = "RagChat Sample App")
        }
    }
}
