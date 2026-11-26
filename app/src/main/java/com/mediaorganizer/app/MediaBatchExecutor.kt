package com.mediaorganizer.app

import android.content.Context
import com.mediaorganizer.app.MediaScanner.MediaItem
import com.mediaorganizer.app.data.AppDatabase
import com.mediaorganizer.app.data.MovedFileRecordEntity
import com.mediaorganizer.app.data.OrganizeBatchEntity
import com.mediaorganizer.app.data.RoomJsonConverters
import com.mediaorganizer.app.data.SubtitleMovePair
import com.mediaorganizer.app.data.TransactionDao
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class BatchExecutionResult(
    val batchId: Long?,
    val movieCount: Int,
    val tvCount: Int,
    val skippedMovieCount: Int,
    val skippedTvCount: Int,
    val failedCount: Int,
    val elapsedMillis: Long
)

data class UndoResult(
    val batchId: Long,
    val restoredVideos: Int,
    val restoredSubtitles: Int,
    val skippedMissingOrConflict: Int,
    val errorCount: Int,
    val markedUndone: Boolean
)

object MediaBatchExecutor {

    suspend fun buildPreviewItems(
        rawItems: List<MediaItem>,
        config: AppConfig,
        context: Context? = null,
        onProgress: (Float) -> Unit = {},
        onLog: (String) -> Unit = {}
    ): List<ScanPreviewItem> {
        val db = context?.let { AppDatabase.getInstance(it) }
        val cacheDao = db?.metadataCacheDao()
        val ttlMillis = MediaTitleFetcher.DEFAULT_TTL_MILLIS

        val previewList = mutableListOf<ScanPreviewItem>()
        val resolvedEpisodeTitles = mutableMapOf<MediaItem.TvEpisode, String>()

        val tvItems = rawItems.filterIsInstance<MediaItem.TvEpisode>()
        val tvGroups = tvItems.groupBy { Pair(it.showName, it.season) }
        val totalGroups = tvGroups.size.coerceAtLeast(1)
        var groupIdx = 0

        for ((key, itemsInSeason) in tvGroups) {
            val (showName, season) = key
            val episodeNumbers = mutableSetOf<Int>()
            for (item in itemsInSeason) {
                episodeNumbers.add(item.episode)
                if (item.episodeEnd != null) {
                    for (n in item.episode..item.episodeEnd) {
                        episodeNumbers.add(n)
                    }
                }
            }

            val sortedEpNums = episodeNumbers.sorted()
            onLog("Fetching titles for $showName Season $season...")
            val seasonMap = try {
                MediaTitleFetcher.getSeasonEpisodeMap(
                    showName = showName,
                    season = season,
                    episodeNumbers = sortedEpNums,
                    wikiFirst = config.wikiFirst,
                    cacheDao = cacheDao,
                    ttlMillis = ttlMillis,
                    onLog = onLog
                )
            } catch (e: Exception) {
                onLog("⚠️ Could not fetch titles for $showName: ${e.message}")
                emptyMap()
            }

            val groupResolved = MediaTitleFetcher.resolveEpisodeTitlesForSeason(
                itemsInSeason = itemsInSeason,
                seasonMap = seasonMap,
                onLog = onLog
            )
            resolvedEpisodeTitles.putAll(groupResolved)

            groupIdx++
            onProgress(0.3f + (groupIdx.toFloat() / totalGroups.toFloat()) * 0.5f)
        }

        for ((idx, item) in rawItems.withIndex()) {
            when (item) {
                is MediaItem.Movie -> {
                    val ext = item.file.extension
                    val resolvedTitle = MovieMetadataFetcher.resolveMovieTitle(
                        parsedTitle = item.movieName,
                        year = item.year,
                        onlineLookupEnabled = config.movieLookupOnline,
                        cacheDao = cacheDao,
                        ttlMillis = ttlMillis,
                        onLog = onLog
                    )
                    val destName = NamingTemplateFormatter.formatMovieFilename(
                        template = config.movieTemplate,
                        movieName = resolvedTitle,
                        year = item.year,
                        tags = item.tags,
                        ext = ext
                    )
                    val destPath = File(config.movieDir, destName).absolutePath
                    previewList.add(
                        ScanPreviewItem(
                            mediaItem = item,
                            proposedName = destName,
                            proposedPath = destPath
                        )
                    )
                    val subNotice = if (item.companionSubtitles.isNotEmpty()) " (+${item.companionSubtitles.size} subs)" else ""
                    onLog("Found Movie: ${item.originalName}$subNotice → $destName")
                }
                is MediaItem.TvEpisode -> {
                    val ext = item.file.extension
                    val epNum = item.episode
                    val epEnd = item.episodeEnd
                    val epTitle: String? = resolvedEpisodeTitles[item]

                    val destName = NamingTemplateFormatter.formatTvEpisodeFilename(
                        template = config.tvTemplate,
                        showName = item.showName,
                        season = item.season,
                        episode = epNum,
                        episodeEnd = epEnd,
                        tag = item.tag,
                        episodeTitle = epTitle,
                        tags = item.tags,
                        ext = ext
                    )

                    val showFolder = File(config.tvDir, item.showName)
                    val seasonFolder = File(showFolder, String.format("%02d", item.season))
                    val destPath = File(seasonFolder, destName).absolutePath

                    previewList.add(
                        ScanPreviewItem(
                            mediaItem = item,
                            proposedName = destName,
                            proposedPath = destPath
                        )
                    )
                    val subNotice = if (item.companionSubtitles.isNotEmpty()) " (+${item.companionSubtitles.size} subs)" else ""
                    onLog("Found TV Episode: ${item.originalName}$subNotice → $destName")
                }
            }
            onProgress(0.8f + (idx.toFloat() / rawItems.size.coerceAtLeast(1).toFloat()) * 0.2f)
        }

        return previewList
    }

