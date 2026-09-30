package com.ragchat.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ragchat.api.config.ModelRoutingMode
import com.ragchat.api.config.RagChatConfigBuilder
import com.ragchat.sample.ui.DebugRoutingSheet
import com.ragchat.sample.ui.ModelsScreen
import com.ragchat.sample.ui.PrivacyScreen
import com.ragchat.sample.ui.SettingsScreen
import com.ragchat.sdk.RagChat
import com.ragchat.ui.compose.ChatView
import com.ragchat.ui.compose.components.DocumentManagerScreen
import com.ragchat.ui.compose.theme.RagChatTheme
import com.ragchat.ui.compose.viewmodel.ChatViewModel
import com.ragchat.ui.compose.viewmodel.ChatViewModelFactory
import com.ragchat.ui.compose.viewmodel.DocumentManagerViewModel
import com.ragchat.ui.compose.viewmodel.DocumentManagerViewModelFactory
import kotlinx.coroutines.runBlocking

/**
 * Navigation tabs for the sample application showcase.
 */
public enum class SampleDestination(
    public val label: String,
) {
    CHAT("Chat"),
    DOCS("Documents"),
    SETTINGS("Settings"),
    MODELS("Models"),
    PRIVACY("Privacy"),
}

/**
 * State holder bundling navigation and viewmodel references.
 */
public data class SampleAppState(
    public val destination: SampleDestination,
    public val routingMode: ModelRoutingMode,
    public val chatViewModel: ChatViewModel,
    public val docViewModel: DocumentManagerViewModel,
)

/**
 * Main activity demonstrating RagChat SDK features.
 */
public class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(chatManagerProvider = { RagChat.chat })
    }

    private val documentManagerViewModel: DocumentManagerViewModel by viewModels {
        DocumentManagerViewModelFactory(documentManagerProvider = { RagChat.documents })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 10-line integration initialization pattern
        runBlocking {
            RagChat.builder(applicationContext) {
                llm {
                    local()
                    routing = ModelRoutingMode.LOCAL_FIRST
                }
                embeddings { onDevice() }
                storage { encrypted() }
            }
            if (runCatching { RagChat.chat }.isFailure) {
                val dummyConfig = RagChatConfigBuilder().build()
                RagChat.initialize(applicationContext, dummyConfig)
            }
        }

        setContent {
            RagChatTheme {
                SampleAppScaffold(chatViewModel, documentManagerViewModel)
            }
        }
    }
}

@Composable
public fun SampleAppScaffold(
    chatViewModel: ChatViewModel,
    documentManagerViewModel: DocumentManagerViewModel,
) {
    var currentDestination by remember { mutableStateOf(SampleDestination.CHAT) }
    var currentRoutingMode by remember { mutableStateOf(ModelRoutingMode.LOCAL_FIRST) }
    var showDebugSheet by remember { mutableStateOf(false) }

    val appState =
        SampleAppState(
            destination = currentDestination,
            routingMode = currentRoutingMode,
            chatViewModel = chatViewModel,
            docViewModel = documentManagerViewModel,
        )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            SampleBottomNav(currentDestination) { currentDestination = it }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SampleContentArea(
                state = appState,
                onRoutingModeChanged = { currentRoutingMode = it },
                onNavigate = { currentDestination = it },
            )
        }
    }

    if (showDebugSheet) {
        DebugRoutingSheet(onDismiss = { showDebugSheet = false })
    }
}

@Composable
private fun SampleBottomNav(
    currentDestination: SampleDestination,
    onDestinationSelected: (SampleDestination) -> Unit,
) {
    NavigationBar {
        SampleDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = (currentDestination == dest),
                onClick = { onDestinationSelected(dest) },
                label = { Text(dest.label) },
                icon = {
                    Text(
                        text =
                            when (dest) {
                                SampleDestination.CHAT -> "💬"
                                SampleDestination.DOCS -> "📁"
                                SampleDestination.SETTINGS -> "⚙️"
                                SampleDestination.MODELS -> "🧠"
                                SampleDestination.PRIVACY -> "🛡️"
                            },
                    )
                },
            )
        }
    }
}

@Composable
private fun SampleContentArea(
    state: SampleAppState,
    onRoutingModeChanged: (ModelRoutingMode) -> Unit,
    onNavigate: (SampleDestination) -> Unit,
) {
    val docState by state.docViewModel.uiState.collectAsState()

    when (state.destination) {
        SampleDestination.CHAT ->
            ChatView(
                viewModel = state.chatViewModel,
                onManageDocsClick = { onNavigate(SampleDestination.DOCS) },
            )
        SampleDestination.DOCS ->
            DocumentManagerScreen(
                state = docState,
                onUploadClick = {},
                onDeleteClick = { state.docViewModel.requestDelete(it) },
                onConfirmDelete = { state.docViewModel.confirmDelete(it) },
                onDismissDeleteDialog = { state.docViewModel.dismissDeleteDialog() },
                onBackClick = { onNavigate(SampleDestination.CHAT) },
            )
        SampleDestination.SETTINGS -> SettingsScreen(state.routingMode, onRoutingModeChanged)
        SampleDestination.MODELS -> ModelsScreen()
        SampleDestination.PRIVACY -> PrivacyScreen()
    }
}
