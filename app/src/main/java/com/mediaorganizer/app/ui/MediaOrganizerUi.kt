package com.mediaorganizer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.mediaorganizer.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaorganizer.app.MediaOrganizerViewModel
import com.mediaorganizer.app.MediaScanner.MediaItem
import com.mediaorganizer.app.RenameStatus
import com.mediaorganizer.app.ScanPreviewItem
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import com.mediaorganizer.app.CleanupExecutor
import com.mediaorganizer.app.MainActivity
import com.mediaorganizer.app.MediaTagExtractor
import com.mediaorganizer.app.NamingTemplateFormatter
import com.mediaorganizer.app.data.BatchWithRecords
import com.mediaorganizer.app.data.CleanupBatchWithRecords
import com.mediaorganizer.app.data.RoomJsonConverters
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Highly Vibrant Premium Color Palette
val IndigoDark = Color(0xFF1E1B4B)  // Deep rich Indigo
val VioletVibrant = Color(0xFF4C1D95) // Neon Violet
val TealVibrant = Color(0xFF0F766E)   // Glowing Teal
val SlateBlack = Color(0xFF0B0F19)    // Core Sleek Slate

val VibrantBackground = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0B0F19),
        Color(0xFF0D1425),
        Color(0xFF0E1A2E)
    )
)

// Glassmorphism Values
val GlassBackplate = Color(0xD0111827) // Semi-transparent Slate
val GlassBorderNeon = Brush.linearGradient(
    colors = listOf(
        Color(0xFF06B6D4), // Cyan
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEC4899)  // Pink
    )
)

val CyanPrimary = Color(0xFF06B6D4)
val VioletSecondary = Color(0xFF8B5CF6)
val PinkAccent = Color(0xFFEC4899)
val EmeraldSuccess = Color(0xFF10B981)
val RoseError = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF9FAFB)
val TextSecondary = Color(0xFFD1D5DB)

val TerminalBg = Color(0xE005070B)
val TerminalCyan = Color(0xFF00E5FF)
val TerminalYellow = Color(0xFFFFD600)

