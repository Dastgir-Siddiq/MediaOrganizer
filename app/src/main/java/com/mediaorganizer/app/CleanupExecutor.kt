package com.mediaorganizer.app

import com.mediaorganizer.app.data.CleanupBatchEntity
import com.mediaorganizer.app.data.CleanupDao
import com.mediaorganizer.app.data.CleanupRecordEntity
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class CleanupExecutionResult(
    val batchId: Long?,
    val removedJunkCount: Int,
    val deletedEmptyDirCount: Int
)

data class CleanupUndoResult(
    val batchId: Long,
    val restoredJunkCount: Int,
    val restoredEmptyDirCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val markedUndone: Boolean
)

object CleanupExecutor {

    const val TYPE_JUNK = "JUNK"
    const val TYPE_EMPTY_DIR = "EMPTY_DIR"

    suspend fun executeCleanup(
        junkPaths: List<String>,
        rootDir: File,
        trashRootDir: File,
        cleanupDao: CleanupDao?,
        onLog: (String) -> Unit = {}
    ): CleanupExecutionResult {
        onLog("🧹 CLEANING JUNK DIRECTORIES & EMPTY FOLDERS...")

        val now = System.currentTimeMillis()
        val batchTrashDir = File(trashRootDir, "batch_$now")
        val records = mutableListOf<CleanupRecordEntity>()
        var junkCount = 0

        for ((index, path) in junkPaths.withIndex()) {
            val file = File(path)
            if (file.exists()) {
                try {
                    if (!batchTrashDir.exists()) {
                        batchTrashDir.mkdirs()
                    }
                    val backupFile = File(batchTrashDir, "junk_${index}_${file.name}")
                    moveDirectoryOrFileSafely(file, backupFile)
                    records.add(
                        CleanupRecordEntity(
                            batchId = 0L,
                            recordType = TYPE_JUNK,
                            originalPath = file.absolutePath,
                            backupPath = backupFile.absolutePath
                        )
                    )
                    junkCount++
                    onLog("🗑️ Removed Junk Folder: $path")
                } catch (e: Exception) {
                    onLog("⚠️ Failed to remove junk path $path: ${e.message}")
                }
            }
        }

        if (batchTrashDir.exists() && batchTrashDir.listFiles()?.isEmpty() == true) {
            batchTrashDir.delete()
        }

        onLog("Scanning for empty folders recursively in ${rootDir.absolutePath}...")
        val deletedEmptyDirs = mutableListOf<String>()
        try {
            if (rootDir.exists() && rootDir.isDirectory) {
                deleteEmptyDirectoriesRecursive(
                    currentDir = rootDir,
                    rootDir = rootDir,
                    trashRootDir = trashRootDir,
                    deletedList = deletedEmptyDirs
                )
            }
        } catch (e: Exception) {
            onLog("⚠️ Error during empty folder scan: ${e.message}")
        }

        if (deletedEmptyDirs.isNotEmpty()) {
            onLog("🧹 Deleted ${deletedEmptyDirs.size} empty directories:")
            for (d in deletedEmptyDirs) {
                onLog("   🗑️ $d")
                records.add(
                    CleanupRecordEntity(
                        batchId = 0L,
                        recordType = TYPE_EMPTY_DIR,
                        originalPath = d,
                        backupPath = null
                    )
                )
            }
        } else {
            onLog("ℹ️ No empty directories found.")
        }

        var batchId: Long? = null
        if (records.isNotEmpty() && cleanupDao != null) {
            val batch = CleanupBatchEntity(
                timestamp = now,
                junkCount = junkCount,
                emptyDirCount = deletedEmptyDirs.size,
                trashDirPath = if (junkCount > 0) batchTrashDir.absolutePath else ""
            )
            batchId = cleanupDao.insertBatchWithRecords(batch, records)
            onLog("💾 Saved Cleanup Batch #$batchId to Undo History.")
        }

        onLog("🎉 CLEANUP TASK COMPLETED SUCCESSFULLY!")
        return CleanupExecutionResult(
            batchId = batchId,
            removedJunkCount = junkCount,
            deletedEmptyDirCount = deletedEmptyDirs.size
        )
    }

