package com.mediaorganizer.app

import com.mediaorganizer.app.data.BatchWithRecords
import com.mediaorganizer.app.data.MovedFileRecordEntity
import com.mediaorganizer.app.data.OrganizeBatchEntity
import com.mediaorganizer.app.data.TransactionDao
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

class MediaBatchExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeTransactionDao : TransactionDao {
        private var nextBatchId = 1L
        private var nextRecordId = 1L
        private val batches = mutableListOf<OrganizeBatchEntity>()
        private val records = mutableListOf<MovedFileRecordEntity>()
        private val flow = MutableStateFlow<List<BatchWithRecords>>(emptyList())

        private fun snapshot(): List<BatchWithRecords> {
            return batches.sortedByDescending { it.timestamp }.map { b ->
                BatchWithRecords(b, records.filter { it.batchId == b.id })
            }
        }

        override suspend fun insertBatch(batch: OrganizeBatchEntity): Long {
            val assigned = batch.copy(id = nextBatchId++)
            batches.add(assigned)
            flow.value = snapshot()
            return assigned.id
        }

        override suspend fun insertRecords(records: List<MovedFileRecordEntity>) {
            records.forEach { this.records.add(it.copy(id = nextRecordId++)) }
            flow.value = snapshot()
        }

        override fun getAllBatchesFlow(): Flow<List<BatchWithRecords>> = flow

        override suspend fun getAllBatches(): List<BatchWithRecords> = snapshot()

        override suspend fun getLatestActiveBatch(): BatchWithRecords? =
            snapshot().firstOrNull { !it.batch.isUndone }

        override suspend fun getBatchById(batchId: Long): BatchWithRecords? =
            snapshot().firstOrNull { it.batch.id == batchId }

        override suspend fun markBatchUndone(batchId: Long, undoneAt: Long) {
            val idx = batches.indexOfFirst { it.id == batchId }
            if (idx >= 0) {
                batches[idx] = batches[idx].copy(isUndone = true, undoneAt = undoneAt)
                flow.value = snapshot()
            }
        }

        override suspend fun deleteBatch(batchId: Long) {
            batches.removeAll { it.id == batchId }
            records.removeAll { it.batchId == batchId }
            flow.value = snapshot()
        }

        override suspend fun clearAllHistory() {
            batches.clear()
            records.clear()
            flow.value = emptyList()
        }
    }

    @Test
    fun executeBatchAndUndoRestoresVideosSubtitlesAndCleansEmptyTargetDirs() = runBlocking {
        val downloadDir = tempFolder.newFolder("Download")
        val movieDir = tempFolder.newFolder("Movies")
        val tvDir = tempFolder.newFolder("TV")

        val srcMovieDir = File(downloadDir, "InceptionRelease").apply { mkdirs() }
        val srcMovie = File(srcMovieDir, "Inception.2010.1080p.BluRay.mkv").apply { writeText("movie-bytes") }
        val srcMovieSub = File(srcMovieDir, "Inception.2010.1080p.BluRay.en.srt").apply { writeText("sub-en") }

        val srcTv = File(downloadDir, "Severance.S01E01.1080p.mkv").apply { writeText("tv-bytes") }
        val srcTvSub = File(downloadDir, "Severance.S01E01.1080p.es.srt").apply { writeText("sub-es") }

        val movieDest = File(movieDir, "Inception (2010).mkv")
        val tvSeasonDir = File(File(tvDir, "Severance"), "01")
        val tvDest = File(tvSeasonDir, "Severance S01E01.mkv")

        val previewItems = listOf(
            ScanPreviewItem(
                mediaItem = MediaScanner.MediaItem.Movie(
                    file = srcMovie,
                    originalName = srcMovie.name,
                    movieName = "Inception",
                    year = "2010",
                    companionSubtitles = listOf(srcMovieSub)
                ),
                proposedName = movieDest.name,
                proposedPath = movieDest.absolutePath
            ),
            ScanPreviewItem(
                mediaItem = MediaScanner.MediaItem.TvEpisode(
                    file = srcTv,
                    originalName = srcTv.name,
                    showName = "Severance",
                    season = 1,
                    episode = 1,
                    episodeEnd = null,
                    tag = "S01E01",
                    companionSubtitles = listOf(srcTvSub)
                ),
                proposedName = tvDest.name,
                proposedPath = tvDest.absolutePath
            )
        )

        val dao = FakeTransactionDao()
        val execResult = MediaBatchExecutor.executeBatch(
            items = previewItems,
            transactionDao = dao
        )

        assertNotNull(execResult.batchId)
        assertEquals(1, execResult.movieCount)
        assertEquals(1, execResult.tvCount)
        assertFalse(srcMovie.exists())
        assertFalse(srcMovieSub.exists())
        assertFalse(srcTv.exists())
        assertFalse(srcTvSub.exists())
        assertTrue(movieDest.exists())
        assertTrue(File(movieDir, "Inception (2010).en.srt").exists())
        assertTrue(tvDest.exists())
        assertTrue(File(tvSeasonDir, "Severance S01E01.es.srt").exists())

        // Undo the entire batch in one call
        val undoResult = MediaBatchExecutor.undoBatch(
            batchId = execResult.batchId!!,
            transactionDao = dao,
            movieDir = movieDir.absolutePath,
            tvDir = tvDir.absolutePath
        )

        assertTrue(undoResult.markedUndone)
        assertEquals(2, undoResult.restoredVideos)
        assertEquals(2, undoResult.restoredSubtitles)
        assertEquals(0, undoResult.errorCount)

        // All original files & subtitles are restored
        assertTrue(srcMovie.exists())
        assertEquals("movie-bytes", srcMovie.readText())
        assertTrue(srcMovieSub.exists())
        assertEquals("sub-en", srcMovieSub.readText())
        assertTrue(srcTv.exists())
        assertEquals("tv-bytes", srcTv.readText())
        assertTrue(srcTvSub.exists())
        assertEquals("sub-es", srcTvSub.readText())

        // Destination files & newly created empty show/season folders cleaned, root target dirs intact
        assertFalse(movieDest.exists())
        assertFalse(tvDest.exists())
        assertFalse(File(tvDir, "Severance").exists())
        assertTrue(movieDir.exists())
        assertTrue(tvDir.exists())
        assertNull(dao.getLatestActiveBatch())
    }
}
