package com.mediaorganizer.app

import com.mediaorganizer.app.data.CleanupBatchEntity
import com.mediaorganizer.app.data.CleanupBatchWithRecords
import com.mediaorganizer.app.data.CleanupDao
import com.mediaorganizer.app.data.CleanupRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CleanupExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeCleanupDao : CleanupDao {
        private var nextBatchId = 1L
        private var nextRecordId = 1L
        private val batches = mutableListOf<CleanupBatchEntity>()
        private val records = mutableListOf<CleanupRecordEntity>()
        private val flow = MutableStateFlow<List<CleanupBatchWithRecords>>(emptyList())

        private fun snapshot(): List<CleanupBatchWithRecords> {
            return batches.sortedByDescending { it.timestamp }.map { b ->
                CleanupBatchWithRecords(b, records.filter { it.batchId == b.id })
            }
        }

        override suspend fun insertBatch(batch: CleanupBatchEntity): Long {
            val assigned = batch.copy(id = nextBatchId++)
            batches.add(assigned)
            flow.value = snapshot()
            return assigned.id
        }

        override suspend fun insertRecords(records: List<CleanupRecordEntity>) {
            records.forEach { this.records.add(it.copy(id = nextRecordId++)) }
            flow.value = snapshot()
        }

        override fun getAllBatchesFlow(): Flow<List<CleanupBatchWithRecords>> = flow
        override suspend fun getAllBatches(): List<CleanupBatchWithRecords> = snapshot()
        override suspend fun getLatestActiveBatch(): CleanupBatchWithRecords? =
            snapshot().firstOrNull { !it.batch.isUndone }
        override suspend fun getBatchById(batchId: Long): CleanupBatchWithRecords? =
            snapshot().firstOrNull { it.batch.id == batchId }

        override suspend fun markBatchUndone(batchId: Long, undoneAt: Long) {
            val idx = batches.indexOfFirst { it.id == batchId }
            if (idx >= 0) {
                batches[idx] = batches[idx].copy(isUndone = true, undoneAt = undoneAt)
                flow.value = snapshot()
            }
        }

        override suspend fun clearAllHistory() {
            batches.clear()
            records.clear()
            flow.value = emptyList()
        }
    }

    @Test
    fun cleanupRemovesJunkAndEmptyDirsAndUndoRestoresEverything() = runBlocking {
        val storageRoot = tempFolder.newFolder("StorageRoot")
        val trashRoot = tempFolder.newFolder("TrashRoot")

        val junkDir = File(storageRoot, "Download/.thumbnails").apply { mkdirs() }
        val junkFile = File(junkDir, "thumb_1.jpg").apply { writeText("thumb-bytes") }

        val emptyNestedParent = File(storageRoot, "EmptyParent")
        val emptyNestedChild = File(emptyNestedParent, "EmptyChild").apply { mkdirs() }
        val nonEmptyDir = File(storageRoot, "KeepDir").apply { mkdirs() }
        File(nonEmptyDir, "movie.mkv").writeText("keep")

        val dao = FakeCleanupDao()
        val execResult = CleanupExecutor.executeCleanup(
            junkPaths = listOf(junkDir.absolutePath),
            rootDir = storageRoot,
            trashRootDir = trashRoot,
            cleanupDao = dao
        )

        assertNotNull(execResult.batchId)
        assertEquals(1, execResult.removedJunkCount)
        assertEquals(3, execResult.deletedEmptyDirCount) // EmptyChild, EmptyParent, and now-empty Download!
        assertFalse(junkDir.exists())
        assertFalse(emptyNestedChild.exists())
        assertFalse(emptyNestedParent.exists())
        assertTrue(nonEmptyDir.exists())

        // Undo the cleanup batch
        val undoResult = CleanupExecutor.undoCleanupBatch(
            batchId = execResult.batchId!!,
            cleanupDao = dao
        )

        assertTrue(undoResult.markedUndone)
        assertEquals(1, undoResult.restoredJunkCount)
        assertEquals(3, undoResult.restoredEmptyDirCount)
        assertEquals(0, undoResult.errorCount)

        // Both junk folder + its contents AND empty folders are restored
        assertTrue(junkDir.exists())
        assertTrue(junkFile.exists())
        assertEquals("thumb-bytes", junkFile.readText())
        assertTrue(emptyNestedChild.exists())
        assertTrue(emptyNestedParent.exists())
        assertNull(dao.getLatestActiveBatch())

        // Clear history purges trash dir cleanly
        CleanupExecutor.clearCleanupHistory(dao, trashRoot)
        assertTrue(dao.getAllBatches().isEmpty())
        assertFalse(trashRoot.exists())
    }
}
