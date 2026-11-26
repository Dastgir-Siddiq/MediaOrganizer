package com.mediaorganizer.app

data class MediaTags(
    val resolution: String = "",
    val codec: String = "",
    val audio: String = "",
    val audioLanguages: String = "",
    val source: String = "",
    val hdr: String = "",
    val edition: String = ""
)

object MediaTagExtractor {

    private val RESOLUTION_PATTERNS = listOf(
        "2160p" to Regex("\\b2160p\\b", RegexOption.IGNORE_CASE),
        "4K" to Regex("\\b4k\\b", RegexOption.IGNORE_CASE),
        "1080p" to Regex("\\b1080p\\b", RegexOption.IGNORE_CASE),
        "720p" to Regex("\\b720p\\b", RegexOption.IGNORE_CASE),
        "480p" to Regex("\\b480p\\b", RegexOption.IGNORE_CASE)
    )

    private val CODEC_PATTERNS = listOf(
        "HEVC" to Regex("\\bHEVC\\b", RegexOption.IGNORE_CASE),
        "x265" to Regex("\\bx265\\b", RegexOption.IGNORE_CASE),
        "H.265" to Regex("\\bH[.\\s-]?265\\b", RegexOption.IGNORE_CASE),
        "x264" to Regex("\\bx264\\b", RegexOption.IGNORE_CASE),
        "H.264" to Regex("\\bH[.\\s-]?264\\b", RegexOption.IGNORE_CASE),
        "AV1" to Regex("\\bAV1\\b", RegexOption.IGNORE_CASE)
    )

    private val AUDIO_CODEC_PATTERNS = listOf(
        "TrueHD" to Regex("\\bTrueHD\\b", RegexOption.IGNORE_CASE),
        "DDP5.1" to Regex("\\bDDP\\s*5[.\\s]1\\b", RegexOption.IGNORE_CASE),
        "DD5.1" to Regex("\\bDD\\s*5[.\\s]1\\b", RegexOption.IGNORE_CASE),
        "DD2.0" to Regex("\\bDD\\s*2[.\\s]0\\b", RegexOption.IGNORE_CASE),
        "DDP" to Regex("\\bDDP\\b", RegexOption.IGNORE_CASE),
        "EAC3" to Regex("\\bEAC3\\b", RegexOption.IGNORE_CASE),
        "DTS" to Regex("\\bDTS\\b", RegexOption.IGNORE_CASE),
        "AC3" to Regex("\\bAC3\\b", RegexOption.IGNORE_CASE),
        "AAC" to Regex("\\bAAC\\b", RegexOption.IGNORE_CASE),
        "FLAC" to Regex("\\bFLAC\\b", RegexOption.IGNORE_CASE)
    )

    private val ATMOS_PATTERN = Regex("\\bAtmos\\b", RegexOption.IGNORE_CASE)

    private val AUDIO_CHANNEL_PATTERNS = listOf(
        "7.1" to Regex("(?<![A-Za-z0-9])7[.\\s]1(?![A-Za-z0-9])"),
        "5.1" to Regex("(?<![A-Za-z0-9])5[.\\s]1(?![A-Za-z0-9])"),
        "2.0" to Regex("(?<![A-Za-z0-9])2[.\\s]0(?![A-Za-z0-9])")
    )

