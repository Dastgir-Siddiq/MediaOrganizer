# ⚡ MediaOrganizer

MediaOrganizer is an intelligent, automated Android application built with Kotlin and Jetpack Compose (Material 3) that takes the hassle out of organizing your downloaded movies and TV series. It scans unstructured media folders, automatically identifies and fetches metadata (such as verified TV episode titles) from the web, cleanly renames your files with customizable naming templates, and moves them to structured libraries.

**Developed by [Dastgir Siddiq](https://github.com/Dastgir-Siddiq)**

---

## ✨ Features

### 🔍 Intelligent Media Scanning & Camera Exclusion
- **Smart Classification:** Automatically distinguishes between Movies (`Title (Year)`) and TV Series (`S01E01`, `1x01`) based on standard release patterns.
- **Camera & Recording Filter:** Automatically detects and excludes personal camera recordings, DCIM folders, screen captures, voice notes, and burst photos (e.g. `Recording (1) 2024.mp4`, `VID_2024...`, `PXL_...`, `IMG_...`), ensuring your personal media is never misclassified or moved.

### 🏷️ Custom Naming Templates & Audio Languages
- **Flexible Template System:** Fully customize how movies and TV series are renamed in Settings with dynamic placeholders:
  - `{title}`, `{year}`, `{show}`, `{season}`, `{episode}`, `{episode_title}`
  - `{resolution}` (e.g. `1080p`, `2160p`, `4K`, `720p`)
  - `{codec}` (e.g. `x265`, `HEVC`, `x264`, `AVC`)
  - `{audio}` (e.g. `AAC`, `DTS`, `Atmos`, `DD5.1`, `AC3`)
  - `{audio_languages}` / `{language}` (e.g. `Hindi`, `English`, `Telugu`, `Tamil`, `Multi`, `Dual-Audio`)
  - `{source}` (e.g. `BluRay`, `WEB-DL`, `WEBRip`, `HDTV`)
  - `{hdr}` (e.g. `HDR`, `DV`, `HDR10+`)
  - `{edition}` (e.g. `Remastered`, `Director's Cut`, `Extended`)
- **Audio Language Detection:** Accurately extracts audio languages embedded in filenames while safely ignoring subtitle-only language tags.

### 🔄 One-Tap Undo & Transaction History
- **Organize Undo:** Every organize batch is recorded in a local SQLite/Room database (including original path, new path, synchronized subtitle paths, and batch timestamp). Revert any batch with a single tap to restore all files and subtitles to their exact original locations and remove newly created directories.
- **Cleanup Undo:** Junk cleanup batches stage removed files in a secure trash directory and track deleted empty folders, allowing full reversal of any cleanup operation.

### ⚡ Offline Metadata Cache (90-Day TTL)
- Background caching for TVMaze API and Wikipedia episode metadata stored in Room.
- Re-scans become instant, API rate limits are avoided, and media renaming functions completely offline for previously queried TV shows.

### 🐕 Background Watchdog (WorkManager)
- Periodically scans your configured Source Directory in the background when the phone is idle or charging.
- Sends an interactive notification showing newly detected files with direct actions:
  - **Preview:** Opens the app directly to the preview list.
  - **Organize Now:** Processes and organizes the files immediately in the background without needing to keep the UI open.

### 🎛️ Quick Settings Tile
- Includes an Android Quick Settings tile (`Media Scan`) allowing you to trigger an immediate background scan of your Source Directory directly from your system quick settings panel.

### 📋 Live Terminal Logs & Backup/Restore Configuration
- **Terminal Log Export:** View a real-time console log of all scanning, metadata fetching, and file operations. Export the complete terminal log as `.txt` or `.json` for debugging and diagnostic reporting.
- **Config Backup & Restore:** Export all configured paths, exclusions, junk folder definitions, and custom naming templates into a single portable JSON file, and restore it at any time.

### 🌐 Multi-Language Localization
- Full in-app language switching supporting:
  - English
  - Hindi (हिन्दी)
  - Telugu (తెలుగు)
  - Tamil (தமிழ்)
  - Kannada (ಕನ್ನಡ)
  - Malayalam (മലയാളം)
  - Simplified Chinese (简体中文)

### 🎬 Accurate Metadata & Subtitle Synchronization
- **Accurate Episode Titles:** Multi-source metadata resolution combining TVMaze and Wikipedia table scrapers with uniform offset detection, preventing off-by-one or special-episode title shifts.
- **Synchronized Subtitles:** Recognizes multiple subtitle files associated with a video (e.g., `.en.srt`, `.es.srt`, `.2.srt`) and moves them alongside the video, maintaining language tags synchronized with the newly formatted video name.

---

## 🚀 Usage Guide

### 1. Configure Settings ⚙️
Open the **Settings** tab to customize:
- **Source Directory:** The directory where downloaded or unorganized media files reside.
- **Movies Target Directory:** Destination library for movies.
- **TV Series Target Directory:** Destination library for TV shows.
- **Custom Naming Templates:** Define your custom template patterns for movies and TV episodes.
- **Excluded Folders & Junk Folders:** Add custom folders to ignore during scans or target during cleanup.
- **App Language:** Choose your preferred language.

### 2. Organize Media 🎬
1. Navigate to the **Organize** tab.
2. Tap **SCAN**. MediaOrganizer analyzes files, filters out camera recordings, fetches episode titles from the cache or web, and renders a live preview.
3. Filter or search by name, movie, or series.
4. Tap **RENAME & MOVE** to execute the batch.
5. If you ever need to revert, navigate to **Transaction History** and tap **UNDO**.

### 3. Cleanup Junk 🧹
1. Navigate to the **Cleanup** tab.
2. Review the detected junk paths and empty folders.
3. Tap **RUN JUNK CLEANUP**.
4. Revert any cleanup batch at any time using the cleanup history undo button.

---

## 🛠️ Architecture & Tech Stack

- **Language:** Kotlin 1.9.22
- **UI Framework:** Jetpack Compose with Material 3 (Cyberpunk Neon Dark Theme)
- **Database:** AndroidX Room 2.6.1 (with KSP code generator)
- **Background Tasks:** AndroidX WorkManager 2.9.0
- **Asynchronous Execution:** Kotlin Coroutines & StateFlow
- **Web Scraping & APIs:** TVMaze REST API & Jsoup 1.17.2 for Wikipedia HTML table parsing
- **Testing:** Comprehensive JUnit 4 test suite covering scanner regex, template formatting, batch undo, cleanup undo, episode title alignment, and config backup/restore.

---

## 📦 Building and Testing

### Prerequisites
- JDK 17 or JDK 21
- Android SDK 34 (Android 14)

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
This project is developed and maintained by **Dastgir Siddiq**. All rights reserved.
