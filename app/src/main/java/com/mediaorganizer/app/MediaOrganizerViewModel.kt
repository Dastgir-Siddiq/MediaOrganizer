package com.mediaorganizer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaorganizer.app.MediaScanner.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

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

class MediaOrganizerViewModel : ViewModel() {

    // Default paths based on the new python script
    var basePath by mutableStateOf("/storage/emulated/0/Download")
    var movieDir by mutableStateOf("/storage/emulated/0/Entertainment/Movies")
    var tvDir by mutableStateOf("/storage/emulated/0/Entertainment")

    // New feature states
    var dryRunMode by mutableStateOf(false)
    var filterQuery by mutableStateOf("")
    var filterType by mutableStateOf("ALL") // "ALL", "MOVIE", "TV"
    var sortOrder by mutableStateOf("ORIGINAL") // "ORIGINAL", "PROPOSED", "TYPE"
    var wikiFirst by mutableStateOf(false)

    // Progress tracking
    var totalFilesScanned by mutableStateOf(0)
    var scanProgress by mutableStateOf(0f)

    // Execution states
    var isScanning by mutableStateOf(false)
    var isRenaming by mutableStateOf(false)
    var isCleaning by mutableStateOf(false)

    // Scanned results
    var scannedItems by mutableStateOf<List<ScanPreviewItem>>(emptyList())
    var isSelectAll by mutableStateOf(true)

    // Log terminal output
    var logs by mutableStateOf<List<String>>(emptyList())

    // Summary counts
    var movedMovies by mutableStateOf(0)
    var movedTv by mutableStateOf(0)
    var skippedMovies by mutableStateOf(0)
    var skippedTv by mutableStateOf(0)
    var executionTime by mutableStateOf("")

    // Specific junk paths
    var junkPaths by mutableStateOf<List<String>>(emptyList())
    var excludedPaths by mutableStateOf<List<String>>(emptyList())

    private val DEFAULT_JUNK_PATHS = listOf(
        "/storage/emulated/0/.FileManagerRecycler",
        "/storage/emulated/0/Download/.com_oplus_rom",
        "/storage/emulated/0/Download/.content_portal",
        "/storage/emulated/0/Download/com_account_usercenter_new",
        "/storage/emulated/0/Movies/.thumbnails",
        "/storage/emulated/0/Music/.thumbnails",
        "/storage/emulated/0/oua_classifier",
        "/storage/emulated/0/Pictures/.thumbnails"
    )

    private val DEFAULT_EXCLUDED_PATHS = listOf(
        "/storage/emulated/0/Android",
        "/storage/emulated/0/DCIM"
    )

    private val DEFAULT_BASE_PATH = "/storage/emulated/0/Download"
    private val DEFAULT_MOVIE_DIR = "/storage/emulated/0/Entertainment/Movies"
    private val DEFAULT_TV_DIR = "/storage/emulated/0/Entertainment"

