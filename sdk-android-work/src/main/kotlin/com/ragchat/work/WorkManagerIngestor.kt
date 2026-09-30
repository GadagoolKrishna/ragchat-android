package com.ragchat.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.IngestionStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import java.io.File

/**
 * Host constraints guiding background document ingestion execution.
 *
 * @property requiresCharging Whether ingestion only runs when the device is plugged in.
 * @property requiresBatteryNotLow Whether ingestion pauses if the battery drops below low threshold.
 * @property requiresUnmeteredNetwork Whether cloud-dependent embeddings require Wi-Fi.
 * @property requiresDeviceIdle Whether execution is deferred until device is idle.
 */
public data class IngestionConstraints(
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = true,
    val requiresUnmeteredNetwork: Boolean = false,
    val requiresDeviceIdle: Boolean = false,
)

/**
 * Background document ingestion coordinator leveraging Android [WorkManager].
 */
public class WorkManagerIngestor(
    private val context: Context,
    private val engineConfig: WorkerEngineConfiguration,
    private val constraints: IngestionConstraints = IngestionConstraints(),
) {
    private val workManager = WorkManager.getInstance(context)
    private val internalProgressFlow = MutableSharedFlow<IngestionProgress>(replay = 1)

    init {
        DocumentIngestionWorker.workerEngineHolder =
            engineConfig.copy(
                progressListener = { progress ->
                    internalProgressFlow.tryEmit(progress)
                    engineConfig.progressListener?.invoke(progress)
                },
            )
    }

    /**
     * Enqueues a document for background ingestion.
     *
     * @param documentId Unique identifier of the document.
     * @param collectionId Target collection.
     * @param file Local file reference.
     * @param mimeType Document MIME type.
     * @return Hot [Flow] observing ingestion progress events.
     */
    public fun enqueue(
        documentId: String,
        collectionId: String,
        file: File,
        mimeType: String,
    ): Flow<IngestionProgress> {
        val workConstraints =
            Constraints
                .Builder()
                .setRequiresCharging(constraints.requiresCharging)
                .setRequiresBatteryNotLow(constraints.requiresBatteryNotLow)
                .setRequiredNetworkType(
                    if (constraints.requiresUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED,
                ).setRequiresDeviceIdle(constraints.requiresDeviceIdle)
                .build()

        val inputData =
            Data
                .Builder()
                .putString(DocumentIngestionWorker.KEY_DOCUMENT_ID, documentId)
                .putString(DocumentIngestionWorker.KEY_COLLECTION_ID, collectionId)
                .putString(DocumentIngestionWorker.KEY_FILE_PATH, file.absolutePath)
                .putString(DocumentIngestionWorker.KEY_MIME_TYPE, mimeType)
                .build()

        val workRequest =
            OneTimeWorkRequestBuilder<DocumentIngestionWorker>()
                .setConstraints(workConstraints)
                .setInputData(inputData)
                .addTag("ragchat_doc_$documentId")
                .build()

        workManager.enqueueUniqueWork(
            "ragchat_ingest_$documentId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )

        return internalProgressFlow.asSharedFlow()
    }

    /**
     * Observes live [WorkInfo] state from WorkManager mapped to [IngestionProgress].
     */
    public fun observeWorkProgress(documentId: String): Flow<IngestionProgress> =
        workManager
            .getWorkInfosForUniqueWorkFlow("ragchat_ingest_$documentId")
            .mapNotNull { workInfoList ->
                val workInfo = workInfoList.firstOrNull() ?: return@mapNotNull null
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        IngestionProgress(documentId, IngestionStage.QUEUED, 0, 1, 0.05f)
                    WorkInfo.State.RUNNING ->
                        IngestionProgress(documentId, IngestionStage.INDEXING, 1, 1, 0.75f)
                    WorkInfo.State.SUCCEEDED ->
                        IngestionProgress(documentId, IngestionStage.DONE, 1, 1, 1.0f)
                    WorkInfo.State.FAILED ->
                        IngestionProgress(documentId, IngestionStage.FAILED, errorCategory = "WORK_FAILED")
                    WorkInfo.State.CANCELLED ->
                        IngestionProgress(documentId, IngestionStage.CANCELLED, errorCategory = "WORK_CANCELLED")
                }
            }

    /**
     * Cancels an ongoing background ingestion job.
     */
    public fun cancel(documentId: String) {
        DocumentIngestionWorker.cancelPipeline(documentId)
        workManager.cancelUniqueWork("ragchat_ingest_$documentId")
    }

    /**
     * Cancels all scheduled or active background ingestion jobs.
     */
    public fun cancelAll() {
        workManager.cancelAllWorkByTag("ragchat_doc_")
    }
}
