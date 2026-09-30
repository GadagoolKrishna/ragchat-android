package com.ragchat.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.ingestion.IngestionNotificationInfo
import com.ragchat.api.ingestion.IngestionNotificationProvider
import com.ragchat.api.model.Document
import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.IngestionStage
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.api.storage.VectorStore
import com.ragchat.ingestion.IngestionJobParams
import com.ragchat.ingestion.IngestionPipeline
import com.ragchat.ingestion.backpressure.BackpressureController
import com.ragchat.ingestion.checkpoint.InMemoryIngestionCheckpointStore
import com.ragchat.ingestion.checkpoint.IngestionCheckpointStore
import com.ragchat.work.thermal.AndroidThermalStatusProvider
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Android WorkManager [CoroutineWorker] executing resilient document ingestion.
 *
 * Automatically promotes long-running jobs to an Android Foreground Service using
 * the host-supplied [IngestionNotificationProvider], and monitors thermal status
 * via [AndroidThermalStatusProvider].
 */
public class DocumentIngestionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID)
        val collectionId = inputData.getString(KEY_COLLECTION_ID)
        val engine = workerEngineHolder

        if (documentId == null || collectionId == null || engine == null) {
            return Result.failure()
        }

        return executePipelineWork(documentId, collectionId, engine)
    }

    private suspend fun executePipelineWork(
        documentId: String,
        collectionId: String,
        engine: WorkerEngineConfiguration,
    ): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "text/plain"

        val source =
            if (filePath != null) {
                FileDocumentSource(File(filePath), mimeType)
            } else {
                engine.sourceProvider?.invoke(documentId)
            } ?: return Result.failure()

        val notificationProvider = engine.notificationProvider
        val thermalProvider = AndroidThermalStatusProvider(applicationContext)
        val backpressure = BackpressureController(thermalProvider)

        val jobParams =
            IngestionJobParams(
                document = Document(id = documentId, mimeType = mimeType),
                source = source,
                collectionId = collectionId,
                parsers = engine.parsers,
                chunker = engine.chunker,
                embeddingProvider = engine.embeddingProvider,
                vectorStore = engine.vectorStore,
                checkpointStore = engine.checkpointStore,
                backpressureController = backpressure,
            )

        val pipeline = IngestionPipeline()
        activePipelines[documentId] = pipeline

        return try {
            pipeline.ingest(jobParams).collect { progress ->
                engine.progressListener?.invoke(progress)

                // Foreground promotion
                if (notificationProvider != null && progress.stage != IngestionStage.DONE && progress.stage != IngestionStage.FAILED) {
                    val notifInfo = notificationProvider.getNotificationInfo(progress)
                    val foregroundInfo = createForegroundInfo(notifInfo)
                    setForeground(foregroundInfo)
                }

                if (isStopped) {
                    pipeline.cancel(documentId)
                }
            }

            Result.success()
        } catch (_: Exception) {
            if (isStopped) Result.retry() else Result.failure()
        } finally {
            activePipelines.remove(documentId)
            source.close()
        }
    }

    private fun createForegroundInfo(info: IngestionNotificationInfo): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    info.channelId,
                    info.channelName,
                    NotificationManager.IMPORTANCE_LOW,
                )
            notificationManager.createNotificationChannel(channel)
        }

        val builder =
            NotificationCompat
                .Builder(applicationContext, info.channelId)
                .setContentTitle(info.title)
                .setContentText(info.contentText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(info.maxProgress, info.currentProgress, info.isIndeterminate)

        val notification = builder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(info.notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(info.notificationId, notification)
        }
    }

    /**
     * File-backed [DocumentSource] implementation.
     */
    public class FileDocumentSource(
        private val file: File,
        override val mimeType: String,
    ) : DocumentSource {
        override val sizeBytes: Long get() = file.length()

        override fun openStream(): InputStream = FileInputStream(file)

        override fun close() {
            // No-op for file stream
        }
    }

    public companion object {
        public const val KEY_DOCUMENT_ID: String = "key_document_id"
        public const val KEY_COLLECTION_ID: String = "key_collection_id"
        public const val KEY_FILE_PATH: String = "key_file_path"
        public const val KEY_MIME_TYPE: String = "key_mime_type"

        internal var workerEngineHolder: WorkerEngineConfiguration? = null
        private val activePipelines = ConcurrentHashMap<String, IngestionPipeline>()

        /**
         * Cancels an actively running pipeline worker.
         */
        public fun cancelPipeline(documentId: String) {
            activePipelines[documentId]?.cancel(documentId)
        }
    }
}

/**
 * Engine configuration injected into background workers.
 */
public data class WorkerEngineConfiguration(
    val parsers: List<DocumentParser>,
    val chunker: Chunker,
    val embeddingProvider: EmbeddingProvider,
    val vectorStore: VectorStore,
    val checkpointStore: IngestionCheckpointStore = InMemoryIngestionCheckpointStore(),
    val notificationProvider: IngestionNotificationProvider? = null,
    val sourceProvider: ((documentId: String) -> DocumentSource?)? = null,
    val progressListener: ((progress: IngestionProgress) -> Unit)? = null,
)
