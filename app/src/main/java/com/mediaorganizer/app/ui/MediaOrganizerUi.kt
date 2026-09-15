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
import androidx.compose.ui.Alignment
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
import java.net.URLDecoder

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

    var selectedTab by remember { mutableStateOf(0) }

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
                        Text(
                            "Organize",
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
                        Text(
                            "Cleanup",
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
                        Text(
                            "Settings",
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Header
        AppHeader()
        Spacer(modifier = Modifier.height(10.dp))

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
                            dryRunMode = viewModel.dryRunMode,
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
            Text(
                text = "Scan directory to list and rename Movies or TV Series.\nUse the Cleanup tab to clear empty folders safely.",
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
    val isRunning = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning

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
fun ScanPreviewCard(item: ScanPreviewItem, dryRunMode: Boolean = false, onClick: () -> Unit) {
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
                width = if (dryRunMode && item.isSelected) 1.dp else 1.dp,
                color = when {
                    dryRunMode && item.isSelected -> Color(0xFFFFD600)
                    item.isSelected -> Color(0xFF06B6D4)
                    else -> Color(0x22FFFFFF)
                },
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
                    checkedColor = if (dryRunMode) TerminalYellow else CyanPrimary
                ),
                enabled = item.status == RenameStatus.PENDING
            )

            Spacer(modifier = Modifier.width(2.dp))

            // File Information Details
            Column(modifier = Modifier.weight(1f)) {
                // Category Badge + Original Name + DRY RUN badge
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
                    if (dryRunMode) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFD600), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "DRY RUN",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
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
                modifier = Modifier.weight(1f)
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
            Text(
                text = "SYSTEM ACTIVE. Ready for file sorting, TVMaze indexing, or standalone device cleanup sweeps.",
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
                    Text(
                        text = log,
                        color = when {
                            log.contains("[DRY RUN]") -> TerminalYellow
                            log.contains("❌") || log.contains("Error") || log.contains("Failed") -> RoseError
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
                    // Dry Run banner
                    if (viewModel.dryRunMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFD600), RoundedCornerShape(8.dp))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔬 DRY RUN — No files were moved",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = if (viewModel.dryRunMode) "⚡ Dry Run Completed!" else "⚡ Batch Completed!",
                        color = CyanPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (viewModel.dryRunMode)
                            "Simulation complete. Review logs to see planned operations."
                        else
                            "Media files sorted and all redundant empty directories swept clean.",
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

                    Button(
                        onClick = { viewModel.executionTime = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
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
        Text("Sort: ", color = TextSecondary, fontSize = 10.sp)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        AppHeader()
        Spacer(modifier = Modifier.height(10.dp))
        
        TerminalLogView(viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isRunning = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning

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
                        text = "RUN JUNK CLEANUP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
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
                    "Source Directory", 
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
                    "Movies Target", 
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
                    "TV Series Target", 
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
                    title = "Excluded Paths (Ignored during scan)",
                    paths = viewModel.excludedPaths,
                    onAdd = { viewModel.addExcludedPath(context, it) },
                    onRemove = { viewModel.removeExcludedPath(context, it) }
                )
            }
            
            item {
                PathListManager(
                    title = "Junk Folders (Deleted during cleanup)",
                    paths = viewModel.junkPaths,
                    onAdd = { viewModel.addJunkPath(context, it) },
                    onRemove = { viewModel.removeJunkPath(context, it) }
                )
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
                    Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    var newValue by remember { mutableStateOf("") }
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
                    placeholder = { Text("Add new path keyword...", fontSize = 11.sp, color = TextSecondary) },
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
                    Text("ADD", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