    suspend fun executeBatch(
        items: List<ScanPreviewItem>,
        transactionDao: TransactionDao?,
        onItemUpdated: (Int, ScanPreviewItem) -> Unit = { _, _ -> },
        onLog: (String) -> Unit = {}
    ): BatchExecutionResult {
        val startTime = System.currentTimeMillis()
        var movieCount = 0
        var tvCount = 0
        var skippedMovieCount = 0
        var skippedTvCount = 0
        var failedCount = 0

        val recordedMoves = mutableListOf<MovedFileRecordEntity>()

        for (i in items.indices) {
            val previewItem = items[i]
            if (!previewItem.isSelected || previewItem.status != RenameStatus.PENDING) {
                continue
            }

            val sourceFile = previewItem.mediaItem.file
            val destFile = File(previewItem.proposedPath)

            if (!sourceFile.exists()) {
                val failed = previewItem.copy(
                    status = RenameStatus.FAILED,
                    errorMessage = "Source file does not exist"
                )
                onItemUpdated(i, failed)
                failedCount++
                onLog("❌ Failed: Source file not found: ${previewItem.mediaItem.originalName}")
                continue
            }

            if (destFile.exists()) {
                val skipped = previewItem.copy(
                    status = RenameStatus.FAILED,
                    errorMessage = "Destination file already exists"
                )
                onItemUpdated(i, skipped)
                when (previewItem.mediaItem) {
                    is MediaItem.Movie -> skippedMovieCount++
                    is MediaItem.TvEpisode -> skippedTvCount++
                }
                onLog("⚠️ Skipped: Destination already exists: ${previewItem.proposedName}")
                continue
            }

            try {
                destFile.parentFile?.mkdirs()
                moveFileSafely(sourceFile, destFile)

                val movedSubs = mutableListOf<SubtitleMovePair>()
                val usedNames = mutableSetOf<String>()
                val existingSubs = destFile.parentFile
                    ?.listFiles { f -> MediaScanner.isSubtitleFile(f) }
                    ?.map { it.name }
                    ?: emptyList()
                usedNames.addAll(existingSubs)

                for (subFile in previewItem.mediaItem.companionSubtitles) {
                    if (subFile.exists()) {
                        val subDestName = computeSubtitleDestName(subFile, previewItem.proposedName, usedNames)
                        usedNames.add(subDestName)
                        val subDestFile = File(destFile.parentFile, subDestName)
                        if (!subDestFile.exists()) {
                            try {
                                val oldSubPath = subFile.absolutePath
                                moveFileSafely(subFile, subDestFile)
                                movedSubs.add(SubtitleMovePair(oldPath = oldSubPath, newPath = subDestFile.absolutePath))
                                onLog("   📄 Subtitle moved: ${subFile.name} → $subDestName")
                            } catch (subEx: Exception) {
                                onLog("   ⚠️ Subtitle move error: ${subEx.message}")
                            }
                        }
                    }
                }

                val now = System.currentTimeMillis()
                recordedMoves.add(
                    MovedFileRecordEntity(
                        batchId = 0L,
                        oldPath = sourceFile.absolutePath,
                        newPath = destFile.absolutePath,
                        movedSubtitlePairsJson = RoomJsonConverters.encodeSubtitlePairs(movedSubs),
                        mediaType = if (previewItem.mediaItem is MediaItem.Movie) "MOVIE" else "TV",
                        timestamp = now
                    )
                )

                onItemUpdated(i, previewItem.copy(status = RenameStatus.SUCCESS))
                when (previewItem.mediaItem) {
                    is MediaItem.Movie -> movieCount++
                    is MediaItem.TvEpisode -> tvCount++
                }
                onLog("✅ Moved: ${previewItem.mediaItem.originalName} → ${previewItem.proposedName}")
            } catch (e: Exception) {
                failedCount++
                onItemUpdated(
                    i,
                    previewItem.copy(
                        status = RenameStatus.FAILED,
                        errorMessage = e.message
                    )
                )
                onLog("❌ Error moving ${previewItem.mediaItem.originalName}: ${e.message}")
            }
        }

        var batchId: Long? = null
        if (recordedMoves.isNotEmpty() && transactionDao != null) {
            val batch = OrganizeBatchEntity(
                timestamp = System.currentTimeMillis(),
                totalFiles = recordedMoves.size,
                movieCount = movieCount,
                tvCount = tvCount
            )
            batchId = transactionDao.insertBatchWithRecords(batch, recordedMoves)
            onLog("💾 Saved Batch #$batchId (${recordedMoves.size} files) to Undo History.")
        }

        val elapsedMillis = System.currentTimeMillis() - startTime
        return BatchExecutionResult(
            batchId = batchId,
            movieCount = movieCount,
            tvCount = tvCount,
            skippedMovieCount = skippedMovieCount,
            skippedTvCount = skippedTvCount,
            failedCount = failedCount,
            elapsedMillis = elapsedMillis
        )
    }

