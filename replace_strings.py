import re

UI_FILE = "app/src/main/java/com/mediaorganizer/app/ui/MediaOrganizerUi.kt"

with open(UI_FILE, 'r') as f:
    content = f.read()

replacements = {
    r'Text\("Text to Find"': r'Text(stringResource(R.string.text_to_find)"',
    r'Text\("App Language"': r'Text(stringResource(R.string.app_language)"',
    r'Text\("CLOSE"': r'Text(stringResource(R.string.close)"',
    r'Text\("DRY RUN"': r'Text(stringResource(R.string.dry_run)"',
    r'Text\("Target Directory"': r'Text(stringResource(R.string.target_directory)"',
    r'Text\("No items match your filter."': r'Text(stringResource(R.string.no_items_match_filter)"',
    r'Text\("CLEAR"': r'Text(stringResource(R.string.clear)"',
    r'Text\("⚡ Media Organiser"': r'Text(stringResource(R.string.media_organiser)"',
    r'Text\("ADD"': r'Text(stringResource(R.string.add)"',
    r'Text\("SCAN"': r'Text(stringResource(R.string.scan)"',
    r'Text\("RUN"': r'Text(stringResource(R.string.run)"',
    r'Text\("Find & Replace"': r'Text(stringResource(R.string.find_replace)"',
    r'Text\("🔬 DRY RUN — No files were moved"': r'Text(stringResource(R.string.dry_run_no_files_moved)"',
    r'Text\("Sort: "': r'Text(stringResource(R.string.sort)"',
    r'Text\("Change"': r'Text(stringResource(R.string.change)"',
    r'Text\("CANCEL"': r'Text(stringResource(R.string.cancel)"',
    r'Text\("⚡ Cyberpunk Console Log Viewport"': r'Text(stringResource(R.string.cyberpunk_console_log_viewport)"',
    r'Text\("Developed by"': r'Text(stringResource(R.string.developed_by)"',
    r'Text\("Settings"': r'Text(stringResource(R.string.settings)"',
    r'Text\("SYSTEM ACTIVE. Ready for file sorting, TVMaze indexing, or standalone device cleanup sweeps."': r'Text(stringResource(R.string.system_active_ready)"',
    r'Text\("FIND & REPLACE"': r'Text(stringResource(R.string.find_replace_btn)"',
    r'Text\("JUNK CLEANUP"': r'Text(stringResource(R.string.junk_cleanup_btn)"',
    r'Text\("Cleanup"': r'Text(stringResource(R.string.cleanup)"',
    r'Text\("Organize"': r'Text(stringResource(R.string.organize)"',
    r'Text\("Scan directory to list and rename Movies or TV Series.\\nUse the Cleanup tab to clear empty folders safely."': r'Text(stringResource(R.string.scan_directory_desc)"',
    r'Text\("Replace With"': r'Text(stringResource(R.string.replace_with)"',
    r'Text\("Dastgir Siddiq"': r'Text(stringResource(R.string.dastgir_siddiq)"',
    r'Text\("✨ Ready to Scan"': r'Text(stringResource(R.string.ready_to_scan)"',
    r'Text\("Add new path keyword..."': r'Text(stringResource(R.string.add_new_path_keyword)"',
    r'Text\("Clear"': r'Text(stringResource(R.string.clear_capital)"',
    r'title = "Excluded Paths \(Ignored during scan\)"': r'title = stringResource(R.string.excluded_paths)',
    r'title = "Junk Folders \(Deleted during cleanup\)"': r'title = stringResource(R.string.junk_folders)',
    r'PathConfigItem\(\s*"Source Directory"': r'PathConfigItem(\n                    stringResource(R.string.source_directory)',
    r'PathConfigItem\(\s*"Movies Target"': r'PathConfigItem(\n                    stringResource(R.string.movies_target)',
    r'PathConfigItem\(\s*"TV Series Target"': r'PathConfigItem(\n                    stringResource(R.string.tv_series_target)',
}

# The previous replace pattern had an extra quote at the end for Text(stringResource...)" which is wrong
# Let's fix the replacements dictionary

