package com.ragchat.ui.compose

import com.ragchat.api.document.DocumentManager
import com.ragchat.api.model.Document
import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.OverallIngestionProgress
import com.ragchat.api.parser.DocumentSource
import com.ragchat.ui.compose.viewmodel.DocumentManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentManagerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadDocumentsAndDeletionFlow() =
        runTest {
            val doc1 =
                Document(
                    id = "doc_1",
                    uri = null,
                    mimeType = "application/pdf",
                    checksum = "sha1",
                    sizeBytes = 2048L,
                    metadata = mapOf("name" to "Contract.pdf"),
                )
            val doc2 =
                Document(
                    id = "doc_2",
                    uri = null,
                    mimeType = "application/pdf",
                    checksum = "sha2",
                    sizeBytes = 4096L,
                    metadata = mapOf("name" to "Manual.pdf"),
                )
            val docList = mutableListOf(doc1, doc2)

            val fakeDocManager =
                object : DocumentManager {
                    override suspend fun add(
                        source: DocumentSource,
                        collectionId: String,
                        metadata: Map<String, String>,
                    ): Flow<IngestionProgress> = emptyFlow()

                    override suspend fun add(
                        file: File,
                        mimeType: String,
                        collectionId: String,
                        metadata: Map<String, String>,
                    ): Flow<IngestionProgress> = emptyFlow()

                    override suspend fun add(
                        stream: InputStream,
                        name: String,
                        mimeType: String,
                        collectionId: String,
                        metadata: Map<String, String>,
                    ): Flow<IngestionProgress> = emptyFlow()

                    override suspend fun list(collectionId: String?): List<Document> = docList

                    override suspend fun status(documentId: String): IngestionProgress? = null

                    override suspend fun remove(documentId: String) {
                        docList.removeAll { it.id == documentId }
                    }

                    override suspend fun reindexAll(collectionId: String): Flow<OverallIngestionProgress> = emptyFlow()
                }

            val viewModel = DocumentManagerViewModel(fakeDocManager, "col_1")
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.documents.size)

            viewModel.requestDelete("doc_1")
            assertEquals("doc_1", viewModel.uiState.value.deleteConfirmationDocId)

            viewModel.confirmDelete("doc_1")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.documents.size)
            assertEquals(
                "doc_2",
                viewModel.uiState.value.documents[0]
                    .id,
            )
            assertEquals(null, viewModel.uiState.value.deleteConfirmationDocId)
        }
}