    suspend fun undoBatch(
        batchId: Long,
        transactionDao: TransactionDao,
        movieDir: String,
        tvDir: String,
        onLog: (String) -> Unit = {}
    ): UndoResult {
        val batchWithRecords = transactionDao.getBatchById(batchId)
            ?: return UndoResult(batchId, 0, 0, 0, 1, false)

        if (batchWithRecords.batch.isUndone) {
            onLog("ℹ️ Batch #$batchId was already undone.")
            return UndoResult(batchId, 0, 0, 0, 0, true)
        }

        onLog("↩️ UNDOING BATCH #$batchId (${batchWithRecords.records.size} files)...")

        var restoredVideos = 0
        var restoredSubs = 0
        var skipped = 0
        var errors = 0

        val movieStopDir = File(movieDir)
        val tvStopDir = File(tvDir)

        for (record in batchWithRecords.records.asReversed()) {
            val currentFile = File(record.newPath)
            val originalFile = File(record.oldPath)

            if (!currentFile.exists()) {
                skipped++
                onLog("⚠️ Skipped undo (moved file missing): ${currentFile.name}")
            } else if (originalFile.exists()) {
                skipped++
                onLog("⚠️ Skipped undo (original path already occupied): ${originalFile.name}")
            } else {
                try {
                    val destParent = currentFile.parentFile
                    originalFile.parentFile?.mkdirs()
                    moveFileSafely(currentFile, originalFile)
                    restoredVideos++
                    onLog("↩️ Restored: ${currentFile.name} → ${originalFile.absolutePath}")

                    if (destParent != null) {
                        val stopAt = if (record.mediaType == "TV") tvStopDir else movieStopDir
                        cleanupEmptyDestinationParents(destParent, stopAt)
                    }
                } catch (e: Exception) {
                    errors++
                    onLog("❌ Failed to undo ${currentFile.name}: ${e.message}")
                }
            }

            val subtitlePairs = RoomJsonConverters.decodeSubtitlePairs(record.movedSubtitlePairsJson)
            for (pair in subtitlePairs) {
                val subCurrent = File(pair.newPath)
                val subOriginal = File(pair.oldPath)
                if (!subCurrent.exists() || subOriginal.exists()) {
                    continue
                }
                try {
                    val subDestParent = subCurrent.parentFile
                    subOriginal.parentFile?.mkdirs()
                    moveFileSafely(subCurrent, subOriginal)
                    restoredSubs++
                    onLog("   ↩️ Restored subtitle: ${subCurrent.name} → ${subOriginal.name}")
                    if (subDestParent != null) {
                        val stopAt = if (record.mediaType == "TV") tvStopDir else movieStopDir
                        cleanupEmptyDestinationParents(subDestParent, stopAt)
                    }
                } catch (e: Exception) {
                    errors++
                    onLog("   ⚠️ Failed to restore subtitle ${subCurrent.name}: ${e.message}")
                }
            }
        }

        val shouldMarkUndone = errors == 0
        if (shouldMarkUndone) {
            transactionDao.markBatchUndone(batchId, System.currentTimeMillis())
        }

        onLog("🎉 Undo complete for Batch #$batchId: $restoredVideos videos, $restoredSubs subtitles restored ($skipped skipped, $errors errors).")

        return UndoResult(
            batchId = batchId,
            restoredVideos = restoredVideos,
            restoredSubtitles = restoredSubs,
            skippedMissingOrConflict = skipped,
            errorCount = errors,
            markedUndone = shouldMarkUndone
        )
    }