replacements = {
    r'Text\(\s*"Text to Find"': r'Text(stringResource(R.string.text_to_find)',
    r'Text\(\s*"App Language"': r'Text(stringResource(R.string.app_language)',
    r'Text\(\s*"CLOSE"': r'Text(stringResource(R.string.close)',
    r'Text\(\s*"DRY RUN"': r'Text(stringResource(R.string.dry_run)',
    r'Text\(\s*"Target Directory"': r'Text(stringResource(R.string.target_directory)',
    r'Text\(\s*"No items match your filter."': r'Text(stringResource(R.string.no_items_match_filter)',
    r'Text\(\s*"CLEAR"': r'Text(stringResource(R.string.clear)',
    r'Text\(\s*"⚡ Media Organiser"': r'Text(stringResource(R.string.media_organiser)',
    r'Text\(\s*"ADD"': r'Text(stringResource(R.string.add)',
    r'Text\(\s*"SCAN"': r'Text(stringResource(R.string.scan)',
    r'Text\(\s*"RUN"': r'Text(stringResource(R.string.run)',
    r'Text\(\s*"Find & Replace"': r'Text(stringResource(R.string.find_replace)',
    r'Text\(\s*"🔬 DRY RUN — No files were moved"': r'Text(stringResource(R.string.dry_run_no_files_moved)',
    r'Text\(\s*"Sort: "': r'Text(stringResource(R.string.sort)',
    r'Text\(\s*"Change"': r'Text(stringResource(R.string.change)',
    r'Text\(\s*"CANCEL"': r'Text(stringResource(R.string.cancel)',
    r'Text\(\s*"⚡ Cyberpunk Console Log Viewport"': r'Text(stringResource(R.string.cyberpunk_console_log_viewport)',
    r'Text\(\s*"Developed by"': r'Text(stringResource(R.string.developed_by)',
    r'Text\(\s*"Settings"': r'Text(stringResource(R.string.settings)',
    r'Text\(\s*"SYSTEM ACTIVE. Ready for file sorting, TVMaze indexing, or standalone device cleanup sweeps."': r'Text(stringResource(R.string.system_active_ready)',
    r'Text\(\s*"FIND & REPLACE"': r'Text(stringResource(R.string.find_replace_btn)',
    r'Text\(\s*"JUNK CLEANUP"': r'Text(stringResource(R.string.junk_cleanup_btn)',
    r'Text\(\s*"Cleanup"': r'Text(stringResource(R.string.cleanup)',
    r'Text\(\s*"Organize"': r'Text(stringResource(R.string.organize)',
    r'Text\(\s*"Scan directory to list and rename Movies or TV Series.\\nUse the Cleanup tab to clear empty folders safely."': r'Text(stringResource(R.string.scan_directory_desc)',
    r'Text\(\s*"Replace With"': r'Text(stringResource(R.string.replace_with)',
    r'Text\(\s*"Dastgir Siddiq"': r'Text(stringResource(R.string.dastgir_siddiq)',
    r'Text\(\s*"✨ Ready to Scan"': r'Text(stringResource(R.string.ready_to_scan)',
    r'Text\(\s*"Add new path keyword..."': r'Text(stringResource(R.string.add_new_path_keyword)',
    r'Text\(\s*"Clear"': r'Text(stringResource(R.string.clear_capital)',
    r'Text\(\s*text\s*=\s*"Scan directory to list and rename Movies or TV Series.\\nUse the Cleanup tab to clear empty folders safely."': r'Text(text = stringResource(R.string.scan_directory_desc)',
    r'Text\(\s*text\s*=\s*"SYSTEM ACTIVE. Ready for file sorting, TVMaze indexing, or standalone device cleanup sweeps."': r'Text(text = stringResource(R.string.system_active_ready)',
    r'title\s*=\s*"Excluded Paths \(Ignored during scan\)"': r'title = stringResource(R.string.excluded_paths)',
    r'title\s*=\s*"Junk Folders \(Deleted during cleanup\)"': r'title = stringResource(R.string.junk_folders)',
    r'PathConfigItem\(\s*"Source Directory"': r'PathConfigItem(\n                    stringResource(R.string.source_directory)',
    r'PathConfigItem\(\s*"Movies Target"': r'PathConfigItem(\n                    stringResource(R.string.movies_target)',
    r'PathConfigItem\(\s*"TV Series Target"': r'PathConfigItem(\n                    stringResource(R.string.tv_series_target)',
}

for old, new in replacements.items():
    content = re.sub(old, new, content)

# Also need to add R import
if "import com.mediaorganizer.app.R" not in content:
    content = content.replace("import androidx.compose.ui.Alignment", "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.res.stringResource\nimport com.mediaorganizer.app.R")

with open(UI_FILE, 'w') as f:
    f.write(content)
print("Replaced strings successfully.")