    private val SOURCE_PATTERNS = listOf(
        "REMUX" to Regex("\\bREMUX\\b", RegexOption.IGNORE_CASE),
        "BluRay" to Regex("\\bBlu-?Ray\\b", RegexOption.IGNORE_CASE),
        "WEB-DL" to Regex("\\bWEB[.\\s-]?DL\\b", RegexOption.IGNORE_CASE),
        "WEBRip" to Regex("\\bWEBRip\\b", RegexOption.IGNORE_CASE),
        "BRRip" to Regex("\\bBRRip\\b", RegexOption.IGNORE_CASE),
        "BDRip" to Regex("\\bBDRip\\b", RegexOption.IGNORE_CASE),
        "HDRip" to Regex("\\bHDRip\\b", RegexOption.IGNORE_CASE),
        "DVDRip" to Regex("\\bDVDRip\\b", RegexOption.IGNORE_CASE),
        "DVDScr" to Regex("\\bDVDScr\\b", RegexOption.IGNORE_CASE),
        "HDTS" to Regex("\\bHDTS\\b", RegexOption.IGNORE_CASE),
        "HDTC" to Regex("\\bHDTC\\b", RegexOption.IGNORE_CASE),
        "HDCAM" to Regex("\\bHDCAM\\b", RegexOption.IGNORE_CASE),
        "WEB" to Regex("\\bWEB\\b", RegexOption.IGNORE_CASE),
        "CAM" to Regex("\\bCAM\\b", RegexOption.IGNORE_CASE)
    )

    private val HDR_PATTERNS = listOf(
        "HDR10Plus" to Regex("\\bHDR10Plus\\b", RegexOption.IGNORE_CASE),
        "HDR10" to Regex("\\bHDR10\\b", RegexOption.IGNORE_CASE),
        "HDR" to Regex("\\bHDR\\b", RegexOption.IGNORE_CASE),
        "DoVi" to Regex("\\bDoVi\\b", RegexOption.IGNORE_CASE),
        "DV" to Regex("\\bDV\\b", RegexOption.IGNORE_CASE),
        "SDR" to Regex("\\bSDR\\b", RegexOption.IGNORE_CASE)
    )

    private val EDITION_PATTERNS = listOf(
        "DIRECTORS CUT" to Regex("\\bDIRECTORS[.\\s_-]?CUT\\b", RegexOption.IGNORE_CASE),
        "EXTENDED" to Regex("\\bEXTENDED\\b", RegexOption.IGNORE_CASE),
        "UNRATED" to Regex("\\bUNRATED\\b", RegexOption.IGNORE_CASE),
        "IMAX" to Regex("\\bIMAX\\b", RegexOption.IGNORE_CASE),
        "PROPER" to Regex("\\bPROPER\\b", RegexOption.IGNORE_CASE),
        "REPACK" to Regex("\\bREPACK\\b", RegexOption.IGNORE_CASE)
    )

    // Full language names and unambiguous 3-letter release codes used for audio tracks.
    internal val AUDIO_LANGUAGE_MAP = mapOf(
        "english" to "English", "eng" to "English",
        "hindi" to "Hindi", "hin" to "Hindi",
        "tamil" to "Tamil", "tam" to "Tamil",
        "telugu" to "Telugu", "tel" to "Telugu",
        "malayalam" to "Malayalam", "mal" to "Malayalam",
        "kannada" to "Kannada", "kan" to "Kannada",
        "marathi" to "Marathi", "mar" to "Marathi",
        "bengali" to "Bengali", "ben" to "Bengali", "bangla" to "Bengali",
        "punjabi" to "Punjabi", "pun" to "Punjabi", "pan" to "Punjabi",
        "gujarati" to "Gujarati", "guj" to "Gujarati",
        "urdu" to "Urdu", "urd" to "Urdu",
        "spanish" to "Spanish", "spa" to "Spanish", "esp" to "Spanish",
        "french" to "French", "fra" to "French", "fre" to "French",
        "german" to "German", "ger" to "German", "deu" to "German",
        "italian" to "Italian", "ita" to "Italian",
        "portuguese" to "Portuguese", "por" to "Portuguese",
        "russian" to "Russian", "rus" to "Russian",
        "japanese" to "Japanese", "jpn" to "Japanese", "jap" to "Japanese",
        "korean" to "Korean", "kor" to "Korean",
        "chinese" to "Chinese", "chi" to "Chinese", "zho" to "Chinese",
        "mandarin" to "Mandarin",
        "cantonese" to "Cantonese",
        "arabic" to "Arabic", "ara" to "Arabic",
        "turkish" to "Turkish", "tur" to "Turkish",
        "indonesian" to "Indonesian", "ind" to "Indonesian",
        "thai" to "Thai", "tha" to "Thai",
        "vietnamese" to "Vietnamese", "vie" to "Vietnamese",
        "dutch" to "Dutch", "dut" to "Dutch", "nld" to "Dutch",
        "polish" to "Polish", "pol" to "Polish",
        "swedish" to "Swedish", "swe" to "Swedish",
        "norwegian" to "Norwegian", "nor" to "Norwegian",
        "danish" to "Danish", "dan" to "Danish",
        "finnish" to "Finnish", "fin" to "Finnish",
        "czech" to "Czech", "cze" to "Czech", "ces" to "Czech",
        "hungarian" to "Hungarian", "hun" to "Hungarian",
        "greek" to "Greek", "gre" to "Greek", "ell" to "Greek",
        "hebrew" to "Hebrew", "heb" to "Hebrew",
        "persian" to "Persian", "per" to "Persian", "fas" to "Persian",
        "ukrainian" to "Ukrainian", "ukr" to "Ukrainian",
        "romanian" to "Romanian", "rum" to "Romanian", "ron" to "Romanian"
    )