    internal fun moveFileSafely(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            // Fallback across mount boundaries (e.g. internal storage <-> SD card)
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destination.length() != source.length()) {
                destination.delete()
                throw IOException("Cross-volume copy verification failed for ${source.name}", e)
            }
            if (!source.delete()) {
                throw IOException("Copied to destination but could not delete source ${source.absolutePath}")
            }
        }
    }

    private fun computeSubtitleDestName(subFile: File, proposedName: String, usedNames: Set<String>): String {
        val extIndex = proposedName.lastIndexOf('.')
        val baseStem = if (extIndex > 0) proposedName.substring(0, extIndex) else proposedName
        return SubtitleHelper.subtitleDestinationName(baseStem, subFile.absolutePath, usedNames)
    }

    private fun cleanupEmptyDestinationParents(dir: File, stopAt: File) {
        var current: File? = dir
        val stopCanonical = try {
            stopAt.canonicalPath
        } catch (e: Exception) {
            stopAt.absolutePath
        }

        while (current != null && current.exists()) {
            val currentCanonical = try {
                current.canonicalPath
            } catch (e: Exception) {
                current.absolutePath
            }
            if (currentCanonical == stopCanonical || !currentCanonical.startsWith("$stopCanonical${File.separator}")) {
                break
            }
            val children = current.listFiles()
            if (children != null && children.isEmpty()) {
                if (!current.delete()) break
            } else {
                break
            }
            current = current.parentFile
        }
    }
}