    // Computed filtered/sorted list
    val filteredItems: List<ScanPreviewItem>
        get() {
            var list = scannedItems
            if (filterType != "ALL") {
                list = list.filter {
                    if (filterType == "MOVIE") it.mediaItem is MediaScanner.MediaItem.Movie
                    else it.mediaItem is MediaScanner.MediaItem.TvEpisode
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
                "TYPE" -> list.sortedBy { if (it.mediaItem is MediaScanner.MediaItem.Movie) 0 else 1 }
                else -> list.sortedBy { it.mediaItem.originalName.lowercase() }
            }
        }

    fun loadSettings(context: android.content.Context) {
        val prefs = context.getSharedPreferences("MediaOrganizerPrefs", android.content.Context.MODE_PRIVATE)
        basePath = prefs.getString("basePath", DEFAULT_BASE_PATH) ?: DEFAULT_BASE_PATH
        movieDir = prefs.getString("movieDir", DEFAULT_MOVIE_DIR) ?: DEFAULT_MOVIE_DIR
        tvDir = prefs.getString("tvDir", DEFAULT_TV_DIR) ?: DEFAULT_TV_DIR
        dryRunMode = prefs.getBoolean("dryRunMode", false)

        val version = prefs.getInt("prefs_version", 1)
        if (version < 2) {
            junkPaths = DEFAULT_JUNK_PATHS
            excludedPaths = DEFAULT_EXCLUDED_PATHS
            saveJunkPathsInternal(context, junkPaths)
            saveExcludedPathsInternal(context, excludedPaths)
            prefs.edit().putInt("prefs_version", 2).apply()
        } else {
            val savedJunk = prefs.getStringSet("junkPaths", null)
            junkPaths = if (savedJunk != null) savedJunk.toList().sorted() else DEFAULT_JUNK_PATHS

            val savedExcluded = prefs.getStringSet("excludedPaths", null)
            excludedPaths = if (savedExcluded != null) savedExcluded.toList().sorted() else DEFAULT_EXCLUDED_PATHS
        }
    }

    fun saveSettings(context: android.content.Context) {
        val prefs = context.getSharedPreferences("MediaOrganizerPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("basePath", basePath)
            .putString("movieDir", movieDir)
            .putString("tvDir", tvDir)
            .putBoolean("dryRunMode", dryRunMode)
            .apply()
    }

    fun resetPathsToDefaults() {
        basePath = DEFAULT_BASE_PATH
        movieDir = DEFAULT_MOVIE_DIR
        tvDir = DEFAULT_TV_DIR
    }

    // Keep backward compat
    fun loadJunkPaths(context: android.content.Context) {
        loadSettings(context)
    }

    private fun saveJunkPathsInternal(context: android.content.Context, paths: List<String>) {
        val prefs = context.getSharedPreferences("MediaOrganizerPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("junkPaths", paths.toSet()).apply()
    }

    private fun saveExcludedPathsInternal(context: android.content.Context, paths: List<String>) {
        val prefs = context.getSharedPreferences("MediaOrganizerPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("excludedPaths", paths.toSet()).apply()
    }

    fun addJunkPath(context: android.content.Context, path: String) {
        if (path.isNotBlank() && !junkPaths.contains(path)) {
            val newList = junkPaths.toMutableList().apply { add(path) }
            junkPaths = newList.sorted()
            saveJunkPathsInternal(context, junkPaths)
        }
    }

    fun removeJunkPath(context: android.content.Context, path: String) {
        if (junkPaths.contains(path)) {
            val newList = junkPaths.toMutableList().apply { remove(path) }
            junkPaths = newList
            saveJunkPathsInternal(context, junkPaths)
        }
    }

    fun addExcludedPath(context: android.content.Context, path: String) {
        if (path.isNotBlank() && !excludedPaths.contains(path)) {
            val newList = excludedPaths.toMutableList().apply { add(path) }
            excludedPaths = newList.sorted()
            saveExcludedPathsInternal(context, excludedPaths)
        }
    }

    fun removeExcludedPath(context: android.content.Context, path: String) {
        if (excludedPaths.contains(path)) {
            val newList = excludedPaths.toMutableList().apply { remove(path) }
            excludedPaths = newList
            saveExcludedPathsInternal(context, excludedPaths)
        }
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
        if (isScanning || isRenaming || isCleaning) return
        isScanning = true
        scannedItems = emptyList()
        clearLogs()
        movedMovies = 0
        movedTv = 0
        skippedMovies = 0
        skippedTv = 0
        executionTime = ""
        totalFilesScanned = 0
        scanProgress = 0f

        val dryRunPrefix = if (dryRunMode) "[DRY RUN] " else ""
        addLog("🎬 ${dryRunPrefix}MEDIA ORGANIZER SCAN STARTED")
        addLog("Scanning directory: $basePath")

        viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val baseFile = File(basePath)

            if (!baseFile.exists() || !baseFile.isDirectory) {
                withContext(Dispatchers.Main) {
                    addLog("❌ Error: Path does not exist or is not a directory: $basePath")
                    isScanning = false
                }
                return@launch
            }

            val rawItems = MediaScanner.scanDirectory(baseFile, excludedPaths) { progressMessage ->
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
                }
                return@launch
            }

            addLog("🔍 Classification complete. Found ${rawItems.size} items. Fetching titles from TVMaze & Wikipedia...")

            // Fetch TV titles & Build proposed names/paths
            val previewList = mutableListOf<ScanPreviewItem>()
            val showTitlesCache = mutableMapOf<Pair<String, Int>, Map<Int, String>>()

            // --- FETCH TV TITLES WITH FULL EPISODE LIST CONTEXT ---
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
                if (!showTitlesCache.containsKey(key)) {
                    withContext(Dispatchers.Main) {
                        addLog("Fetching titles for $showName Season $season...")
                    }
                    val titles = try {
                        withContext(Dispatchers.IO) {
                            MediaTitleFetcher.getEpisodeTitles(showName, season, sortedEpNums, wikiFirst) { logMsg ->
                                viewModelScope.launch(Dispatchers.Main) {
                                    addLog(logMsg)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            addLog("⚠️ Could not fetch titles for $showName: ${e.message}")
                        }
                        emptyMap<Int, String>()
                    }
                    showTitlesCache[key] = titles
                }
                groupIdx++
                val progress = 0.3f + (groupIdx.toFloat() / totalGroups.toFloat()) * 0.5f
                withContext(Dispatchers.Main) { scanProgress = progress }
            }

            // --- BUILD PREVIEWS ---
            for ((idx, item) in rawItems.withIndex()) {
                when (item) {
                    is MediaItem.Movie -> {
                        val ext = item.file.extension
                        val destName = "${item.movieName} (${item.year}).$ext"
                        val destPath = File(movieDir, destName).absolutePath
                        previewList.add(
                            ScanPreviewItem(
                                mediaItem = item,
                                proposedName = destName,
                                proposedPath = destPath
                            )
                        )
                        withContext(Dispatchers.Main) {
                            val subNotice = if (item.companionSubtitles.isNotEmpty()) " (+${item.companionSubtitles.size} subs)" else ""
                            addLog("Found Movie: ${item.originalName}$subNotice → $destName")
                        }
                    }
                    is MediaItem.TvEpisode -> {
                        val ext = item.file.extension
                        val cacheKey = Pair(item.showName, item.season)
                        val titles = showTitlesCache[cacheKey] ?: emptyMap()

                        val epNum = item.episode
                        val epEnd = item.episodeEnd
                        val epTag = item.tag // S01E01 or S01E01-E02

                        val destName = if (epEnd != null) {
                            val epTitles = mutableListOf<String>()
                            for (n in epNum..epEnd) {
                                if (titles.containsKey(n)) {
                                    epTitles.add(titles[n]!!)
                                }
                            }
                            if (epTitles.isNotEmpty()) {
                                val combinedTitle = MediaScanner.sanitizeFilename(epTitles.joinToString(" & "))
                                "${item.showName} $epTag - $combinedTitle.$ext"
                            } else {
                                "${item.showName} $epTag.$ext"
                            }
                        } else {
                            if (titles.containsKey(epNum)) {
                                val epTitle = MediaScanner.sanitizeFilename(titles[epNum]!!)
                                "${item.showName} $epTag - $epTitle.$ext"
                            } else {
                                "${item.showName} $epTag.$ext"
                            }
                        }

                        // Target dir: tvDir/showName/season:02d/
                        val showFolder = File(tvDir, item.showName)
                        val seasonFolder = File(showFolder, String.format("%02d", item.season))
                        val destPath = File(seasonFolder, destName).absolutePath

                        previewList.add(
                            ScanPreviewItem(
                                mediaItem = item,
                                proposedName = destName,
                                proposedPath = destPath
                            )
                        )
                        withContext(Dispatchers.Main) {
                            val subNotice = if (item.companionSubtitles.isNotEmpty()) " (+${item.companionSubtitles.size} subs)" else ""
                            addLog("Found TV Episode: ${item.originalName}$subNotice → $destName")
                        }
                    }
                }
                // Update progress during preview building
                val buildProgress = 0.8f + (idx.toFloat() / rawItems.size.coerceAtLeast(1).toFloat()) * 0.2f
                withContext(Dispatchers.Main) { scanProgress = buildProgress }
            }

            val durationSec = (System.currentTimeMillis() - startTime) / 1000.0

            withContext(Dispatchers.Main) {
                scannedItems = previewList
                scanProgress = 1f
                addLog("🎉 SCAN COMPLETED in ${String.format("%.2f", durationSec)}s. Found ${previewList.size} items.")
                isScanning = false
            }
        }
    }

    private fun computeSubtitleDestName(subFile: File, proposedName: String, usedNames: Set<String>): String {
        val extIndex = proposedName.lastIndexOf('.')
        val baseStem = if (extIndex > 0) proposedName.substring(0, extIndex) else proposedName
        return SubtitleHelper.subtitleDestinationName(baseStem, subFile.absolutePath, usedNames)
    }

    private fun removeEmptyParents(dirPath: File, stopAt: File, onLog: (String) -> Unit = {}) {
        var current: File? = dirPath
        val stopNorm = stopAt.canonicalPath

        while (current != null && current.exists()) {
            val currentNorm = current.canonicalPath
            if (currentNorm == stopNorm || !currentNorm.startsWith(stopNorm)) {
                break
            }

            val list = current.listFiles()
            if (list != null && list.isEmpty()) {
                val dirName = current.name
                if (current.delete()) {
                    onLog("🗑 Removed empty folder: $dirName/")
                } else {
                    break
                }
            } else {
                break
            }
            current = current.parentFile
        }
    }

    fun executeRename() {
        if (isRenaming || isScanning || isCleaning) return
        isRenaming = true

        val itemsToRename = scannedItems.filter { it.isSelected && it.status == RenameStatus.PENDING }
        if (itemsToRename.isEmpty()) {
            addLog("⚠️ No items selected or pending renaming.")
            isRenaming = false
            return
        }

        val dryRunPrefix = if (dryRunMode) "[DRY RUN] " else ""
        addLog("🚀 ${dryRunPrefix}BATCH RENAMING STARTED: Processing ${itemsToRename.size} items...")

        viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            var movieCount = 0
            var tvCount = 0
            var skippedMovieCount = 0
            var skippedTvCount = 0
            val sourceDirsToClean = mutableSetOf<File>()

            val updatedList = scannedItems.map { it }.toMutableList()

            for (i in scannedItems.indices) {
                val previewItem = scannedItems[i]
                if (!previewItem.isSelected || previewItem.status != RenameStatus.PENDING) {
                    continue
                }

                val sourceFile = previewItem.mediaItem.file
                val destFile = File(previewItem.proposedPath)

                // DRY RUN: just log what would happen
                if (dryRunMode) {
                    withContext(Dispatchers.Main) {
                        addLog("[DRY RUN] Would move: ${previewItem.mediaItem.originalName} → ${previewItem.proposedName}")
                    }
                    updatedList[i] = previewItem.copy(status = RenameStatus.SUCCESS)
                    when (previewItem.mediaItem) {
                        is MediaItem.Movie -> movieCount++
                        is MediaItem.TvEpisode -> tvCount++
                    }
                    withContext(Dispatchers.Main) { scannedItems = updatedList.toList() }
                    continue
                }

                // Skip if source doesn't exist
                if (!sourceFile.exists()) {
                    updatedList[i] = previewItem.copy(
                        status = RenameStatus.FAILED,
                        errorMessage = "Source file does not exist"
                    )
                    withContext(Dispatchers.Main) {
                        addLog("❌ Failed: Source file not found: ${previewItem.mediaItem.originalName}")
                        scannedItems = updatedList.toList()
                    }
                    continue
                }

                // Skip if destination already exists
                if (destFile.exists()) {
                    updatedList[i] = previewItem.copy(
                        status = RenameStatus.FAILED,
                        errorMessage = "Destination file already exists"
                    )
                    when (previewItem.mediaItem) {
                        is MediaItem.Movie -> skippedMovieCount++
                        is MediaItem.TvEpisode -> skippedTvCount++
                    }
                    withContext(Dispatchers.Main) {
                        addLog("⚠️ Skipped: Destination already exists: ${previewItem.proposedName}")
                        scannedItems = updatedList.toList()
                    }
                    continue
                }

                try {
                    // Record parent dir for empty folder cleanup
                    sourceFile.parentFile?.let { sourceDirsToClean.add(it) }

                    // Create parent folders if necessary
                    destFile.parentFile?.mkdirs()

                    withContext(Dispatchers.IO) {
                        Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    // Move companion subtitles if present
                    val usedNames = mutableSetOf<String>()
                    val existingSubs = destFile.parentFile?.listFiles { f -> MediaScanner.isSubtitleFile(f) }?.map { it.name } ?: emptyList()
                    usedNames.addAll(existingSubs)

                    for (subFile in previewItem.mediaItem.companionSubtitles) {
                        if (subFile.exists()) {
                            val subDestName = computeSubtitleDestName(subFile, previewItem.proposedName, usedNames)
                            usedNames.add(subDestName)
                            val subDestFile = File(destFile.parentFile, subDestName)
                            if (!subDestFile.exists()) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        Files.move(subFile.toPath(), subDestFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                    }
                                    withContext(Dispatchers.Main) {
                                        addLog("   📄 Subtitle moved: ${subFile.name} → $subDestName")
                                    }
                                } catch (subEx: Exception) {
                                    withContext(Dispatchers.Main) {
                                        addLog("   ⚠️ Subtitle move error: ${subEx.message}")
                                    }
                                }
                            }
                        }
                    }

                    updatedList[i] = previewItem.copy(status = RenameStatus.SUCCESS)

                    when (previewItem.mediaItem) {
                        is MediaItem.Movie -> movieCount++
                        is MediaItem.TvEpisode -> tvCount++
                    }

                    withContext(Dispatchers.Main) {
                        addLog("✅ Moved: ${previewItem.mediaItem.originalName} → ${previewItem.proposedName}")
                        scannedItems = updatedList.toList()
                    }
                } catch (e: Exception) {
                    updatedList[i] = previewItem.copy(
                        status = RenameStatus.FAILED,
                        errorMessage = e.message
                    )
                    withContext(Dispatchers.Main) {
                        addLog("❌ Error moving ${previewItem.mediaItem.originalName}: ${e.message}")
                        scannedItems = updatedList.toList()
                    }
                }
            }

            // Cleanup empty parent directories of moved source files up to basePath
            if (!dryRunMode) {
                val baseFile = File(basePath)
                if (baseFile.exists()) {
                    for (dir in sourceDirsToClean) {
                        removeEmptyParents(dir, baseFile) { logMsg ->
                            viewModelScope.launch(Dispatchers.Main) {
                                addLog(logMsg)
                            }
                        }
                    }
                }
            }

            val elapsedMillis = System.currentTimeMillis() - startTime
            val seconds = (elapsedMillis / 1000) % 60
            val minutes = (elapsedMillis / (1000 * 60)) % 60
            val hours = (elapsedMillis / (1000 * 60 * 60)) % 24
            val durationString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

            withContext(Dispatchers.Main) {
                movedMovies = movieCount
                movedTv = tvCount
                skippedMovies = skippedMovieCount
                skippedTv = skippedTvCount
                executionTime = durationString
                isRenaming = false
            }

            // AUTO CLEANUP AT THE END - only if not dry run
            if (!dryRunMode) {
                runJunkAndEmptyCleanupInternal()
            } else {
                withContext(Dispatchers.Main) {
                    addLog("[DRY RUN] Skipping actual file operations. Review the log above for planned actions.")
                }
            }
        }
    }

    fun runJunkAndEmptyCleanup() {
        if (isCleaning || isScanning || isRenaming) return
        isCleaning = true
        clearLogs()
        addLog("🧹 STANDALONE CLEANUP STARTED")
        viewModelScope.launch(Dispatchers.Default) {
            runJunkAndEmptyCleanupInternal()
            withContext(Dispatchers.Main) {
                isCleaning = false
            }
        }
    }

    private suspend fun runJunkAndEmptyCleanupInternal() {
        withContext(Dispatchers.Main) {
            addLog("🧹 CLEANING JUNK DIRECTORIES & EMPTY FOLDERS...")
        }

        // 1. Remove specific junk paths recursively
        for (path in junkPaths) {
            val file = File(path)
            if (file.exists()) {
                try {
                    val deleted = withContext(Dispatchers.IO) {
                        file.deleteRecursively()
                    }
                    if (deleted) {
                        withContext(Dispatchers.Main) {
                            addLog("🗑️ Removed Junk Folder: $path")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        addLog("⚠️ Failed to remove junk path $path: ${e.message}")
                    }
                }
            }
        }

        // 2. Remove empty directories bottom-up under /storage/emulated/0
        val rootPath = "/storage/emulated/0"
        withContext(Dispatchers.Main) {
            addLog("Scanning for empty folders recursively in $rootPath...")
        }

        val deletedEmptyDirs = mutableListOf<String>()
        try {
            val rootFile = File(rootPath)
            if (rootFile.exists() && rootFile.isDirectory) {
                withContext(Dispatchers.IO) {
                    deleteEmptyDirectoriesRecursive(rootFile, deletedEmptyDirs)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                addLog("⚠️ Error during empty folder scan: ${e.message}")
            }
        }

        withContext(Dispatchers.Main) {
            if (deletedEmptyDirs.isNotEmpty()) {
                addLog("🧹 Deleted ${deletedEmptyDirs.size} empty directories:")
                for (d in deletedEmptyDirs) {
                    addLog("   🗑️ $d")
                }
            } else {
                addLog("ℹ️ No empty directories found.")
            }
            addLog("🎉 CLEANUP TASK COMPLETED SUCCESSFULLY!")
        }
    }

    private fun deleteEmptyDirectoriesRecursive(currentDir: File, deletedList: MutableList<String>) {
        val files = currentDir.listFiles() ?: return

        // Post-order traversal (bottom-up), check children first
        for (file in files) {
            if (file.isDirectory) {
                val absolutePath = file.absolutePath
                // Keep Android system folders and camera pictures completely safe!
                if (absolutePath.startsWith("/storage/emulated/0/Android") ||
                    absolutePath.startsWith("/storage/emulated/0/DCIM")
                ) {
                    continue
                }

                // Recursively check children
                deleteEmptyDirectoriesRecursive(file, deletedList)
            }
        }

        // Check if current directory is now empty and not the root /storage/emulated/0
        val path = currentDir.absolutePath
        if (path != "/storage/emulated/0" &&
            !path.startsWith("/storage/emulated/0/Android") &&
            !path.startsWith("/storage/emulated/0/DCIM")
        ) {
            val remainingFiles = currentDir.listFiles()
            if (remainingFiles != null && remainingFiles.isEmpty()) {
                try {
                    val deleted = currentDir.delete()
                    if (deleted) {
                        deletedList.add(path)
                    }
                } catch (e: Exception) {
                    // Fail silently
                }
            }
        }
    }
}
