package com.mediaorganizer.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaorganizer.app.MediaScanner.MediaItem
import com.mediaorganizer.app.data.AppDatabase
import com.mediaorganizer.app.data.BatchWithRecords
import com.mediaorganizer.app.data.CleanupBatchWithRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RenameStatus {
    PENDING,
    SUCCESS,
    FAILED
}

data class ScanPreviewItem(
    val mediaItem: MediaItem,
    val proposedName: String,
    val proposedPath: String,
    val isSelected: Boolean = true,
    val status: RenameStatus = RenameStatus.PENDING,
    val errorMessage: String? = null
)

class MediaOrganizerViewModel(application: Application) : AndroidViewModel(application) {

    private val database by lazy { AppDatabase.getInstance(getApplication()) }
    private val transactionDao by lazy { database.transactionDao() }
    private val cleanupDao by lazy { database.cleanupDao() }

    // Directory settings
    var basePath by mutableStateOf(AppConfig.DEFAULT_BASE_PATH)
    var movieDir by mutableStateOf(AppConfig.DEFAULT_MOVIE_DIR)
    var tvDir by mutableStateOf(AppConfig.DEFAULT_TV_DIR)

    // Feature flags & filters
    var filterQuery by mutableStateOf("")
    var filterType by mutableStateOf("ALL") // "ALL", "MOVIE", "TV"
    var sortOrder by mutableStateOf("ORIGINAL") // "ORIGINAL", "PROPOSED", "TYPE"
    var wikiFirst by mutableStateOf(false)
    var movieLookupOnline by mutableStateOf(false)

    // Custom naming templates
    var movieTemplate by mutableStateOf(NamingTemplateFormatter.DEFAULT_MOVIE_TEMPLATE)
    var tvTemplate by mutableStateOf(NamingTemplateFormatter.DEFAULT_TV_TEMPLATE)

    // Background watchdog settings
    var watchdogEnabled by mutableStateOf(false)
    var watchdogIntervalHours by mutableStateOf(AppConfig.DEFAULT_WATCHDOG_INTERVAL_HOURS)
    var watchdogRequireCharging by mutableStateOf(true)
    var watchdogRequireIdle by mutableStateOf(false)

    // Progress tracking
    var totalFilesScanned by mutableStateOf(0)
    var scanProgress by mutableStateOf(0f)

    // Execution states
    var isScanning by mutableStateOf(false)
    var isRenaming by mutableStateOf(false)
    var isCleaning by mutableStateOf(false)
    var isUndoing by mutableStateOf(false)

    // Scanned results
    var scannedItems by mutableStateOf<List<ScanPreviewItem>>(emptyList())
    var isSelectAll by mutableStateOf(true)

    // Undo / Transaction history (Organize + Cleanup)
    var transactionHistory by mutableStateOf<List<BatchWithRecords>>(emptyList())
    val latestActiveBatch: BatchWithRecords?
        get() = transactionHistory.firstOrNull { !it.batch.isUndone }
    var lastExecutedBatchId by mutableStateOf<Long?>(null)
    var previewTriggerCount by mutableStateOf(0)

    var cleanupHistory by mutableStateOf<List<CleanupBatchWithRecords>>(emptyList())
    val latestActiveCleanupBatch: CleanupBatchWithRecords?
        get() = cleanupHistory.firstOrNull { !it.batch.isUndone }

    // Log terminal output
    var logs by mutableStateOf<List<String>>(emptyList())

    // Summary counts
    var movedMovies by mutableStateOf(0)
    var movedTv by mutableStateOf(0)
    var skippedMovies by mutableStateOf(0)
    var skippedTv by mutableStateOf(0)
    var executionTime by mutableStateOf("")

    // Path lists
    var junkPaths by mutableStateOf<List<String>>(emptyList())
    var excludedPaths by mutableStateOf<List<String>>(emptyList())

    private var settingsLoaded = false

    init {
        loadSettings(getApplication())
        observeTransactionHistory()
        observeCleanupHistory()
    }

