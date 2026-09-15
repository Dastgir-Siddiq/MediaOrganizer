import re

with open('/home/dastgir-siddiq/projects/MediaOrganizer/app/src/main/java/com/mediaorganizer/app/ui/MediaOrganizerUi.kt', 'r') as f:
    code = f.read()

# 1. AppHeader - already done.

# 2. OrganizeTab
# We need to remove PathConfigCard and showConfig completely from OrganizeTab.
# Also put OrganizeActionRow at the bottom.
organize_tab_pattern = re.compile(r'(@OptIn\(ExperimentalMaterial3Api::class\)\s*@Composable\s*fun OrganizeTab\(viewModel: MediaOrganizerViewModel\).*?)(\n@Composable\s*fun EmptyStateBox\(\))', re.DOTALL)

def rewrite_organize_tab(match):
    return """@OptIn(ExperimentalMaterial3Api::class)
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
""" + match.group(2)

code = organize_tab_pattern.sub(rewrite_organize_tab, code)

# Update OrganizeActionRow
organize_action_pattern = re.compile(r'(@Composable\s*fun OrganizeActionRow\(\s*viewModel: MediaOrganizerViewModel,\s*showConfig: Boolean,\s*onToggleConfig: \(\) -> Unit\s*\)\s*\{)(.*?)(?=\n@Composable\nfun ResultsHeaderRow)', re.DOTALL)

def rewrite_organize_action(match):
    return """@Composable
fun OrganizeActionRow(viewModel: MediaOrganizerViewModel) {
    val isRunning = viewModel.isScanning || viewModel.isRenaming || viewModel.isCleaning

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dry Run Toggle
            Box(
                modifier = Modifier
                    .background(if (viewModel.dryRunMode) Color(0x33FFFF00) else GlassBackplate, RoundedCornerShape(12.dp))
                    .border(1.dp, if (viewModel.dryRunMode) TerminalYellow else GlassBorderNeon, RoundedCornerShape(12.dp))
                    .clickable { viewModel.dryRunMode = !viewModel.dryRunMode }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (viewModel.dryRunMode) "🔍 DRY RUN ON" else "Dry Run Off",
                    color = if (viewModel.dryRunMode) TerminalYellow else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scan Button
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealVibrant,
                    disabledContainerColor = Color(0x330F766E)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "SCAN DIRECTORY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            // Rename & Move Button
            Button(
                onClick = { viewModel.startRename() },
                enabled = !isRunning && viewModel.scannedItems.any { it.isSelected && it.status == RenameStatus.PENDING },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VioletVibrant,
                    disabledContainerColor = Color(0x334C1D95)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
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
                        color = Color.White
                    )
                }
            }
        }
    }
}"""

code = organize_action_pattern.sub(rewrite_organize_action, code)

# 3. Cleanup Tab:
# move "Junk Folders and Excluded Paths" to settings tab (I'll just copy the TabRow and content to SettingsTab)
# "make sure Junk cleanup also have option to dry run. in this tab cleanup console should big and fill the rest of space."
# "put the "RUN jUNK CLEANUP" button at the bottom."

cleanup_tab_pattern = re.compile(r'(@Composable\s*fun CleanupTab\(viewModel: MediaOrganizerViewModel\)\s*\{)(.*?)(?=\n// ──────────────────────────────────────────────────────────────────────────────\n// TAB 2: SETTINGS)', re.DOTALL)

def rewrite_cleanup_tab(match):
    return """@Composable
fun CleanupTab(viewModel: MediaOrganizerViewModel) {
    val isRunning = viewModel.isCleaning || viewModel.isScanning || viewModel.isRenaming

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        AppHeader()
        Spacer(modifier = Modifier.height(10.dp))

        // Big console fills the rest of space
        TerminalLogView(viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))

        // Action row at the bottom with Dry Run and RUN JUNK CLEANUP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(if (viewModel.dryRunMode) Color(0x33FFFF00) else GlassBackplate, RoundedCornerShape(12.dp))
                    .border(1.dp, if (viewModel.dryRunMode) TerminalYellow else GlassBorderNeon, RoundedCornerShape(12.dp))
                    .clickable { viewModel.dryRunMode = !viewModel.dryRunMode }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (viewModel.dryRunMode) "🔍 DRY RUN ON" else "Dry Run Off",
                    color = if (viewModel.dryRunMode) TerminalYellow else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { viewModel.runJunkAndEmptyCleanup() },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinkAccent,
                    disabledContainerColor = Color(0x33EC4899)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (viewModel.isCleaning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "🧹 RUN JUNK CLEANUP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
"""

code = cleanup_tab_pattern.sub(rewrite_cleanup_tab, code)

# 4. SettingsTab
# move junk/excluded here
# remove dry run section
# remove api source preference
# remove bottom 3 lines text

settings_tab_pattern = re.compile(r'(@OptIn\(ExperimentalMaterial3Api::class\)\s*@Composable\s*fun SettingsTab\(viewModel: MediaOrganizerViewModel, onSaved: \(\) -> Unit\)\s*\{)(.*?)(?=\n@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun PathConfigCard)', re.DOTALL)

def rewrite_settings_tab(match):
    return """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(viewModel: MediaOrganizerViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    var newPath by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Junk, 1 = Excluded
    
    val pathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = getAbsolutePathFromUri(context, it)
            if (path != null) {
                newPath = path
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        AppHeader()
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PathConfigCard(viewModel = viewModel)
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderNeon, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = GlassBackplate),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Manage Folders",
                            color = CyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = CyanPrimary
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Junk Folders") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Excluded Paths") }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        if (selectedTab == 0) {
                            Text(
                                text = "Folders added here will be completely deleted when running Junk Cleanup.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "Folders added here will be SKIPPED during the media scan.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Add new path row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newPath,
                                onValueChange = { newPath = it },
                                placeholder = { Text("Select or type path...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPrimary,
                                    unfocusedBorderColor = Color(0xFF374151)
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { pathLauncher.launch(null) },
                                modifier = Modifier
                                    .background(GlassBackplate, RoundedCornerShape(8.dp))
                                    .border(1.dp, GlassBorderNeon, RoundedCornerShape(8.dp))
                                    .size(50.dp)
                            ) {
                                Text("📂", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPath.isNotBlank()) {
                                        if (selectedTab == 0) viewModel.addJunkPath(context, newPath)
                                        else viewModel.addExcludedPath(context, newPath)
                                        newPath = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Text("ADD", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // List
                        val listToShow = if (selectedTab == 0) viewModel.junkPaths else viewModel.excludedPaths
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            if (listToShow.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No paths configured.", color = TextSecondary, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(listToShow) { path ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(Color(0x33000000), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = path,
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (selectedTab == 0) {
                                                        viewModel.removeJunkPath(context, path)
                                                    } else {
                                                        viewModel.removeExcludedPath(context, path)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Text("❌", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Button(
                    onClick = {
                        viewModel.resetPathsToDefaults()
                        onSaved()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RESET PATHS TO DEFAULTS", fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSaved,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SAVE SETTINGS", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
"""

code = settings_tab_pattern.sub(rewrite_settings_tab, code)

with open('/home/dastgir-siddiq/projects/MediaOrganizer/app/src/main/java/com/mediaorganizer/app/ui/MediaOrganizerUi.kt', 'w') as f:
    f.write(code)
