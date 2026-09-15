package com.mediaorganizer.app

import java.io.File
import java.util.regex.Pattern

object MediaScanner {

    val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "wmv", "flv", "m4v")
    val SUBTITLE_EXTS = setOf("srt", "sub", "ass", "ssa", "vtt")
    val MOVIE_YEAR_REGEX = Pattern.compile("(19|20)\\d{2}")
    
    // Match single episode: S01E02 or multi-episode: S01E01E02, S01E01-E02, S01E01-02
    val EPISODE_PATTERN = Pattern.compile("(S(\\d{2})E)(\\d{2})(?:(?:-?E|-)\\s*(\\d{2}))?", Pattern.CASE_INSENSITIVE)

    val TAGS = listOf(
        // Resolution
        "1080p", "720p", "2160p", "4k", "480p",
        // HDR / color
        "HDR", "HDR10", "HDR10Plus", "SDR", "DV", "DoVi",
        // Video codec
        "HEVC", "x265", "x264", "H.264", "H.265", "H264", "H265",
        "AV1", "10bit", "10-bit",
        // Audio codec
        "AAC", "AC3", "DTS", "DDP", "DDP5.1", "DD5.1", "DD2.0",
        "Atmos", "TrueHD", "FLAC", "EAC3", "5.1", "7.1", "2.0",
        // Source
        "WEBRip", "WEB-DL", "WEB", "DVDScr", "BluRay", "BRRip",
        "HDRip", "CAM", "HDCAM", "HDTC", "HDTS", "DVDRip",
        "BDRip", "REMUX", "Blu-ray",
        // Streaming service
        "AMZN", "NF", "HULU", "DSNP", "ATVP", "PCOK", "HBO",
        "MAX", "PMTP", "CRAV", "iT",
        // Release group / misc
        "YTS", "RARBG", "YIFY", "PROPER", "REPACK", "Multi",
        "EXTENDED", "UNRATED", "DIRECTORS.CUT", "IMAX"
    )

    fun isVideoFile(file: File): Boolean {
        return file.isFile && file.extension.lowercase() in VIDEO_EXTS
    }

    fun isSubtitleFile(file: File): Boolean {
        return file.isFile && file.extension.lowercase() in SUBTITLE_EXTS
    }


    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "-")
    }

    fun cleanReleaseName(name: String): String {
        var cleaned = name
        
        // Strip [Bracketed] prefixes at the start
        cleaned = cleaned.replace(Regex("^\\[.*?\\]\\s*-?\\s*"), "")
        // Strip URL-like prefixes at the start (e.g. www.1TamilMV.Durban - )
        // Also handles cases where dots were replaced by spaces
        cleaned = cleaned.replace(Regex("^(?:www[\\s\\.].*?|[a-zA-Z0-9-]+\\.(?:com|net|org|in|tv|co|uk|us|biz|info|durban|site|me|ru))[\\s-]*-\\s*", RegexOption.IGNORE_CASE), "")
        // Strip @ChannelName -
        cleaned = cleaned.replace(Regex("^@[a-zA-Z0-9_]+\\s*-\\s*"), "")

        cleaned = cleaned.replace(".", " ").replace("_", " ").trim()
        
        for (tag in TAGS) {
            val escaped = Regex.escape(tag)
            cleaned = cleaned.replace(Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE), "")
            
            if (tag.contains(".") || tag.contains("-")) {
                val spaced = tag.replace(".", " ").replace("-", " ")
                val escapedSpaced = Regex.escape(spaced)
                cleaned = cleaned.replace(Regex("\\b$escapedSpaced\\b", RegexOption.IGNORE_CASE), "")
            }
        }
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    data class TvEpisodeInfo(
        val showName: String,
        val season: Int,
        val episode: Int,
        val episodeEnd: Int?,
        val tag: String
    )

    fun classifyTvEpisode(parentDirName: String, filename: String): TvEpisodeInfo? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        val matcher = EPISODE_PATTERN.matcher(nameWithoutExt)
        if (!matcher.find()) return null

        val tagPrefix = matcher.group(1) ?: "S01E"
        val seasonStr = matcher.group(2) ?: "01"
        val episodeStr = matcher.group(3) ?: "01"
        val episodeEndStr = matcher.group(4)

        val season = seasonStr.toIntOrNull() ?: 1
        val episode = episodeStr.toIntOrNull() ?: 1
        val episodeEnd = episodeEndStr?.toIntOrNull()

        val fullTag = if (episodeEnd != null) {
            String.format("S%02dE%02d-E%02d", season, episode, episodeEnd)
        } else {
            String.format("S%02dE%02d", season, episode)
        }

        val showPartEnd = matcher.start()
        val showPart = nameWithoutExt.substring(0, showPartEnd).trim(' ', '.', '-', '_')
        
        val rawShowName = if (showPart.isNotEmpty()) showPart else parentDirName
        val cleanedShowName = sanitizeFilename(cleanReleaseName(rawShowName))

        return TvEpisodeInfo(cleanedShowName, season, episode, episodeEnd, fullTag)
    }

    data class MovieInfo(
        val movieName: String,
        val year: String
    )

    fun classifyMovie(filename: String): MovieInfo? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        val cleaned = cleanReleaseName(nameWithoutExt)
        val matcher = MOVIE_YEAR_REGEX.matcher(cleaned)
        
        var lastMatchStart = -1
        var year = ""
        
        while (matcher.find()) {
            year = matcher.group()
            lastMatchStart = matcher.start()
        }
        
        if (lastMatchStart == -1) return null

        val rawMovieName = cleaned.substring(0, lastMatchStart).trim(' ', '-', '(', ')')
        val movieName = sanitizeFilename(rawMovieName)
        
        if (movieName.isEmpty()) return null

        return MovieInfo(movieName, year)
    }

    sealed class MediaItem {
        abstract val file: File
        abstract val originalName: String
        abstract val companionSubtitles: List<File>
        
        data class Movie(
            override val file: File,
            override val originalName: String,
            val movieName: String,
            val year: String,
            override val companionSubtitles: List<File> = emptyList()
        ) : MediaItem()

        data class TvEpisode(
            override val file: File,
            override val originalName: String,
            val showName: String,
            val season: Int,
            val episode: Int,
            val episodeEnd: Int?,
            val tag: String,
            override val companionSubtitles: List<File> = emptyList()
        ) : MediaItem()
    }

    fun scanDirectory(
        baseDir: File,
        excludedPaths: List<String> = emptyList(),
        onProgress: (String) -> Unit = {}
    ): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        if (!baseDir.exists() || !baseDir.isDirectory) return results

        scanRecursive(baseDir, baseDir, excludedPaths, results, onProgress)
        return results
    }

    private fun scanRecursive(
        currentDir: File,
        baseDir: File,
        excludedPaths: List<String>,
        results: MutableList<MediaItem>,
        onProgress: (String) -> Unit
    ) {
        val files = currentDir.listFiles() ?: return
        
        val currentPath = currentDir.absolutePath
        if (excludedPaths.any { currentPath.startsWith(it) }) {
            return
        }
        
        for (file in files) {
            if (file.isDirectory) {
                scanRecursive(file, baseDir, excludedPaths, results, onProgress)
            } else if (isVideoFile(file)) {
                onProgress("Scanning file: ${file.name}")
                val tvInfo = classifyTvEpisode(currentDir.name, file.name)
                if (tvInfo != null) {
                    val companionSubs = SubtitleHelper.findCompanionSubtitlesAdv(
                        file, excludedPaths, tvInfo.season, tvInfo.episode, tvInfo.episodeEnd
                    )
                    results.add(
                        MediaItem.TvEpisode(
                            file = file,
                            originalName = file.name,
                            showName = tvInfo.showName,
                            season = tvInfo.season,
                            episode = tvInfo.episode,
                            episodeEnd = tvInfo.episodeEnd,
                            tag = tvInfo.tag,
                            companionSubtitles = companionSubs
                        )
                    )
                    continue
                }

                // 2. Try to classify as Movie
                val movieInfo = classifyMovie(file.name)
                if (movieInfo != null) {
                    val companionSubs = SubtitleHelper.findCompanionSubtitlesAdv(file, excludedPaths)
                    results.add(
                        MediaItem.Movie(
                            file = file,
                            originalName = file.name,
                            movieName = movieInfo.movieName,
                            year = movieInfo.year,
                            companionSubtitles = companionSubs
                        )
                    )
                }
            }
        }
    }
}