    private val SUBTITLE_QUALIFIERS = setOf(
        "sub", "subs", "subtitle", "subtitles", "softsub", "softsubs", "hardsub", "hardsubs", "cc"
    )

    private val DOMAIN_TLD_REGEX = Regex(
        "\\.(?:com|net|org|in|tv|co|uk|us|biz|info|durban|site|me|ru|fi|mx|cc|io|to|ws|la)\\b",
        RegexOption.IGNORE_CASE
    )

    private val TECH_TAIL_ANCHOR_REGEX = Regex(
        "\\b(?:2160p|4k|1080p|720p|480p|1080i|576p|hevc|x265|x264|h\\.?265|h\\.?264|av1|10bit|10-bit|8bit|web-?dl|webrip|bluray|blu-?ray|brrip|bdrip|hdrip|dvdrip|dvdscr|remux|hdtv|aac|ac3|dts|ddp|eac3|truehd|atmos|flac|hdr|hdr10|hdr10plus|dovi|dual|multi)\\b",
        RegexOption.IGNORE_CASE
    )

    fun extract(nameWithoutExt: String): MediaTags {
        val searchText = nameWithoutExt.replace("_", " ")

        val resolution = firstMatch(searchText, RESOLUTION_PATTERNS)
        val codec = firstMatch(searchText, CODEC_PATTERNS)
        val audio = extractAudio(searchText)
        val audioLanguages = extractAudioLanguages(nameWithoutExt)
        val source = firstMatch(searchText, SOURCE_PATTERNS)
        val hdr = firstMatch(searchText, HDR_PATTERNS)
        val edition = firstMatch(searchText, EDITION_PATTERNS)

        return MediaTags(
            resolution = resolution,
            codec = codec,
            audio = audio,
            audioLanguages = audioLanguages,
            source = source,
            hdr = hdr,
            edition = edition
        )
    }