    suspend fun undoCleanupBatch(
        batchId: Long,
        cleanupDao: CleanupDao,
        onLog: (String) -> Unit = {}
    ): CleanupUndoResult {
        val batchWithRecords = cleanupDao.getBatchById(batchId)
            ?: return CleanupUndoResult(batchId, 0, 0, 0, 1, false)

        if (batchWithRecords.batch.isUndone) {
            onLog("ℹ️ Cleanup Batch #$batchId was already undone.")
            return CleanupUndoResult(batchId, 0, 0, 0, 0, true)
        }

        onLog("↩️ UNDOING CLEANUP BATCH #$batchId...")

        var restoredJunk = 0
        var restoredDirs = 0
        var skipped = 0
        var errors = 0

        // 1. Restore deleted empty directories first (reverse order so parent-to-child works cleanly)
        val emptyDirRecords = batchWithRecords.records.filter { it.recordType == TYPE_EMPTY_DIR }.asReversed()
        for (record in emptyDirRecords) {
            val dir = File(record.originalPath)
            if (dir.exists()) {
                skipped++
            } else {
                try {
                    if (dir.mkdirs()) {
                        restoredDirs++
                        onLog("   ↩️ Recreated folder: ${dir.absolutePath}")
                    } else {
                        errors++
                        onLog("   ⚠️ Could not recreate folder: ${dir.absolutePath}")
                    }
                } catch (e: Exception) {
                    errors++
                    onLog("   ⚠️ Error recreating folder ${dir.absolutePath}: ${e.message}")
                }
            }
        }

        // 2. Restore removed junk directories/files from trash staging
        val junkRecords = batchWithRecords.records.filter { it.recordType == TYPE_JUNK }
        for (record in junkRecords) {
            val backupPath = record.backupPath
            if (backupPath.isNullOrBlank()) {
                skipped++
                continue
            }
            val backupFile = File(backupPath)
            val originalFile = File(record.originalPath)

            if (!backupFile.exists()) {
                skipped++
                onLog("⚠️ Skipped undo (backup missing): ${originalFile.name}")
                continue
            }
            if (originalFile.exists()) {
                skipped++
                onLog("⚠️ Skipped undo (path already exists): ${originalFile.absolutePath}")
                continue
            }

            try {
                moveDirectoryOrFileSafely(backupFile, originalFile)
                restoredJunk++
                onLog("↩️ Restored junk: ${originalFile.absolutePath}")
            } catch (e: Exception) {
                errors++
                onLog("❌ Failed to restore ${originalFile.name}: ${e.message}")
            }
        }

        val trashDirPath = batchWithRecords.batch.trashDirPath
        if (trashDirPath.isNotBlank()) {
            val batchTrash = File(trashDirPath)
            if (batchTrash.exists() && batchTrash.listFiles()?.isEmpty() == true) {
                batchTrash.delete()
            }
        }

        val shouldMarkUndone = errors == 0
        if (shouldMarkUndone) {
            cleanupDao.markBatchUndone(batchId, System.currentTimeMillis())
        }

        onLog("🎉 Cleanup Undo complete: $restoredJunk junk path(s), $restoredDirs empty folder(s) restored ($skipped skipped, $errors errors).")

        return CleanupUndoResult(
            batchId = batchId,
            restoredJunkCount = restoredJunk,
            restoredEmptyDirCount = restoredDirs,
            skippedCount = skipped,
            errorCount = errors,
            markedUndone = shouldMarkUndone
        )
    }

    suspend fun clearCleanupHistory(
        cleanupDao: CleanupDao,
        trashRootDir: File,
        onLog: (String) -> Unit = {}
    ) {
        if (trashRootDir.exists()) {
            trashRootDir.deleteRecursively()
        }
        cleanupDao.clearAllHistory()
        onLog("🗑️ Cleared cleanup undo history & purged backed-up junk.")
    }

    private fun deleteEmptyDirectoriesRecursive(
        currentDir: File,
        rootDir: File,
        trashRootDir: File,
        deletedList: MutableList<String>
    ) {
        val files = currentDir.listFiles() ?: return
        val trashPath = trashRootDir.absolutePath

        for (file in files) {
            if (file.isDirectory) {
                val absolutePath = file.absolutePath
                if (absolutePath.startsWith("/storage/emulated/0/Android") ||
                    absolutePath.startsWith("/storage/emulated/0/DCIM") ||
                    absolutePath == trashPath ||
                    absolutePath.startsWith("$trashPath/")
                ) {
                    continue
                }
                deleteEmptyDirectoriesRecursive(file, rootDir, trashRootDir, deletedList)
            }
        }

        val path = currentDir.absolutePath
        if (path != rootDir.absolutePath &&
            !path.startsWith("/storage/emulated/0/Android") &&
            !path.startsWith("/storage/emulated/0/DCIM") &&
            path != trashPath &&
            !path.startsWith("$trashPath/")
        ) {
            val remainingFiles = currentDir.listFiles()
            if (remainingFiles != null && remainingFiles.isEmpty()) {
                try {
                    if (currentDir.delete()) {
                        deletedList.add(path)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    internal fun moveDirectoryOrFileSafely(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            if (source.isDirectory) {
                if (!source.copyRecursively(destination, overwrite = true)) {
                    throw IOException("Failed recursive copy for ${source.absolutePath}", e)
                }
                if (!source.deleteRecursively()) {
                    throw IOException("Copied directory but failed deleting ${source.absolutePath}", e)
                }
            } else {
                MediaBatchExecutor.moveFileSafely(source, destination)
            }
        }
    }
}
