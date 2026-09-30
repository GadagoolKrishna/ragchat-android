package com.ragchat.models

import android.content.ContextWrapper
import com.ragchat.models.storage.ModelStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicInstallAndRollbackTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var storageManager: ModelStorageManager

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        val context = ContextWrapper(null)
        storageManager = ModelStorageManager(context, baseDirectory = filesDir)
    }

    @Test
    fun testInstallStagedModelPromotesFileAndSetsActiveVersion() {
        val stagingDir = storageManager.getStagingDirectory("gemma", "1.0.0")
        val stagedFile = File(stagingDir, "model.part")
        stagedFile.writeText("Gemma model v1 weights")

        val installedFile = storageManager.installStagedModel("gemma", "1.0.0", stagedFile)
        assertTrue(installedFile.exists())
        assertEquals("Gemma model v1 weights", installedFile.readText())
        assertEquals("1.0.0", storageManager.getActiveVersion("gemma"))
        assertNotNull(storageManager.getActiveModelFile("gemma"))
    }

    @Test
    fun testRollbackRevertsToPreviousVersion() {
        // Install v1.0.0
        val stage1 = storageManager.getStagingDirectory("gemma", "1.0.0")
        val staged1 = File(stage1, "model.part").apply { writeText("V1 weights") }
        storageManager.installStagedModel("gemma", "1.0.0", staged1)

        // Install v2.0.0
        val stage2 = storageManager.getStagingDirectory("gemma", "2.0.0")
        val staged2 = File(stage2, "model.part").apply { writeText("V2 weights") }
        storageManager.installStagedModel("gemma", "2.0.0", staged2)

        assertEquals("2.0.0", storageManager.getActiveVersion("gemma"))

        // Perform rollback
        val rolledBack = storageManager.rollback("gemma")
        assertTrue(rolledBack)
        assertEquals("1.0.0", storageManager.getActiveVersion("gemma"))
        assertEquals("V1 weights", storageManager.getActiveModelFile("gemma")?.readText())
    }

    @Test
    fun testGarbageCollectionPurgesUnpinnedOldVersions() {
        // Install v1.0.0
        val stage1 = storageManager.getStagingDirectory("gemma", "1.0.0")
        val staged1 = File(stage1, "model.part").apply { writeText("V1") }
        storageManager.installStagedModel("gemma", "1.0.0", staged1)

        // Install v2.0.0
        val stage2 = storageManager.getStagingDirectory("gemma", "2.0.0")
        val staged2 = File(stage2, "model.part").apply { writeText("V2") }
        storageManager.installStagedModel("gemma", "2.0.0", staged2)

        // Install v3.0.0 (active)
        val stage3 = storageManager.getStagingDirectory("gemma", "3.0.0")
        val staged3 = File(stage3, "model.part").apply { writeText("V3") }
        storageManager.installStagedModel("gemma", "3.0.0", staged3)

        // Pin v1.0.0
        storageManager.pinVersion("gemma", "1.0.0")

        // GC: v2.0.0 should be purged; v1.0.0 (pinned) and v3.0.0 (active) must remain
        storageManager.garbageCollect("gemma", keepPinned = true)

        val baseDir = File(filesDir, "gemma")
        assertTrue(File(baseDir, "1.0.0").exists()) // pinned
        assertFalse(File(baseDir, "2.0.0").exists()) // purged
        assertTrue(File(baseDir, "3.0.0").exists()) // active
    }
}
