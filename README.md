# MediaOrganizer

MediaOrganizer is an intelligent, automated Android application built with Kotlin and Jetpack Compose that takes the hassle out of organizing your downloaded movies and TV series. It scans unstructured media folders, automatically identifies and fetches metadata (like TV episode titles) from the web, cleanly renames your files, and moves them to structured libraries.

**Developed by Dastgir Siddiq**

## ✨ Features

- **Intelligent Media Scanning:**
  Automatically distinguishes between Movies and TV Series based on filenames (e.g., matching standard `S01E01` or `1x01` formats).
  
- **Automated Metadata Fetching:**
  Fetches episode names directly from the [TVMaze API](https://www.tvmaze.com/api). If TVMaze doesn't have the data, it seamlessly falls back to scraping Wikipedia for the episode list.
  
- **Clean File Renaming & Moving:**
  - **Movies:** Renames to `Movie Title (Year).ext`
  - **TV Series:** Organizes into designated Season folders (`TV Series Name/Season 01/`) and renames files to `Show Name S01E01 - Episode Title.ext`.
  
- **Advanced Subtitle Handling:**
  Recognizes multiple subtitle files associated with a video (e.g., `.en.srt`, `.es.srt`, `.2.srt`) and moves them alongside the video, maintaining their language and identifier tags perfectly synchronized with the newly generated video name.
  
- **Junk Cleanup Utility:**
  Includes a built-in cleanup tool that scans your device for specified junk folders (like `.thumbnails`, caches, and hidden system folders) and deletes them, freeing up space. It also cleans up any empty folders left behind after moving your media files.
  
- **Fully Customizable Paths:**
  Configure your Source Directory, Movies Target Directory, and TV Series Target Directory directly within the app. You can also customize Excluded Paths to ignore certain folders during scans, and manage your list of Junk Folders.

- **Cyberpunk Material3 UI:**
  Features a sleek, dark-themed UI built with Jetpack Compose Material3, featuring a live terminal log view, scan progress tracking, and detailed preview cards before you commit to moving files.

## 🚀 Usage

### 1. Configure Settings
Open the **Settings ⚙️** tab to set your paths:
- **Source Directory:** The folder where your downloaded/unorganized files reside.
- **Movies Target:** The destination library for your movies.
- **TV Series Target:** The destination library for your TV shows.

*You can also configure which folders should be ignored during scans, and which folders the Cleanup tool should target.*

### 2. Organize Media
1. Go to the **Organize 🎬** tab.
2. Tap **SCAN**. The app will analyze the Source Directory, fetch episode metadata, and generate a preview of how files will be renamed.
3. Review the items in the preview list. You can filter by Movies/TV or search by name.
4. Tap **RENAME & MOVE** to execute the changes. The terminal will log the progress as files are moved to their target directories.

### 3. Cleanup Junk
1. Go to the **Cleanup 🧹** tab.
2. Tap **RUN JUNK CLEANUP**.
3. The app will remove the specified junk folders and delete any empty directories left behind in your Source Directory.

## 🛠️ Built With
* Kotlin
* Jetpack Compose (Material3)
* Kotlin Coroutines & Flow
* Jsoup (for Wikipedia HTML parsing)
* JSON parsing (for TVMaze API)

## 📦 Building

To build the project locally using Gradle:
```bash
./gradlew assembleDebug
```
The APK will be available in `app/build/outputs/apk/debug/`.
