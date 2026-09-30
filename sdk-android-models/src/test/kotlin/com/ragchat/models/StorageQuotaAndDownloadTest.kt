package com.ragchat.models

import android.content.Context
import android.content.ContextWrapper
import com.ragchat.api.error.SdkError
import com.ragchat.models.catalog.ModelEntry
import com.ragchat.models.source.ModelSource
import com.ragchat.models.storage.ModelStorageManager
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageQuotaAndDownloadTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var context: Context

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        context = ContextWrapper(null)
    }

    @Test
    fun testSuccessfulDownloadAndInstallPipeline() =
        runBlocking {
            val testContent = "Complete verified model weights 98765"
            val tempModelFile = tempFolder.newFile("source_model.bin").apply { writeText(testContent) }

            val fakeSource =
                object : ModelSource {
                    override val sourceName: String = "FakeSource"

                    override fun supports(entry: ModelEntry): Boolean = true

                    override suspend fun fetch(
                        entry: ModelEntry,
                        stagingDir: File,
                        onProgress: (Long, Long) -> Unit,
                    ): File {
                        val dest = File(stagingDir, "model.part")
                        tempModelFile.copyTo(dest, overwrite = true)
                        onProgress(dest.length(), dest.length())
                        return dest
                    }
                }

            val storageManager = ModelStorageManager(context, baseDirectory = filesDir)
            val manager =
                ModelDownloadManager(
                    context = context,
                    sources = listOf(fakeSource),
                    storageManager = storageManager,
                )

            val sha256 =
                com.ragchat.models.verifier
                    .IntegrityVerifier()
                    .computeSha256(tempModelFile)
            val entry =
                ModelEntry(
                    id = "fake-model",
                    version = "1.0.0",
                    sizeBytes = tempModelFile.length(),
                    sha256 = sha256,
                    signature = "",
                    license = "Apache 2.0",
                    minRamBytes = 0,
                    minOsVersion = 26,
                    supportedAbis = emptyList(),
                )

            val progressList = manager.downloadAndInstall(entry).toList()
            val lastEvent = progressList.last()
            assertEquals(ModelStage.READY, lastEvent.stage)

            val installedFile = manager.getInstalledModelPath("fake-model")
            assertTrue(installedFile != null && installedFile.exists())
            assertEquals(testContent, installedFile?.readText())
        }

    @Test
    fun testLowStorageRejection() =
        runBlocking {
            val failingStorage = LowStorageTestManager(context, filesDir)
            val manager = ModelDownloadManager(context = context, storageManager = failingStorage)

            val entry =
                ModelEntry(
                    id = "huge-model",
                    version = "1.0.0",
                    sizeBytes = 100_000_000_000L,
                    sha256 = "dummy",
                    signature = "",
                    license = "Apache 2.0",
                    minRamBytes = 0,
                    minOsVersion = 26,
                    supportedAbis = emptyList(),
                    downloadUrl = "https://example.com/huge.bin",
                )

            val progressList = manager.downloadAndInstall(entry).toList()
            val lastEvent = progressList.last()

            assertEquals(ModelStage.FAILED, lastEvent.stage)
            assertTrue(lastEvent.error is SdkError.StorageCryptoError)
        }
}

private class LowStorageTestManager(
    context: Context,
    dir: File,
) : ModelStorageManager(context, baseDirectory = dir) {
    override fun checkStorageQuota(requiredBytes: Long): Unit = throw SdkError.StorageCryptoError("INSUFFICIENT_STORAGE_QUOTA")
}
