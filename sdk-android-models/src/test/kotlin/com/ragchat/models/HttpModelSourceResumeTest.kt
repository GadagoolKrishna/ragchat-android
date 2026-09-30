package com.ragchat.models

import android.content.ContextWrapper
import com.ragchat.models.catalog.ModelEntry
import com.ragchat.models.source.HttpModelSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HttpModelSourceResumeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun testResumableDownloadWithRangeHeader() =
        runBlocking {
            val totalPayload = "PART1_BYTES_DATA_AND_PART2_RESUMED_DATA"
            val totalBytes = totalPayload.toByteArray(Charsets.UTF_8)
            val part1 = "PART1_BYTES_DATA_"

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val range = request.getHeader("Range")
                        if (range != null && range.startsWith("bytes=")) {
                            val offset = range.removePrefix("bytes=").removeSuffix("-").toLong()
                            val remaining = totalPayload.substring(offset.toInt())
                            return MockResponse()
                                .setResponseCode(206)
                                .setHeader("Content-Range", "bytes $offset-${totalBytes.size - 1}/${totalBytes.size}")
                                .setBody(Buffer().writeUtf8(remaining))
                        }
                        return MockResponse()
                            .setResponseCode(200)
                            .setBody(Buffer().writeUtf8(totalPayload))
                    }
                }

            val stagingDir = tempFolder.newFolder("staging")
            val partFile = File(stagingDir, "test-model-1.0.0.part")
            partFile.writeText(part1) // Simulate partially downloaded part 1

            val entry =
                ModelEntry(
                    id = "test-model",
                    version = "1.0.0",
                    sizeBytes = totalBytes.size.toLong(),
                    sha256 = "dummy",
                    signature = "",
                    license = "Apache 2.0",
                    minRamBytes = 0,
                    minOsVersion = 26,
                    supportedAbis = emptyList(),
                    downloadUrl = server.url("/model.bin").toString(),
                )

            val context = ContextWrapper(null)
            val source = HttpModelSource(context = context, wifiOnly = false)

            var lastDownloaded = 0L
            val downloadedFile =
                source.fetch(entry, stagingDir) { downloaded, _ ->
                    lastDownloaded = downloaded
                }

            assertTrue(downloadedFile.exists())
            assertEquals(totalPayload, downloadedFile.readText())
            assertEquals(totalBytes.size.toLong(), lastDownloaded)

            val recorded = server.takeRequest()
            assertEquals("bytes=${part1.length}-", recorded.getHeader("Range"))
        }
}