    // Computed filtered/sorted list
    val filteredItems: List<ScanPreviewItem>
        get() {
            var list = scannedItems
            if (filterType != "ALL") {
                list = list.filter {
                    if (filterType == "MOVIE") it.mediaItem is MediaItem.Movie
                    else it.mediaItem is MediaItem.TvEpisode
                }
            }
            if (filterQuery.isNotBlank()) {
                val q = filterQuery.lowercase()
                list = list.filter {
                    it.mediaItem.originalName.lowercase().contains(q) ||
                        it.proposedName.lowercase().contains(q)
                }
            }
            return when (sortOrder) {
                "PROPOSED" -> list.sortedBy { it.proposedName.lowercase() }
                "TYPE" -> list.sortedBy { if (it.mediaItem is MediaItem.Movie) 0 else 1 }
                else -> list.sortedBy { it.mediaItem.originalName.lowercase() }
            }
        }

    fun currentConfig(): AppConfig {
        return AppConfig(
            basePath = basePath,
            movieDir = movieDir,
            tvDir = tvDir,
            wikiFirst = wikiFirst,
            movieLookupOnline = movieLookupOnline,
            junkPaths = junkPaths,
            excludedPaths = excludedPaths,
            movieTemplate = movieTemplate,
            tvTemplate = tvTemplate,
            watchdogEnabled = watchdogEnabled,
            watchdogIntervalHours = watchdogIntervalHours,
            watchdogRequireCharging = watchdogRequireCharging,
            watchdogRequireIdle = watchdogRequireIdle
        )
    }

    private fun applyConfig(config: AppConfig) {
        basePath = config.basePath
        movieDir = config.movieDir
        tvDir = config.tvDir
        wikiFirst = config.wikiFirst
        movieLookupOnline = config.movieLookupOnline
        junkPaths = config.junkPaths
        excludedPaths = config.excludedPaths
        movieTemplate = config.movieTemplate
        tvTemplate = config.tvTemplate
        watchdogEnabled = config.watchdogEnabled
        watchdogIntervalHours = config.watchdogIntervalHours
        watchdogRequireCharging = config.watchdogRequireCharging
        watchdogRequireIdle = config.watchdogRequireIdle
    }

    private fun observeTransactionHistory() {
        viewModelScope.launch {
            transactionDao.getAllBatchesFlow().collectLatest { batches ->
                transactionHistory = batches
            }
        }
    }

    private fun observeCleanupHistory() {
        viewModelScope.launch {
            cleanupDao.getAllBatchesFlow().collectLatest { batches ->
                cleanupHistory = batches
            }
        }
    }

    private fun getCleanupTrashDir(): File {
        val app = getApplication<Application>()
        return app.getExternalFilesDir("cleanup_trash") ?: File(app.filesDir, "cleanup_trash")
    }

    fun loadSettings(context: Context) {
        if (settingsLoaded) return
        settingsLoaded = true
        val config = AppPreferences.loadConfig(context)
        applyConfig(config)
        WatchdogScheduler.syncWithConfig(context, config)
    }

    fun saveSettings(context: Context) {
        val config = currentConfig()
        AppPreferences.saveConfig(context, config)
        WatchdogScheduler.syncWithConfig(context, config)
    }

    fun updateTemplates(newMovieTemplate: String, newTvTemplate: String) {
        movieTemplate = newMovieTemplate
        tvTemplate = newTvTemplate
        saveSettings(getApplication())
    }

    fun resetTemplatesToDefault() {
        movieTemplate = NamingTemplateFormatter.DEFAULT_MOVIE_TEMPLATE
        tvTemplate = NamingTemplateFormatter.DEFAULT_TV_TEMPLATE
        saveSettings(getApplication())
    }

    fun resetPathsToDefaults() {
        basePath = AppConfig.DEFAULT_BASE_PATH
        movieDir = AppConfig.DEFAULT_MOVIE_DIR
        tvDir = AppConfig.DEFAULT_TV_DIR
        saveSettings(getApplication())
    }

    fun loadJunkPaths(context: Context) {
        loadSettings(context)
    }

    fun addJunkPath(context: Context, path: String) {
        if (path.isNotBlank() && !junkPaths.contains(path)) {
            junkPaths = (junkPaths + path).sorted()
            AppPreferences.saveJunkPaths(context, junkPaths)
        }
    }

    fun removeJunkPath(context: Context, path: String) {
        if (junkPaths.contains(path)) {
            junkPaths = junkPaths.filterNot { it == path }
            AppPreferences.saveJunkPaths(context, junkPaths)
        }
    }

    fun addExcludedPath(context: Context, path: String) {
        if (path.isNotBlank() && !excludedPaths.contains(path)) {
            excludedPaths = (excludedPaths + path).sorted()
            AppPreferences.saveExcludedPaths(context, excludedPaths)
        }
    }

