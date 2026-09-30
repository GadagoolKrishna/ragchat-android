package com.ragchat.ui.compose.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ragchat.api.document.DocumentManager
import com.ragchat.ui.compose.state.DocumentManagerUiState
import com.ragchat.ui.compose.state.DocumentStatus
import com.ragchat.ui.compose.state.DocumentUiItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder managing document catalog and ingestion states.
 */
public open class DocumentManagerViewModel(
    private val documentManager: DocumentManager,
    private val collectionId: String = "default_collection",
) : ViewModel() {
    private val _uiState = MutableStateFlow(DocumentManagerUiState())
    public val uiState: StateFlow<DocumentManagerUiState> = _uiState.asStateFlow()

    init {
        loadDocuments()
    }

    /**
     * Reloads all indexed documents.
     */
    public fun loadDocuments() {
        viewModelScope.launch {
            runCatching {
                documentManager.list(collectionId)
            }.onSuccess { list ->
                val items =
                    list.map { doc ->
                        DocumentUiItem(
                            id = doc.id,
                            name = doc.metadata["name"] ?: doc.id,
                            mimeType = doc.metadata["mimeType"] ?: "application/pdf",
                            sizeBytes = doc.metadata["size"]?.toLongOrNull() ?: 1024L,
                            status = DocumentStatus.INDEXED,
                            progressFraction = 1.0f,
                        )
                    }
                _uiState.update { it.copy(documents = items) }
            }
        }
    }

    /**
     * Prompts confirmation dialog for document deletion.
     */
    public fun requestDelete(docId: String) {
        _uiState.update { it.copy(deleteConfirmationDocId = docId) }
    }

    /**
     * Dismisses document deletion dialog.
     */
    public fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteConfirmationDocId = null) }
    }

    /**
     * Confirms and executes document removal.
     */
    public fun confirmDelete(docId: String) {
        viewModelScope.launch {
            runCatching {
                documentManager.remove(docId)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        documents = current.documents.filter { it.id != docId },
                        deleteConfirmationDocId = null,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(deleteConfirmationDocId = null) }
            }
        }
    }
}

/**
 * Pluggable factory for [DocumentManagerViewModel].
 */
public class DocumentManagerViewModelFactory(
    private val documentManagerProvider: () -> DocumentManager,
    private val collectionId: String = "default_collection",
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentManagerViewModel::class.java)) {
            return DocumentManagerViewModel(
                documentManager = documentManagerProvider(),
                collectionId = collectionId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