    fun extractAudioLanguages(nameWithoutExt: String): String {
        val tail = isolateMetadataTail(nameWithoutExt)
        if (tail.isBlank()) return ""

        // Strip website TLDs (e.g. MoviesMod.fi, 1TamilMV.org) before tokenizing so TLDs are ignored.
        val cleanedTail = DOMAIN_TLD_REGEX.replace(tail, " ")
        val tokens = cleanedTail
            .split(Regex("[.\\s_\\-+/,()\\[\\]{}]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return ""

        val detected = linkedSetOf<String>()
        for (i in tokens.indices) {
            val tokenLower = tokens[i].lowercase()
            val canonical = AUDIO_LANGUAGE_MAP[tokenLower] ?: continue

            val prevLower = if (i > 0) tokens[i - 1].lowercase() else ""
            val nextLower = if (i + 1 < tokens.size) tokens[i + 1].lowercase() else ""
            val afterNextLower = if (i + 2 < tokens.size) tokens[i + 2].lowercase() else ""
            val precededBySub = prevLower in SUBTITLE_QUALIFIERS
            val followedBySub = nextLower in SUBTITLE_QUALIFIERS && afterNextLower !in AUDIO_LANGUAGE_MAP
            if (precededBySub || followedBySub) {
                continue
            }

            detected.add(canonical)
        }
        return detected.joinToString(" ")
    }

    private fun isolateMetadataTail(nameWithoutExt: String): String {
        var text = nameWithoutExt
            .replace(Regex("^\\[.*?\\]\\s*-?\\s*"), "")
            .replace(
                Regex(
                    "^(?:www[\\s\\.].*?|[a-zA-Z0-9-]+\\.(?:com|net|org|in|tv|co|uk|us|biz|info|durban|site|me|ru|fi))[\\s-]*-\\s*",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("^@[a-zA-Z0-9_]+\\s*-\\s*"), "")

        val epMatcher = MediaScanner.EPISODE_PATTERN.matcher(text)
        if (epMatcher.find()) {
            val afterEp = text.substring(epMatcher.end())
            val techMatch = TECH_TAIL_ANCHOR_REGEX.find(afterEp)
            if (techMatch != null) {
                val beforeTech = afterEp.substring(0, techMatch.range.first)
                val beforeTokens = beforeTech.split(Regex("[.\\s_\\-+/,()\\[\\]{}]+"))
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                val onlyLangPrefix = beforeTokens.isNotEmpty() && beforeTokens.all {
                    AUDIO_LANGUAGE_MAP.containsKey(it) || it in setOf("dual", "multi", "audio")
                }
                return if (onlyLangPrefix) afterEp else afterEp.substring(techMatch.range.first)
            }
            val afterTokens = afterEp.split(Regex("[.\\s_\\-+/,()\\[\\]{}]+"))
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            val onlyLanguages = afterTokens.isNotEmpty() && afterTokens.all {
                AUDIO_LANGUAGE_MAP.containsKey(it) || it in SUBTITLE_QUALIFIERS || it in setOf("esub", "esubs", "msub", "msubs")
            }
            return if (onlyLanguages) afterEp else ""
        }

        val yearMatcher = MediaScanner.MOVIE_YEAR_REGEX.matcher(text.replace(".", " ").replace("_", " "))
        var lastYearEnd = -1
        while (yearMatcher.find()) {
            lastYearEnd = yearMatcher.end()
        }
        if (lastYearEnd >= 0 && lastYearEnd <= text.length) {
            return text.substring(lastYearEnd)
        }

        return text
    }

    private fun extractAudio(searchText: String): String {
        val codec = firstMatch(searchText, AUDIO_CODEC_PATTERNS)
        val hasAtmos = ATMOS_PATTERN.containsMatchIn(searchText)
        val channels = firstMatch(searchText, AUDIO_CHANNEL_PATTERNS)

        val parts = mutableListOf<String>()
        if (codec.isNotEmpty()) {
            parts.add(codec)
        }
        if (channels.isNotEmpty() && !codec.contains(channels)) {
            parts.add(channels)
        }
        if (hasAtmos) {
            parts.add("Atmos")
        }
        return parts.joinToString(" ")
    }

    private fun firstMatch(text: String, patterns: List<Pair<String, Regex>>): String {
        for ((label, regex) in patterns) {
            if (regex.containsMatchIn(text)) {
                return label
            }
        }
        return ""
    }
}

object NamingTemplateFormatter {

    const val DEFAULT_MOVIE_TEMPLATE = "{title} ({year})"
    const val DEFAULT_TV_TEMPLATE = "{show} {tag} - {episode_title}"

    val MOVIE_PLACEHOLDERS = listOf(
        "{title}", "{year}", "{resolution}", "{codec}", "{audio}", "{audio_languages}", "{source}", "{hdr}", "{edition}"
    )

    val TV_PLACEHOLDERS = listOf(
        "{show}", "{tag}", "{season}", "{episode}", "{episode_title}",
        "{resolution}", "{codec}", "{audio}", "{audio_languages}", "{source}", "{hdr}", "{edition}"
    )

    fun formatMovieFilename(
        template: String,
        movieName: String,
        year: String,
        tags: MediaTags,
        ext: String
    ): String {
        val effectiveTemplate = template.ifBlank { DEFAULT_MOVIE_TEMPLATE }
        val values = mapOf(
            "{title}" to movieName,
            "{year}" to year,
            "{resolution}" to tags.resolution,
            "{codec}" to tags.codec,
            "{audio}" to tags.audio,
            "{audio_languages}" to tags.audioLanguages,
            "{audio_language}" to tags.audioLanguages,
            "{languages}" to tags.audioLanguages,
            "{language}" to tags.audioLanguages,
            "{source}" to tags.source,
            "{hdr}" to tags.hdr,
            "{edition}" to tags.edition
        )

        var stem = applyReplacements(effectiveTemplate, values)
        if (stem.isBlank()) {
            stem = applyReplacements(DEFAULT_MOVIE_TEMPLATE, values)
        }
        val safeStem = MediaScanner.sanitizeFilename(stem)
        return if (ext.isNotBlank()) "$safeStem.$ext" else safeStem
    }

    fun formatTvEpisodeFilename(
        template: String,
        showName: String,
        season: Int,
        episode: Int,
        episodeEnd: Int?,
        tag: String,
        episodeTitle: String?,
        tags: MediaTags,
        ext: String
    ): String {
        val effectiveTemplate = template.ifBlank { DEFAULT_TV_TEMPLATE }
        val seasonStr = String.format("%02d", season)
        val episodeStr = if (episodeEnd != null) {
            String.format("%02d-E%02d", episode, episodeEnd)
        } else {
            String.format("%02d", episode)
        }

        val values = mapOf(
            "{show}" to showName,
            "{tag}" to tag,
            "{sxe}" to tag,
            "{season}" to seasonStr,
            "{episode}" to episodeStr,
            "{episode_title}" to (episodeTitle ?: ""),
            "{resolution}" to tags.resolution,
            "{codec}" to tags.codec,
            "{audio}" to tags.audio,
            "{audio_languages}" to tags.audioLanguages,
            "{audio_language}" to tags.audioLanguages,
            "{languages}" to tags.audioLanguages,
            "{language}" to tags.audioLanguages,
            "{source}" to tags.source,
            "{hdr}" to tags.hdr,
            "{edition}" to tags.edition
        )

        var stem = applyReplacements(effectiveTemplate, values)
        if (stem.isBlank()) {
            stem = applyReplacements(DEFAULT_TV_TEMPLATE, values)
        }
        val safeStem = MediaScanner.sanitizeFilename(stem)
        return if (ext.isNotBlank()) "$safeStem.$ext" else safeStem
    }

    private fun applyReplacements(template: String, values: Map<String, String>): String {
        var result = template
        for ((token, value) in values) {
            result = result.replace(token, value, ignoreCase = true)
        }
        return cleanupFormattedStem(result)
    }

    internal fun cleanupFormattedStem(raw: String): String {
        var cleaned = raw
        repeat(2) {
            cleaned = cleaned.replace(Regex("\\[\\s*\\]|\\(\\s*\\)|\\{\\s*\\}"), "")
        }
        cleaned = cleaned.replace(Regex("\\.{2,}"), ".")
        cleaned = cleaned.replace(Regex("\\s*-\\s*(?:-\\s*)+"), " - ")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        cleaned = cleaned.replace(Regex("(?:\\s*-\\s*)+$"), "")
        cleaned = cleaned.replace(Regex("^(?:\\s*-\\s*)+"), "")
        cleaned = cleaned.trimEnd('.')
        return cleaned.trim()
    }
}