    fun removeExcludedPath(context: Context, path: String) {
        if (excludedPaths.contains(path)) {
            excludedPaths = excludedPaths.filterNot { it == path }
            AppPreferences.saveExcludedPaths(context, excludedPaths)
        }
    }

    fun updateWatchdogSettings(
        enabled: Boolean,
        intervalHours: Int,
        requireCharging: Boolean,
        requireIdle: Boolean
    ) {
        watchdogEnabled = enabled
        watchdogIntervalHours = intervalHours
        watchdogRequireCharging = requireCharging
        watchdogRequireIdle = requireIdle
        saveSettings(getApplication())
    }

    fun triggerWatchdogScanNow() {
        WatchdogScheduler.triggerImmediateScan(getApplication(), isManualTrigger = true)
        addLog("🐕 Triggered immediate background Watchdog scan.")
    }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs = logs + "[$timestamp] $message"
    }

    fun toggleSelectItem(index: Int) {
        if (index in scannedItems.indices) {
            val list = scannedItems.toMutableList()
            val item = list[index]
            list[index] = item.copy(isSelected = !item.isSelected)
            scannedItems = list
            isSelectAll = list.all { it.isSelected }
        }
    }

    fun toggleSelectAll() {
        val target = !isSelectAll
        scannedItems = scannedItems.map { it.copy(isSelected = target) }
        isSelectAll = target
    }

    fun clearLogs() {
        logs = emptyList()
    }

    fun startScan() {
        if (isScanning || isRenaming || isCleaning || isUndoing) return
        isScanning = true
        scannedItems = emptyList()
        clearLogs()
        movedMovies = 0
        movedTv = 0
        skippedMovies = 0
        skippedTv = 0
        executionTime = ""
        lastExecutedBatchId = null
        totalFilesScanned = 0
        scanProgress = 0f

        val config = currentConfig()
        addLog("🎬 MEDIA ORGANIZER SCAN STARTED")
        addLog("Scanning directory: ${config.basePath}")

        viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val baseFile = File(config.basePath)

            if (!baseFile.exists() || !baseFile.isDirectory) {
                withContext(Dispatchers.Main) {
                    addLog("❌ Error: Path does not exist or is not a directory: ${config.basePath}")
                    isScanning = false
                }
                return@launch
            }

            val rawItems = MediaScanner.scanDirectory(baseFile, config.excludedPaths) { progressMessage ->
                viewModelScope.launch(Dispatchers.Main) {
                    addLog(progressMessage)
                    totalFilesScanned++
                }
            }

            withContext(Dispatchers.Main) {
                totalFilesScanned = rawItems.size
                scanProgress = 0.3f
            }

            if (rawItems.isEmpty()) {
                withContext(Dispatchers.Main) {
                    addLog("⚠️ No matching video files (Movies or TV Episodes) found.")
                    isScanning = false
                    scanProgress = 0f
                    AppPreferences.setLastScanSubtitle(getApplication(), "No new media")
                    WatchdogScanWorker.requestTileRefresh(getApplication())
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                addLog("🔍 Classification complete. Found ${rawItems.size} items. Checking cache & metadata...")
            }

            val previewList = withContext(Dispatchers.IO) {
                MediaBatchExecutor.buildPreviewItems(
                    rawItems = rawItems,
                    config = config,
                    context = getApplication(),
                    onProgress = { p ->
                        viewModelScope.launch(Dispatchers.Main) {
                            scanProgress = p
                        }
                    },
                    onLog = { logMsg ->
                        viewModelScope.launch(Dispatchers.Main) {
                            addLog(logMsg)
                        }
                    }
                )
            }

            val durationSec = (System.currentTimeMillis() - startTime) / 1000.0
            AppPreferences.setLastScanSubtitle(getApplication(), "${previewList.size} item(s) scanned")
            WatchdogScanWorker.requestTileRefresh(getApplication())

            withContext(Dispatchers.Main) {
                scannedItems = previewList
                isSelectAll = true
                scanProgress = 1f
                addLog("🎉 SCAN COMPLETED in ${String.format("%.2f", durationSec)}s. Found ${previewList.size} items.")
                isScanning = false
            }
        }
    }

    fun executeRename() {
        if (isRenaming || isScanning || isCleaning || isUndoing) return
        isRenaming = true

        val itemsToRename = scannedItems.filter { it.isSelected && it.status == RenameStatus.PENDING }
        if (itemsToRename.isEmpty()) {
            addLog("⚠️ No items selected or pending renaming.")
            isRenaming = false
            return
        }

        addLog("🚀 BATCH RENAMING STARTED: Processing ${itemsToRename.size} items...")

        viewModelScope.launch(Dispatchers.Default) {
            val updatedList = scannedItems.toMutableList()
            val result = withContext(Dispatchers.IO) {
                MediaBatchExecutor.executeBatch(
                    items = scannedItems,
                    transactionDao = transactionDao,
                    onItemUpdated = { index, updatedItem ->
                        updatedList[index] = updatedItem
                        val snapshot = updatedList.toList()
                        viewModelScope.launch(Dispatchers.Main) {
                            scannedItems = snapshot
                        }
                    },
                    onLog = { logMsg ->
                        viewModelScope.launch(Dispatchers.Main) {
                            addLog(logMsg)
                        }
                    }
                )
            }

            val seconds = (result.elapsedMillis / 1000) % 60
            val minutes = (result.elapsedMillis / (1000 * 60)) % 60
            val hours = (result.elapsedMillis / (1000 * 60 * 60)) % 24
            val durationString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

            AppPreferences.setLastScanSubtitle(
                getApplication(),
                "Moved ${result.movieCount + result.tvCount} file(s)"
            )
            WatchdogScanWorker.requestTileRefresh(getApplication())

            withContext(Dispatchers.Main) {
                movedMovies = result.movieCount
                movedTv = result.tvCount
                skippedMovies = result.skippedMovieCount
                skippedTv = result.skippedTvCount
                lastExecutedBatchId = result.batchId
                executionTime = durationString
                isRenaming = false
            }
        }
    }

    fun undoBatch(batchId: Long) {
        if (isScanning || isRenaming || isCleaning || isUndoing) return
        isUndoing = true

        viewModelScope.launch(Dispatchers.IO) {
            val undoResult = MediaBatchExecutor.undoBatch(
                batchId = batchId,
                transactionDao = transactionDao,
                movieDir = movieDir,
                tvDir = tvDir,
                onLog = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog(msg)
                    }
                }
            )
            withContext(Dispatchers.Main) {
                if (undoResult.markedUndone) {
                    if (lastExecutedBatchId == batchId) {
                        lastExecutedBatchId = null
                        executionTime = ""
                    }
                    scannedItems = emptyList()
                }
                isUndoing = false
            }
        }
    }

    fun undoLatestBatch() {
        val batch = latestActiveBatch ?: return
        undoBatch(batch.batch.id)
    }

    fun clearTransactionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.clearAllHistory()
            withContext(Dispatchers.Main) {
                lastExecutedBatchId = null
                addLog("🗑️ Cleared transaction undo history.")
            }
        }
    }

    fun exportLogsToUri(uri: Uri, asJson: Boolean, onComplete: (Boolean, String) -> Unit) {
        val currentLogs = logs.toList()
        val config = currentConfig()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val content = if (asJson) {
                    LogExporter.formatAsJson(
                        logs = currentLogs,
                        config = config,
                        device = device,
                        sdkInt = Build.VERSION.SDK_INT
                    )
                } else {
                    LogExporter.formatAsText(
                        logs = currentLogs,
                        config = config,
                        device = device,
                        sdkInt = Build.VERSION.SDK_INT
                    )
                }

                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Could not open output stream")

                val msg = "✅ Exported ${currentLogs.size} log lines (${if (asJson) "JSON" else "TXT"})."
                withContext(Dispatchers.Main) {
                    addLog(msg)
                    onComplete(true, msg)
                }
            } catch (e: Exception) {
                val msg = "❌ Log export failed: ${e.message}"
                withContext(Dispatchers.Main) {
                    addLog(msg)
                    onComplete(false, msg)
                }
            }
        }
    }

    fun exportConfigToUri(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        val config = currentConfig()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = ConfigBackupManager.exportConfigToJson(config)
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Could not open output stream")

                val msg = "✅ Exported configuration backup JSON."
                withContext(Dispatchers.Main) {
                    addLog(msg)
                    onComplete(true, msg)
                }
            } catch (e: Exception) {
                val msg = "❌ Config export failed: ${e.message}"
                withContext(Dispatchers.Main) {
                    addLog(msg)
                    onComplete(false, msg)
                }
            }
        }
    }

    fun importConfigFromUri(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        val current = currentConfig()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val json = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: throw IllegalStateException("Could not open input stream")

                val imported = ConfigBackupManager.importConfigFromJson(json, current)
                AppPreferences.saveConfig(getApplication(), imported)
                WatchdogScheduler.syncWithConfig(getApplication(), imported)

                val msg = "✅ Restored configuration backup (paths, exclusions, junk, templates)."
                withContext(Dispatchers.Main) {
                    applyConfig(imported)
                    addLog(msg)
                    onComplete(true, msg)
                }
            } catch (e: Exception) {
                val msg = "❌ Config import failed: ${e.message}"
                withContext(Dispatchers.Main) {
                    addLog(msg)
                    onComplete(false, msg)
                }
            }
        }
    }

    fun runJunkAndEmptyCleanup() {
        if (isCleaning || isScanning || isRenaming || isUndoing) return
        isCleaning = true
        clearLogs()
        addLog("🧹 STANDALONE CLEANUP STARTED")

        val pathsToClean = junkPaths.toList()
        viewModelScope.launch(Dispatchers.IO) {
            CleanupExecutor.executeCleanup(
                junkPaths = pathsToClean,
                rootDir = File("/storage/emulated/0"),
                trashRootDir = getCleanupTrashDir(),
                cleanupDao = cleanupDao,
                onLog = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog(msg)
                    }
                }
            )
            withContext(Dispatchers.Main) {
                isCleaning = false
            }
        }
    }

    fun undoCleanupBatch(batchId: Long) {
        if (isScanning || isRenaming || isCleaning || isUndoing) return
        isUndoing = true

        viewModelScope.launch(Dispatchers.IO) {
            CleanupExecutor.undoCleanupBatch(
                batchId = batchId,
                cleanupDao = cleanupDao,
                onLog = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog(msg)
                    }
                }
            )
            withContext(Dispatchers.Main) {
                isUndoing = false
            }
        }
    }

    fun undoLatestCleanupBatch() {
        val batch = latestActiveCleanupBatch ?: return
        undoCleanupBatch(batch.batch.id)
    }

    fun clearCleanupHistory() {
        if (isCleaning || isUndoing) return
        viewModelScope.launch(Dispatchers.IO) {
            CleanupExecutor.clearCleanupHistory(
                cleanupDao = cleanupDao,
                trashRootDir = getCleanupTrashDir(),
                onLog = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog(msg)
                    }
                }
            )
        }
    }

    fun runFindAndReplace(folder: String, findText: String, replaceText: String) {
        if (isCleaning || isScanning || isRenaming || isUndoing) return
        isCleaning = true
        clearLogs()
        addLog("❖ FILE RENAME UTILITY ❖")
        addLog("📁 Target Directory : $folder")
        addLog("🔍 Text to Find     : '$findText'")
        addLog("✏️  Replace With     : '${if (replaceText.isNotEmpty()) replaceText else "[DELETING TEXT]"}'")
        addLog("─".repeat(60))

        viewModelScope.launch(Dispatchers.Default) {
            var renameCount = 0
            val dir = File(folder)

            if (!dir.exists() || !dir.isDirectory) {
                withContext(Dispatchers.Main) {
                    addLog("❌ ERROR: The directory '$folder' does not exist or is not a directory.")
                    isCleaning = false
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                addLog("⏳ Scanning directory tree...")
            }

            try {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.name.contains(findText)) {
                        val newName = file.name.replace(findText, replaceText)
                        val newFile = File(file.parentFile, newName)

                        try {
                            if (file.renameTo(newFile)) {
                                renameCount++
                                withContext(Dispatchers.Main) {
                                    addLog("🔹 Old: ${file.name}")
                                    addLog("🔸 New: $newName")
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    addLog("❌ ERROR renaming '${file.name}'")
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                addLog("❌ ERROR renaming '${file.name}': ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog("❌ ERROR during scan: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                addLog("═".repeat(60))
                addLog("❖ TASK FINISHED")
                if (renameCount > 0) {
                    addLog("🎉 Total files successfully renamed: $renameCount")
                } else {
                    addLog("ℹ No files matching '$findText' were found to rename.")
                }
                isCleaning = false
            }
        }
    }
}