fun getAbsolutePathFromUri(context: android.content.Context, uri: Uri): String? {
    try {
        if (DocumentsContract.isTreeUri(uri)) {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val split = documentId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relativePath = split[1]
                val absolutePath = if ("primary".equals(type, ignoreCase = true)) {
                    "/storage/emulated/0/$relativePath"
                } else {
                    "/storage/$type/$relativePath"
                }
                val cleanedPath = absolutePath.replace("//", "/")
                return if (cleanedPath.endsWith("/") && cleanedPath.length > 1) {
                    cleanedPath.substring(0, cleanedPath.length - 1)
                } else {
                    cleanedPath
                }
            }
        }
        val path = uri.path ?: return null
        if (path.contains("document/primary:")) {
            val split = path.split("document/primary:")
            if (split.size > 1) {
                val decoded = URLDecoder.decode(split[1], "UTF-8")
                val absolutePath = "/storage/emulated/0/$decoded"
                val cleanedPath = absolutePath.replace("//", "/")
                return if (cleanedPath.endsWith("/") && cleanedPath.length > 1) {
                    cleanedPath.substring(0, cleanedPath.length - 1)
                } else {
                    cleanedPath
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

// Format file size nicely
fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1fMB", bytes / (1024f * 1024f))
        else -> String.format("%.2fGB", bytes / (1024f * 1024f * 1024f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOrganizerUi(viewModel: MediaOrganizerViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadSettings(context)
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(viewModel.previewTriggerCount) {
        if (viewModel.previewTriggerCount > 0) {
            selectedTab = 0
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xEE0B0F19),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🎬", fontSize = 18.sp) },
                    label = {
                        Text(stringResource(R.string.organize),
                            color = if (selectedTab == 0) CyanPrimary else TextSecondary,
                            fontSize = 10.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanPrimary,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("🧹", fontSize = 18.sp) },
                    label = {
                        Text(stringResource(R.string.cleanup),
                            color = if (selectedTab == 1) PinkAccent else TextSecondary,
                            fontSize = 10.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PinkAccent,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    label = {
                        Text(stringResource(R.string.settings),
                            color = if (selectedTab == 2) VioletSecondary else TextSecondary,
                            fontSize = 10.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VioletSecondary,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VibrantBackground)
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> OrganizeTab(viewModel = viewModel)
                1 -> CleanupTab(viewModel = viewModel)
                2 -> SettingsTab(viewModel = viewModel, onSaved = { viewModel.saveSettings(context) })
            }

            // Summary Overlay Dialog (always on top)
            SummaryOverlay(viewModel = viewModel)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// TAB 0: ORGANIZE
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeTab(viewModel: MediaOrganizerViewModel) {
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }

    if (showHistoryDialog) {
        TransactionHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Header
        AppHeader()
        Spacer(modifier = Modifier.height(8.dp))

        // One-Tap Undo & Transaction History Bar
        val latestActive = viewModel.latestActiveBatch
        if (viewModel.transactionHistory.isNotEmpty()) {
            UndoHistoryBanner(
                latestActive = latestActive,
                totalBatches = viewModel.transactionHistory.size,
                isBusy = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning || viewModel.isUndoing,
                onUndoLatest = { viewModel.undoLatestBatch() },
                onOpenHistory = { showHistoryDialog = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Scan progress bar
        if (viewModel.isScanning) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scanning... ${viewModel.totalFilesScanned} items found",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${(viewModel.scanProgress * 100).toInt()}%",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { viewModel.scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanPrimary,
                    trackColor = Color(0x3306B6D4)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Results Header + Filter chips
        if (viewModel.scannedItems.isNotEmpty()) {
            ResultsHeaderRow(viewModel = viewModel)
            Spacer(modifier = Modifier.height(6.dp))
            FilterSortRow(viewModel = viewModel)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Both consoles equally: weight(1f) for both preview list and terminal log
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val displayItems = if (viewModel.scannedItems.isEmpty()) emptyList() else viewModel.filteredItems

            if (viewModel.scannedItems.isEmpty()) {
                EmptyStateBox()
            } else if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassBackplate)
                        .border(1.dp, GlassBorderNeon, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No items match your filter.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(displayItems) { index, item ->
                        val originalIndex = viewModel.scannedItems.indexOf(item)
                        ScanPreviewCard(
                            item = item,
                            onClick = { if (originalIndex >= 0) viewModel.toggleSelectItem(originalIndex) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Terminal log
        TerminalLogView(viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())

        Spacer(modifier = Modifier.height(10.dp))

        // Action Controls Row (Scan + Rename, plus toggle + dry run) at the bottom
        OrganizeActionRow(viewModel = viewModel)
    }
}

@Composable
fun EmptyStateBox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackplate)
            .border(1.dp, GlassBorderNeon, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "✨ Ready to Scan",
                color = CyanPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = stringResource(R.string.scan_directory_desc),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun OrganizeActionRow(viewModel: MediaOrganizerViewModel) {
    val isRunning = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning || viewModel.isUndoing

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scan Button
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealVibrant,
                    disabledContainerColor = Color(0x330F766E)
                ),
                modifier = Modifier
                    .weight(0.8f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = "SCAN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }

            // Rename & Move Button
            Button(
                onClick = { viewModel.executeRename() },
                enabled = !isRunning && viewModel.scannedItems.any { it.isSelected && it.status == RenameStatus.PENDING },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VioletVibrant,
                    disabledContainerColor = Color(0x334C1D95)
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                if (viewModel.isRenaming) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    val count = viewModel.scannedItems.count { it.isSelected && it.status == RenameStatus.PENDING }
                    Text(
                        text = "RENAME & MOVE ($count)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
@Composable
fun ResultsHeaderRow(viewModel: MediaOrganizerViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackplate, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = viewModel.isSelectAll,
            onCheckedChange = { viewModel.toggleSelectAll() },
            colors = CheckboxDefaults.colors(checkedColor = CyanPrimary)
        )
        Text(
            text = "Select All (${viewModel.scannedItems.size} total / ${viewModel.filteredItems.size} shown)",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Clear",
            color = PinkAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { viewModel.scannedItems = emptyList() }
                .padding(4.dp)
        )
    }
}

@Composable
fun ScanPreviewCard(item: ScanPreviewItem, onClick: () -> Unit) {
    val isMovie = item.mediaItem is MediaItem.Movie
    val fileSize = remember(item.mediaItem.file) { item.mediaItem.file.length() }
    val formattedSize = formatFileSize(fileSize)

    // Shortened destination path (last 2 segments)
    val shortDestPath = remember(item.proposedPath) {
        val parts = item.proposedPath.split("/").filter { it.isNotBlank() }
        if (parts.size >= 2) ".../${parts.takeLast(2).joinToString("/")}" else item.proposedPath
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (item.isSelected) Color(0xFF06B6D4) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSelected) Color(0xFF131D38) else GlassBackplate
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CyanPrimary
                ),
                enabled = item.status == RenameStatus.PENDING
            )

            Spacer(modifier = Modifier.width(2.dp))

            // File Information Details
            Column(modifier = Modifier.weight(1f)) {
                // Category Badge + Original Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isMovie) Color(0x333B82F6) else Color(0x3310B981),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isMovie) "MOVIE" else "TV EPISODE",
                            color = if (isMovie) Color(0xFF60A5FA) else EmeraldSuccess,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (item.mediaItem.companionSubtitles.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0x33EC4899), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "+${item.mediaItem.companionSubtitles.size} SUB",
                                color = PinkAccent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.mediaItem.originalName,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Proposed Naming Display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "→ ",
                        color = PinkAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.proposedName,
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // File size + destination path
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedSize,
                        color = VioletSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = shortDestPath,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Error message display if failed
                if (item.status == RenameStatus.FAILED && item.errorMessage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "⚠️ ${item.errorMessage}",
                        color = RoseError,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Status indicator
            StatusIndicatorIcon(status = item.status)
        }
    }
}

@Composable
fun StatusIndicatorIcon(status: RenameStatus) {
    val text = when (status) {
        RenameStatus.PENDING -> "⏳"
        RenameStatus.SUCCESS -> "✅"
        RenameStatus.FAILED -> "❌"
    }
    Text(text = text, fontSize = 18.sp)
}

@Composable
fun TerminalLogView(viewModel: MediaOrganizerViewModel, modifier: Modifier = Modifier.fillMaxWidth().height(220.dp)) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLogsToUri(uri, asJson = false) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLogsToUri(uri, asJson = true) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showExportDialog) {
        val stamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Terminal Logs", color = CyanPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Choose a format to save the current terminal log (${viewModel.logs.size} lines) and diagnostic summary:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showExportDialog = false
                            exportTxtLauncher.launch("media_organizer_log_$stamp.txt")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealVibrant)
                    ) {
                        Text(".TXT", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            showExportDialog = false
                            exportJsonLauncher.launch("media_organizer_log_$stamp.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary)
                    ) {
                        Text(".JSON", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = SlateBlack
        )
    }

    // Auto-scroll logs when list size changes
    LaunchedEffect(viewModel.logs.size) {
        if (viewModel.logs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(viewModel.logs.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TerminalBg)
            .border(1.dp, GlassBorderNeon, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Terminal Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ Cyberpunk Console Log Viewport",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "EXPORT",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CyanPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { showExportDialog = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            // Clear button
            Text(
                text = "CLEAR",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = PinkAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { viewModel.clearLogs() }
                    .padding(4.dp)
            )
        }

        Divider(color = Color(0x3306B6D4), modifier = Modifier.padding(vertical = 5.dp))

        // Logs Terminal Listing
        if (viewModel.logs.isEmpty()) {
            Text(text = stringResource(R.string.system_active_ready),
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(viewModel.logs) { _, log ->
                    val isMediaDiscoveryLine =
                        log.contains("Found TV Episode:") ||
                            log.contains("Found Movie:") ||
                            log.contains("Scanning file:") ||
                            log.contains("✅ Moved:")
                    Text(
                        text = log,
                        color = when {
                            !isMediaDiscoveryLine && (
                                log.contains("❌") ||
                                    log.contains("ERROR:") ||
                                    log.contains("] Error:") ||
                                    log.contains("] Failed:")
                                ) -> RoseError
                            log.contains("✅") || log.contains("SUCCESS") || log.contains("🎉") -> EmeraldSuccess
                            log.contains("🗑️") || log.contains("🧹") || log.contains("Removed Junk") || log.contains("Deleted") -> TerminalYellow
                            log.contains("🎬") || log.contains("🚀") || log.contains("Search") -> TerminalCyan
                            else -> TextPrimary
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryOverlay(viewModel: MediaOrganizerViewModel) {
    AnimatedVisibility(
        visible = viewModel.executionTime.isNotEmpty() && !viewModel.isRenaming,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA0B0F19)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(320.dp)
                    .border(2.dp, GlassBorderNeon, RoundedCornerShape(20.dp))
                    .shadow(16.dp, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = GlassBackplate),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚡ Batch Completed!",
                        color = CyanPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Media files sorted and all redundant empty directories swept clean.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Stats Rows
                    StatRow(label = "Movies Moved", value = viewModel.movedMovies.toString(), color = CyanPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatRow(label = "Episodes Moved", value = viewModel.movedTv.toString(), color = EmeraldSuccess)
                    if (viewModel.skippedMovies > 0 || viewModel.skippedTv > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StatRow(label = "Movies Skipped", value = viewModel.skippedMovies.toString(), color = TerminalYellow)
                        Spacer(modifier = Modifier.height(6.dp))
                        StatRow(label = "Episodes Skipped", value = viewModel.skippedTv.toString(), color = TerminalYellow)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    StatRow(label = "Time Elapsed", value = viewModel.executionTime, color = PinkAccent)

                    Spacer(modifier = Modifier.height(20.dp))

                    val batchIdToUndo = viewModel.lastExecutedBatchId
                    if (batchIdToUndo != null) {
                        Button(
                            onClick = { viewModel.undoBatch(batchIdToUndo) },
                            enabled = !viewModel.isUndoing,
                            colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("↩️ UNDO THIS BATCH", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { viewModel.executionTime = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x550B0F19), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚡ Media Organiser",
            color = CyanPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Developed by",
                color = TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = "Dastgir Siddiq",
                color = PinkAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FilterSortRow(viewModel: MediaOrganizerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("ALL", "MOVIE", "TV").forEach { type ->
            FilterChip(
                selected = viewModel.filterType == type,
                onClick = { viewModel.filterType = type },
                label = { Text(type, fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VioletSecondary,
                    selectedLabelColor = Color.White
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(stringResource(R.string.sort), color = TextSecondary, fontSize = 10.sp)
        Box(
            modifier = Modifier
                .clickable {
                    viewModel.sortOrder = when(viewModel.sortOrder) {
                        "ORIGINAL" -> "PROPOSED"
                        "PROPOSED" -> "TYPE"
                        else -> "ORIGINAL"
                    }
                }
                .padding(4.dp)
        ) {
            Text(text = viewModel.sortOrder, color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CleanupTab(viewModel: MediaOrganizerViewModel) {
    var showFindReplaceDialog by rememberSaveable { mutableStateOf(false) }
    var showCleanupHistoryDialog by rememberSaveable { mutableStateOf(false) }

    if (showCleanupHistoryDialog) {
        CleanupHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showCleanupHistoryDialog = false }
        )
    }

    if (showFindReplaceDialog) {
        var findText by rememberSaveable { mutableStateOf("") }
        var replaceText by rememberSaveable { mutableStateOf("") }
        var folderPath by rememberSaveable { mutableStateOf(viewModel.basePath) }
        val context = LocalContext.current
        
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                folderPath = getAbsolutePathFromUri(context, uri) ?: uri.toString()
            }
        }

        AlertDialog(
            onDismissRequest = { showFindReplaceDialog = false },
            title = { Text(stringResource(R.string.find_replace), color = CyanPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.target_directory), color = TextSecondary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = folderPath,
                            onValueChange = { folderPath = it },
                            modifier = Modifier.weight(1f).height(48.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = Color(0xFF374151)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { launcher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary),
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("📁", fontSize = 14.sp)
                        }
                    }
                    
                    Text(stringResource(R.string.text_to_find), color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color(0xFF374151)
                        )
                    )
                    
                    Text(stringResource(R.string.replace_with), color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color(0xFF374151)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFindReplaceDialog = false
                        viewModel.runFindAndReplace(folderPath, findText, replaceText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    enabled = findText.isNotEmpty()
                ) {
                    Text(stringResource(R.string.run), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFindReplaceDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = SlateBlack
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        AppHeader()
        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.cleanupHistory.isNotEmpty()) {
            CleanupUndoHistoryBanner(
                latestActive = viewModel.latestActiveCleanupBatch,
                totalBatches = viewModel.cleanupHistory.size,
                isBusy = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning || viewModel.isUndoing,
                onUndoLatest = { viewModel.undoLatestCleanupBatch() },
                onOpenHistory = { showCleanupHistoryDialog = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        TerminalLogView(viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isRunning = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning || viewModel.isUndoing

            Button(
                onClick = { viewModel.runJunkAndEmptyCleanup() },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinkAccent,
                    disabledContainerColor = Color(0x33EC4899)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (viewModel.isCleaning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "JUNK CLEANUP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
            
            Button(
                onClick = { showFindReplaceDialog = true },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VioletSecondary,
                    disabledContainerColor = Color(0x338B5CF6)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "FIND & REPLACE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SettingsTab(viewModel: MediaOrganizerViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        AppHeader()
        Spacer(modifier = Modifier.height(10.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PathConfigItem(
                    stringResource(R.string.source_directory), 
                    viewModel.basePath,
                    onPathChanged = { 
                        viewModel.basePath = it
                        onSaved()
                    },
                    onUriSelected = { uri ->
                        val path = getAbsolutePathFromUri(context, uri) ?: uri.toString()
                        viewModel.basePath = path
                        onSaved()
                    }
                )
            }
            item {
                PathConfigItem(
                    stringResource(R.string.movies_target), 
                    viewModel.movieDir,
                    onPathChanged = { 
                        viewModel.movieDir = it
                        onSaved()
                    },
                    onUriSelected = { uri ->
                        val path = getAbsolutePathFromUri(context, uri) ?: uri.toString()
                        viewModel.movieDir = path
                        onSaved()
                    }
                )
            }
            item {
                PathConfigItem(
                    stringResource(R.string.tv_series_target), 
                    viewModel.tvDir,
                    onPathChanged = { 
                        viewModel.tvDir = it
                        onSaved()
                    },
                    onUriSelected = { uri ->
                        val path = getAbsolutePathFromUri(context, uri) ?: uri.toString()
                        viewModel.tvDir = path
                        onSaved()
                    }
                )
            }
            
            item {
                PathListManager(
                    title = stringResource(R.string.excluded_paths),
                    paths = viewModel.excludedPaths,
                    onAdd = { viewModel.addExcludedPath(context, it) },
                    onRemove = { viewModel.removeExcludedPath(context, it) }
                )
            }
            
            item {
                PathListManager(
                    title = stringResource(R.string.junk_folders),
                    paths = viewModel.junkPaths,
                    onAdd = { viewModel.addJunkPath(context, it) },
                    onRemove = { viewModel.removeJunkPath(context, it) }
                )
            }
            
            item {
                ScanOptionsCard(viewModel = viewModel, onSaved = onSaved)
            }

            item {
                NamingTemplatesCard(viewModel = viewModel)
            }

            item {
                WatchdogSettingsCard(viewModel = viewModel)
            }

            item {
                BackupRestoreCard(viewModel = viewModel)
            }

            item {
                LanguageSelector()
            }
        }
    }
}

@Composable
fun PathConfigItem(title: String, path: String, onPathChanged: (String) -> Unit, onUriSelected: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            onUriSelected(uri)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderNeon, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = path,
                    onValueChange = onPathChanged,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF374151)
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { launcher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.change), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathListManager(
    title: String,
    paths: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newValue by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = getAbsolutePathFromUri(context, uri) ?: uri.toString()
            newValue = path
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = PinkAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            paths.forEach { path ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = path,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "❌",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onRemove(path) }
                            .padding(4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TextPrimary),
                    placeholder = { Text(stringResource(R.string.add_new_path_keyword), fontSize = 11.sp, color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { launcher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("📁", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { 
                        if (newValue.isNotBlank()) {
                            onAdd(newValue.trim())
                            newValue = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(stringResource(R.string.add), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LanguageSelector() {
    val context = LocalContext.current
    val languages = listOf(
        "en" to "English",
        "hi" to "Hindi (हिन्दी)",
        "te" to "Telugu (తెలుగు)",
        "ta" to "Tamil (தமிழ்)",
        "kn" to "Kannada (ಕನ್ನಡ)",
        "ml" to "Malayalam (മലയാളം)",
        "zh" to "Mandarin (中文)"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderNeon, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = stringResource(R.string.app_language), color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(languages.size) { index ->
                    val (code, name) = languages[index]
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                                localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(code)
                            } else {
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    androidx.core.os.LocaleListCompat.forLanguageTags(code)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UndoHistoryBanner(
    latestActive: BatchWithRecords?,
    totalBatches: Int,
    isBusy: Boolean,
    onUndoLatest: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackplate, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x44EC4899), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (latestActive != null) {
            Text(
                text = "💾 Batch #${latestActive.batch.id} (${latestActive.batch.totalFiles} files)",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onUndoLatest,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("↩️ UNDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Text(
                text = "📜 $totalBatches recorded batch(es) in history",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(
            onClick = onOpenHistory,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text("HISTORY ($totalBatches)", color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TransactionHistoryDialog(
    viewModel: MediaOrganizerViewModel,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    var expandedBatchId by remember { mutableStateOf<Long?>(null) }
    val isBusy = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning || viewModel.isUndoing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📜 Transaction History & Undo", color = CyanPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            if (viewModel.transactionHistory.isEmpty()) {
                Text("No organize batches recorded yet.", color = TextSecondary, fontSize = 12.sp)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.transactionHistory, key = { it.batch.id }) { item ->
                        val batch = item.batch
                        val isExpanded = expandedBatchId == batch.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (batch.isUndone) Color(0x33FFFFFF) else CyanPrimary,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { expandedBatchId = if (isExpanded) null else batch.id },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D38)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Batch #${batch.id} • ${dateFormat.format(Date(batch.timestamp))}",
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${batch.movieCount} Movies, ${batch.tvCount} Episodes (${item.records.size} files)",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (batch.isUndone) Color(0x339CA3AF) else Color(0x3310B981),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (batch.isUndone) "UNDONE" else "ACTIVE",
                                            color = if (batch.isUndone) TextSecondary else EmeraldSuccess,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!batch.isUndone) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { viewModel.undoBatch(batch.id) },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                    ) {
                                        Text("↩️ UNDO ENTIRE BATCH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    item.records.forEach { rec ->
                                        val subs = RoomJsonConverters.decodeSubtitlePairs(rec.movedSubtitlePairsJson)
                                        val oldName = File(rec.oldPath).name
                                        val newName = File(rec.newPath).name
                                        Text(
                                            text = "• $oldName → $newName" + if (subs.isNotEmpty()) " (+${subs.size} subs)" else "",
                                            color = CyanPrimary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.transactionHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearTransactionHistory() },
                        enabled = !isBusy
                    ) {
                        Text("Clear History", color = PinkAccent, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = SlateBlack
    )
}

@Composable
fun ScanOptionsCard(viewModel: MediaOrganizerViewModel, onSaved: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Scan & Metadata Options", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            SettingSwitchRow(
                label = "Prefer Wikipedia First for TV Titles",
                checked = viewModel.wikiFirst,
                onCheckedChange = {
                    viewModel.wikiFirst = it
                    onSaved()
                }
            )
            SettingSwitchRow(
                label = "Online Movie Title Lookup",
                checked = viewModel.movieLookupOnline,
                onCheckedChange = {
                    viewModel.movieLookupOnline = it
                    onSaved()
                }
            )
        }
    }
}

@Composable
fun NamingTemplatesCard(viewModel: MediaOrganizerViewModel) {
    val sampleMovieTags = remember {
        MediaTagExtractor.extract("Inception.2010.1080p.x265.DDP5.1.English.Hindi.BluRay.HDR10")
    }
    val sampleTvTags = remember {
        MediaTagExtractor.extract("Breaking.Bad.S01E01.1080p.HEVC.AAC.English.WEB-DL")
    }
    val moviePreview = remember(viewModel.movieTemplate) {
        NamingTemplateFormatter.formatMovieFilename(
            template = viewModel.movieTemplate,
            movieName = "Inception",
            year = "2010",
            tags = sampleMovieTags,
            ext = "mkv"
        )
    }
    val tvPreview = remember(viewModel.tvTemplate) {
        NamingTemplateFormatter.formatTvEpisodeFilename(
            template = viewModel.tvTemplate,
            showName = "Breaking Bad",
            season = 1,
            episode = 1,
            episodeEnd = null,
            tag = "S01E01",
            episodeTitle = "Pilot",
            tags = sampleTvTags,
            ext = "mkv"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderNeon, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom Naming Templates", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Reset Defaults",
                    color = PinkAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { viewModel.resetTemplatesToDefault() }
                        .padding(4.dp)
                )
            }

            Text("Movie Template", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = viewModel.movieTemplate,
                onValueChange = { viewModel.updateTemplates(it, viewModel.tvTemplate) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF374151)
                )
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NamingTemplateFormatter.MOVIE_PLACEHOLDERS) { token ->
                    SuggestionChip(
                        onClick = {
                            val sep = if (viewModel.movieTemplate.isBlank() || viewModel.movieTemplate.endsWith(" ")) "" else " "
                            viewModel.updateTemplates(viewModel.movieTemplate + sep + token, viewModel.tvTemplate)
                        },
                        label = { Text(token, fontSize = 10.sp, color = CyanPrimary) }
                    )
                }
            }
            Text("Preview: $moviePreview", color = EmeraldSuccess, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

            Divider(color = Color(0x22FFFFFF))

            Text("TV Episode Template", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = viewModel.tvTemplate,
                onValueChange = { viewModel.updateTemplates(viewModel.movieTemplate, it) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF374151)
                )
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NamingTemplateFormatter.TV_PLACEHOLDERS) { token ->
                    SuggestionChip(
                        onClick = {
                            val sep = if (viewModel.tvTemplate.isBlank() || viewModel.tvTemplate.endsWith(" ")) "" else " "
                            viewModel.updateTemplates(viewModel.movieTemplate, viewModel.tvTemplate + sep + token)
                        },
                        label = { Text(token, fontSize = 10.sp, color = VioletSecondary) }
                    )
                }
            }
            Text("Preview: $tvPreview", color = EmeraldSuccess, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchdogSettingsCard(viewModel: MediaOrganizerViewModel) {
    val context = LocalContext.current
    val intervalOptions = listOf(0 to "15m", 1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderNeon, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Background Watchdog (WorkManager)", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            SettingSwitchRow(
                label = "Periodically scan & notify for new media",
                checked = viewModel.watchdogEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        (context as? MainActivity)?.requestNotificationPermissionIfNeeded()
                    }
                    viewModel.updateWatchdogSettings(
                        enabled = enabled,
                        intervalHours = viewModel.watchdogIntervalHours,
                        requireCharging = viewModel.watchdogRequireCharging,
                        requireIdle = viewModel.watchdogRequireIdle
                    )
                }
            )

            Text("Scan Interval:", color = TextSecondary, fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(intervalOptions) { (hours, label) ->
                    FilterChip(
                        selected = viewModel.watchdogIntervalHours == hours,
                        onClick = {
                            viewModel.updateWatchdogSettings(
                                enabled = viewModel.watchdogEnabled,
                                intervalHours = hours,
                                requireCharging = viewModel.watchdogRequireCharging,
                                requireIdle = viewModel.watchdogRequireIdle
                            )
                        },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealVibrant,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            SettingSwitchRow(
                label = "Require Charging",
                checked = viewModel.watchdogRequireCharging,
                onCheckedChange = { charging ->
                    viewModel.updateWatchdogSettings(
                        enabled = viewModel.watchdogEnabled,
                        intervalHours = viewModel.watchdogIntervalHours,
                        requireCharging = charging,
                        requireIdle = viewModel.watchdogRequireIdle
                    )
                }
            )

            SettingSwitchRow(
                label = "Require Device Idle",
                checked = viewModel.watchdogRequireIdle,
                onCheckedChange = { idle ->
                    viewModel.updateWatchdogSettings(
                        enabled = viewModel.watchdogEnabled,
                        intervalHours = viewModel.watchdogIntervalHours,
                        requireCharging = viewModel.watchdogRequireCharging,
                        requireIdle = idle
                    )
                }
            )

            Button(
                onClick = {
                    (context as? MainActivity)?.requestNotificationPermissionIfNeeded()
                    viewModel.triggerWatchdogScanNow()
                    Toast.makeText(context, "Watchdog scan started in background", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealVibrant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🐕 Scan & Notify Now", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BackupRestoreCard(viewModel: MediaOrganizerViewModel) {
    val context = LocalContext.current
    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportConfigToUri(uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importConfigFromUri(uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassBackplate),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup & Restore Config (.json)", color = PinkAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Export or restore all directories, excluded paths, junk folders, templates, and Watchdog settings.",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportConfigLauncher.launch("media_organizer_config_$stamp.json")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export JSON", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        importConfigLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import JSON", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CyanPrimary,
                uncheckedTrackColor = Color(0xFF374151)
            )
        )
    }
}

@Composable
fun CleanupUndoHistoryBanner(
    latestActive: CleanupBatchWithRecords?,
    totalBatches: Int,
    isBusy: Boolean,
    onUndoLatest: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackplate, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x44EC4899), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (latestActive != null) {
            Text(
                text = "🧹 Cleanup #${latestActive.batch.id} (${latestActive.batch.junkCount} junk, ${latestActive.batch.emptyDirCount} dirs)",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onUndoLatest,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("↩️ UNDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Text(
                text = "📜 $totalBatches cleanup batch(es) in history",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(
            onClick = onOpenHistory,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text("HISTORY ($totalBatches)", color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CleanupHistoryDialog(
    viewModel: MediaOrganizerViewModel,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    var expandedBatchId by remember { mutableStateOf<Long?>(null) }
    val isBusy = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning || viewModel.isUndoing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🧹 Cleanup History & Undo", color = CyanPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            if (viewModel.cleanupHistory.isEmpty()) {
                Text("No cleanup runs recorded yet.", color = TextSecondary, fontSize = 12.sp)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.cleanupHistory, key = { it.batch.id }) { item ->
                        val batch = item.batch
                        val isExpanded = expandedBatchId == batch.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (batch.isUndone) Color(0x33FFFFFF) else PinkAccent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { expandedBatchId = if (isExpanded) null else batch.id },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D38)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cleanup #${batch.id} • ${dateFormat.format(Date(batch.timestamp))}",
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${batch.junkCount} Junk folder(s), ${batch.emptyDirCount} Empty dir(s)",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (batch.isUndone) Color(0x339CA3AF) else Color(0x3310B981),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (batch.isUndone) "UNDONE" else "ACTIVE",
                                            color = if (batch.isUndone) TextSecondary else EmeraldSuccess,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!batch.isUndone) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { viewModel.undoCleanupBatch(batch.id) },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                    ) {
                                        Text("↩️ UNDO CLEANUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    item.records.forEach { rec ->
                                        val prefix = if (rec.recordType == CleanupExecutor.TYPE_JUNK) "🗑️ Junk:" else "📁 Empty:"
                                        Text(
                                            text = "$prefix ${rec.originalPath}",
                                            color = CyanPrimary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.cleanupHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearCleanupHistory() },
                        enabled = !isBusy
                    ) {
                        Text("Clear & Purge Trash", color = PinkAccent, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = SlateBlack
    )
}
