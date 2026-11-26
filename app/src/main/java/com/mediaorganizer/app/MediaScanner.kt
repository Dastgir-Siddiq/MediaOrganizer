package com.mediaorganizer.app

import java.io.File
import java.util.Calendar
import java.util.regex.Pattern

object MediaScanner {

    val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "wmv", "flv", "m4v")
    val SUBTITLE_EXTS = setOf("srt", "sub", "ass", "ssa", "vtt")
    val MOVIE_YEAR_REGEX = Pattern.compile("(?<![A-Za-z0-9])((?:19|20)\\d{2})(?![A-Za-z0-9])")

    private const val MIN_VALID_MOVIE_YEAR = 1900

    val IGNORED_DIR_NAMES = setOf(
        "dcim",
        "camera",
        "screenrecordings",
        "screen recordings",
        "screen_recordings",
        "screenrecorder",
        "screen recorder",
        "screen_recorder",
        "screenshots",
        ".thumbnails"
    )

    // Unambiguous prefixes used by device cameras, screen recorders, messaging apps, and mobile editors.
    private val CAMERA_RECORDING_PREFIX_REGEX = Regex(
        "^(?:" +
            "vid|pxl|img|mvi|mov|svid|dji|dsc|dscn|dscf|_dsc|gopr|mah0|rpreplay|" +
            "mvimg|pro_vid|lrv|dcim|camcorder|" +
            "screen[-_\\s]?recording|screen[-_\\s]?recorder|screenrecorder|" +
            "screen[-_\\s]?record|screen[-_\\s]?capture|srecorder|xrecorder|" +
            "az[-_\\s]?screen|mobizen|screenshot|" +
            "inshot|youcut|capcut|kinemaster|vivavideo|filmora|" +
            "snapchat|fb_vid|whatsapp[-_\\s]?video|telegram[-_\\s]?video|" +
            "slomo|slo[-_\\s]?mo|timelapse|hyperlapse|" +
            "(?:snow|soda|b612|c360)_\\d|" +
            "lv_\\d+_|km_\\d+_|vn\\d{6,}|screen-\\d{6,}" +
        ")(?:[._\\-\\s\\d]|$)",
        RegexOption.IGNORE_CASE
    )

    private val WHATSAPP_SUFFIX_REGEX = Regex("[-_]WA\\d{3,}(?:[^0-9]|$)", RegexOption.IGNORE_CASE)
    private val MAC_RECORDING_REGEX = Regex(
        "^Movie\\s+on\\s+\\d{1,4}[-./]\\d{1,2}[-./]\\d{1,4}\\s+at\\s+\\d",
        RegexOption.IGNORE_CASE
    )

    // Date and timestamp patterns commonly embedded in camera, screen, and social recordings.
    private val COMPACT_DATE_REGEX = Regex(
        "(?<![0-9])(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])(?:\\d{1,9})?(?![0-9])"
    )
    private val YMD_SEP_DATE_REGEX = Regex(
        "(?<![0-9])(?:19|20)\\d{2}[-_./\\s](?:0[1-9]|1[0-2])[-_./\\s](?:0[1-9]|[12]\\d|3[01])(?![0-9])"
    )
    private val YMD_SLASH_DATE_REGEX = Regex(
        "(?<![0-9])(?:19|20)\\d{2}[-_/](?:0?[1-9]|1[0-2])[-_/](?:0?[1-9]|[12]\\d|3[01])(?![0-9])"
    )
    private val DMY_OR_MDY_SLASH_DATE_REGEX = Regex(
        "(?<![0-9])(?:(?:0?[1-9]|[12]\\d|3[01])[-_/](?:0?[1-9]|1[0-2])|(?:0?[1-9]|1[0-2])[-_/](?:0?[1-9]|[12]\\d|3[01]))[-_/](?:19|20)\\d{2}(?![0-9])"
    )
    private val DMY_OR_MDY_DOT_SPACE_DATE_REGEX = Regex(
        "(?<![0-9])(?:(?:0[1-9]|[12]\\d|3[01])[-_.\\s](?:0[1-9]|1[0-2])|(?:0[1-9]|1[0-2])[-_.\\s](?:0[1-9]|[12]\\d|3[01]))[-_.\\s](?:19|20)\\d{2}(?![0-9])"
    )
    private val YEAR_MMDD_REGEX = Regex(
        "(?<![0-9])(?:19|20)\\d{2}[-_/](?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])(?![0-9])"
    )
    private val TIME_SEP_REGEX = Regex(
        "(?<![0-9])(?:[01]?\\d|2[0-3])([._-])[0-5]\\d\\1[0-5]\\d(?![0-9])"
    )
    private val YEAR_HHMMSS_REGEX = Regex(
        "(?<![0-9])(?:19|20)\\d{2}[-_.\\s](?:[01]\\d|2[0-3])[0-5]\\d[0-5]\\d(?![0-9])"
    )
    private val EPOCH_TIMESTAMP_REGEX = Regex(
        "(?<![0-9])1[4-9]\\d{8}(?:\\d{3})?(?![0-9])"
    )

    // Video frame dimension pairs like 1920x1080, 1920_1080, 1080 1920, 2048x1080.
    private val RESOLUTION_PAIR_REGEX = Regex(
        "(?<![A-Za-z0-9])(?:(?:1920|2048)[-_.x×*\\s]+(?:720|1080|1152|1200|1440|1536|2048|2160)|(?:720|1080|1152|1200|1440|1536|2048|2160)[-_.x×*\\s]+(?:1920|2048))(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_RECORDING_TITLE_REGEX = Regex(
        "^(?:rec|cam|sam|win|scr|v|video|clip|record|recording|camera|capture|screen|untitled|sample|test|file|timelapse|hyperlapse)(?:\\s*(?:\\(\\d+\\)|\\d+))?$",
        RegexOption.IGNORE_CASE
    )

    private val STRONG_MOVIE_RELEASE_TAGS = listOf(
        "WEBRip", "WEB-DL", "DVDScr", "BluRay", "BRRip",
        "HDRip", "DVDRip", "BDRip", "REMUX", "Blu-ray",
        "YTS", "RARBG", "YIFY", "x264", "x265", "HEVC",
        "H.264", "H.265", "H264", "H265", "AV1", "DTS",
        "Atmos", "TrueHD", "DDP", "DDP5.1", "DD5.1",
        "EXTENDED", "UNRATED", "DIRECTORS.CUT", "IMAX"
    )

    private val DATE_OR_TIMESTAMP_PATTERNS = listOf(
        COMPACT_DATE_REGEX,
        YMD_SEP_DATE_REGEX,
        YMD_SLASH_DATE_REGEX,
        DMY_OR_MDY_SLASH_DATE_REGEX,
        DMY_OR_MDY_DOT_SPACE_DATE_REGEX,
        YEAR_MMDD_REGEX,
        TIME_SEP_REGEX,
        YEAR_HHMMSS_REGEX,
        EPOCH_TIMESTAMP_REGEX
    )
    
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
        val tag: String,
        val tags: MediaTags = MediaTags(),
        val titleHint: String = ""
    )

    private fun maxValidMovieYear(): Int {
        return maxOf(Calendar.getInstance().get(Calendar.YEAR) + 2, 2028)
    }

    private fun isUnambiguousCameraRecording(nameWithoutExt: String): Boolean {
        return CAMERA_RECORDING_PREFIX_REGEX.containsMatchIn(nameWithoutExt) ||
            WHATSAPP_SUFFIX_REGEX.containsMatchIn(nameWithoutExt) ||
            MAC_RECORDING_REGEX.containsMatchIn(nameWithoutExt)
    }

    private fun isLikelyCameraOrPersonalRecording(nameWithoutExt: String): Boolean {
        if (isUnambiguousCameraRecording(nameWithoutExt)) return true
        return DATE_OR_TIMESTAMP_PATTERNS.any { it.containsMatchIn(nameWithoutExt) }
    }

    private fun stripResolutionPairs(name: String): String {
        return RESOLUTION_PAIR_REGEX.replace(name, " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun hasExplicitBracketedYear(nameWithoutExt: String, year: String): Boolean {
        return nameWithoutExt.contains("($year)") || nameWithoutExt.contains("[$year]")
    }

    private fun hasStrongReleaseTag(nameWithoutExt: String): Boolean {
        val spaced = nameWithoutExt.replace(".", " ").replace("_", " ")
        return STRONG_MOVIE_RELEASE_TAGS.any { tag ->
            val escaped = Regex.escape(tag)
            if (Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE).containsMatchIn(spaced)) {
                true
            } else if (tag.contains(".") || tag.contains("-")) {
                val tagSpaced = tag.replace(".", " ").replace("-", " ")
                Regex("\\b${Regex.escape(tagSpaced)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(spaced)
            } else {
                false
            }
        }
    }

    private fun isNumericNonMovieTitle(
        movieName: String,
        nameWithoutExt: String,
        year: String
    ): Boolean {
        if (movieName.matches(Regex("^0\\d+$")) || movieName.matches(Regex("^\\d{5,}$"))) {
            return true
        }
        if (movieName.any { it.isLetter() }) return false
        if (hasExplicitBracketedYear(nameWithoutExt, year) || hasStrongReleaseTag(nameWithoutExt)) {
            return false
        }
        val numericTokens = movieName.split(Regex("[\\s-]+")).filter { it.isNotEmpty() }
        if (numericTokens.size >= 2) {
            return true
        }
        val singleNum = movieName.toIntOrNull()
        val yearNum = year.toIntOrNull()
        return singleNum != null && yearNum != null &&
            singleNum in MIN_VALID_MOVIE_YEAR..maxValidMovieYear() &&
            singleNum > yearNum
    }

    private fun isAmbiguousRecordingTitle(
        nameWithoutExt: String,
        cleanedMovieName: String,
        year: String
    ): Boolean {
        if (hasExplicitBracketedYear(nameWithoutExt, year) || hasStrongReleaseTag(nameWithoutExt)) {
            return false
        }
        if (GENERIC_RECORDING_TITLE_REGEX.matches(cleanedMovieName)) {
            return true
        }
        val spacedRaw = nameWithoutExt.replace(".", " ").replace("_", " ").replace(Regex("\\s+"), " ").trim()
        val yearRegex = Regex("(?<![A-Za-z0-9])${Regex.escape(year)}(?![A-Za-z0-9])")
        val lastMatch = yearRegex.findAll(spacedRaw).lastOrNull() ?: return false
        val rawBeforeYear = spacedRaw.substring(0, lastMatch.range.first).trim(' ', '-', '(', ')', '[', ']')
        return GENERIC_RECORDING_TITLE_REGEX.matches(rawBeforeYear)
    }

    fun isPathExcluded(file: File, excludedPaths: List<String>): Boolean {
        val path = file.absolutePath.trimEnd('/')
        return excludedPaths.any { raw ->
            val pattern = raw.trim().trimEnd('/')
            if (pattern.isEmpty()) {
                false
            } else if (pattern.startsWith("/")) {
                path == pattern || path.startsWith("$pattern/")
            } else {
                path.contains(pattern, ignoreCase = true)
            }
        }
    }

    private fun isIgnoredCameraDir(dir: File, baseDir: File): Boolean {
        return dir.absolutePath != baseDir.absolutePath && dir.name.lowercase() in IGNORED_DIR_NAMES
    }

    fun classifyTvEpisode(parentDirName: String, filename: String): TvEpisodeInfo? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        if (isUnambiguousCameraRecording(nameWithoutExt)) return null

        val matcher = EPISODE_PATTERN.matcher(nameWithoutExt)
        if (!matcher.find()) return null

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
        val tags = MediaTagExtractor.extract(nameWithoutExt)
        val titleHint = extractEpisodeTitleHint(nameWithoutExt.substring(matcher.end()))

        return TvEpisodeInfo(cleanedShowName, season, episode, episodeEnd, fullTag, tags, titleHint)
    }

    internal fun extractEpisodeTitleHint(afterTag: String): String {
        val trimmed = afterTag.trimStart(' ', '.', '-', '_')
        if (trimmed.isEmpty()) return ""

        val stopTagRegex = Regex(
            "^(?:2160p|4k|1080p|720p|480p|1080i|576p|hevc|x265|x264|h\\.?265|h\\.?264|av1|" +
                "10bit|10-bit|8bit|web-?dl|webrip|web|bluray|blu-?ray|brrip|bdrip|hdrip|dvdrip|dvdscr|remux|hdtv|" +
                "aac|ac3|dts|ddp|ddp5\\.?1|dd5\\.?1|dd2\\.?0|eac3|atmos|truehd|flac|hdr|hdr10|hdr10plus|dovi|dv|sdr|" +
                "msub|msubs|esub|esubs|engsub|engsubs|hardsub|softsub|sub|subs|subbed|yts|rarbg|yify|proper|repack|" +
                "multi|dual|extended|unrated|imax|amzn|dsnp|atvp|pcok|pmtp|crav|moviesmod|1tamilmv)$",
            RegexOption.IGNORE_CASE
        )
        val domainTokenRegex = Regex(
            "^[a-z0-9-]+\\.(?:com|net|org|in|tv|co|uk|us|biz|info|durban|site|me|ru|fi|mx|cc|io)$",
            RegexOption.IGNORE_CASE
        )

        val rawTokens = trimmed.split(Regex("[.\\s_]+")).filter { it.isNotBlank() }
        val titleTokens = mutableListOf<String>()
        for (rawToken in rawTokens) {
            if (rawToken.startsWith("[") || rawToken.startsWith("(") || domainTokenRegex.matches(rawToken)) {
                break
            }
            val bare = rawToken.trim('[', ']', '(', ')', '-')
            if (bare.isEmpty() || stopTagRegex.matches(bare)) {
                break
            }
            titleTokens.add(rawToken)
        }

        if (titleTokens.isEmpty()) return ""

        // Drop trailing audio-language-only tokens if followed by nothing (e.g., "Show S01E01 Hindi English")
        while (titleTokens.isNotEmpty() && MediaTagExtractor.AUDIO_LANGUAGE_MAP.containsKey(titleTokens.last().lowercase())) {
            val allLangs = titleTokens.all {
                MediaTagExtractor.AUDIO_LANGUAGE_MAP.containsKey(it.lowercase()) ||
                    it.lowercase() in setOf("dual", "multi", "audio")
            }
            if (allLangs) {
                return ""
            }
            break
        }

        return sanitizeFilename(titleTokens.joinToString(" ").trim(' ', '-', '(', ')', '[', ']'))
    }

    data class MovieInfo(
        val movieName: String,
        val year: String,
        val tags: MediaTags = MediaTags()
    )

    fun classifyMovie(filename: String): MovieInfo? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        if (isLikelyCameraOrPersonalRecording(nameWithoutExt)) return null

        val cleaned = stripResolutionPairs(cleanReleaseName(nameWithoutExt))
        val matcher = MOVIE_YEAR_REGEX.matcher(cleaned)
        
        val maxYear = maxValidMovieYear()
        var lastMatchStart = -1
        var year = ""
        
        while (matcher.find()) {
            val candidate = matcher.group(1) ?: matcher.group()
            val yearInt = candidate.toIntOrNull() ?: continue
            if (yearInt in MIN_VALID_MOVIE_YEAR..maxYear) {
                year = candidate
                lastMatchStart = matcher.start(1)
            }
        }
        
        if (lastMatchStart == -1) return null

        val rawMovieName = cleaned.substring(0, lastMatchStart)
            .trimStart(' ', '-', ')', ']')
            .trimEnd(' ', '-', '(', '[')
        val movieName = sanitizeFilename(rawMovieName)
        
        if (movieName.isEmpty()) return null
        if (isNumericNonMovieTitle(movieName, nameWithoutExt, year)) return null
        if (isAmbiguousRecordingTitle(nameWithoutExt, movieName, year)) return null

        val tags = MediaTagExtractor.extract(nameWithoutExt)
        return MovieInfo(movieName, year, tags)
    }

    sealed class MediaItem {
        abstract val file: File
        abstract val originalName: String
        abstract val companionSubtitles: List<File>
        abstract val tags: MediaTags
        
        data class Movie(
            override val file: File,
            override val originalName: String,
            val movieName: String,
            val year: String,
            override val companionSubtitles: List<File> = emptyList(),
            override val tags: MediaTags = MediaTags()
        ) : MediaItem()

        data class TvEpisode(
            override val file: File,
            override val originalName: String,
            val showName: String,
            val season: Int,
            val episode: Int,
            val episodeEnd: Int?,
            val tag: String,
            override val companionSubtitles: List<File> = emptyList(),
            override val tags: MediaTags = MediaTags(),
            val titleHint: String = ""
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
        if (isPathExcluded(currentDir, excludedPaths) || isIgnoredCameraDir(currentDir, baseDir)) {
            return
        }

        val files = currentDir.listFiles() ?: return

        for (file in files) {
            if (isPathExcluded(file, excludedPaths)) {
                continue
            }
            if (file.isDirectory) {
                scanRecursive(file, baseDir, excludedPaths, results, onProgress)
            } else if (file.extension.equals("zip", ignoreCase = true)) {
                onProgress("Checking zip file: ${file.name}")
                if (hasTvEpisodesInZip(file, currentDir.name)) {
                    val extractDir = File(currentDir, file.nameWithoutExtension)
                    if (!extractDir.exists()) extractDir.mkdirs()
                    onProgress("Unzipping episodes from: ${file.name}")
                    if (unzipFile(file, extractDir)) {
                        scanRecursive(extractDir, baseDir, excludedPaths, results, onProgress)
                    }
                }
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
                            companionSubtitles = companionSubs,
                            tags = tvInfo.tags,
                            titleHint = tvInfo.titleHint
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
                            companionSubtitles = companionSubs,
                            tags = movieInfo.tags
                        )
                    )
                }
            }
        }
    }

    private fun hasTvEpisodesInZip(zipFile: File, parentDirName: String): Boolean {
        try {
            java.util.zip.ZipFile(zipFile).use { zip ->
                for (entry in zip.entries()) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in VIDEO_EXTS) {
                            if (classifyTvEpisode(parentDirName, name) != null) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore corrupted zip
        }
        return false
    }

    private fun unzipFile(zipFile: File, destDir: File): Boolean {
        try {
            java.util.zip.ZipFile(zipFile).use { zip ->
                for (entry in zip.entries()) {
                    val file = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

