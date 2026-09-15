package com.mediaorganizer.app

import java.io.File
import java.util.regex.Pattern

object SubtitleHelper {
    private val LANGUAGE_CODES = setOf(
        "en", "eng", "english",
        "es", "spa", "spanish",
        "fr", "fra", "fre", "french",
        "de", "deu", "ger", "german",
        "it", "ita", "italian",
        "pt", "por", "portuguese",
        "br", "ptbr",
        "ru", "rus", "russian",
        "ja", "jpn", "japanese",
        "ko", "kor", "korean",
        "zh", "zho", "chi", "chinese",
        "hi", "hin", "hindi",
        "ta", "tam", "tamil",
        "te", "tel", "telugu",
        "ml", "mal", "malayalam",
        "kn", "kan", "kannada",
        "ar", "ara", "arabic",
        "tr", "tur", "turkish",
        "nl", "nld", "dut", "dutch",
        "pl", "pol", "polish",
        "sv", "swe", "swedish",
        "no", "nor", "norwegian",
        "da", "dan", "danish",
        "fi", "fin", "finnish",
        "cs", "ces", "cze", "czech",
        "hu", "hun", "hungarian",
        "el", "ell", "gre", "greek",
        "he", "heb", "iv", "hebrew",
        "th", "tha", "thai",
        "vi", "vie", "vietnamese",
        "id", "ind", "indonesian",
        "ro", "ron", "rum", "romanian",
        "uk", "ukr", "ukrainian",
        "fa", "fas", "per", "persian"
    )

    private val LANGUAGE_CANONICAL = mapOf(
        "eng" to "en", "english" to "en",
        "spa" to "es", "spanish" to "es",
        "fra" to "fr", "fre" to "fr", "french" to "fr",
        "deu" to "de", "ger" to "de", "german" to "de",
        "ita" to "it", "italian" to "it",
        "por" to "pt", "portuguese" to "pt",
        "ptbr" to "pt", "br" to "pt",
        "rus" to "ru", "russian" to "ru",
        "jpn" to "ja", "japanese" to "ja",
        "kor" to "ko", "korean" to "ko",
        "zho" to "zh", "chi" to "zh", "chinese" to "zh",
        "hin" to "hi", "hindi" to "hi",
        "tam" to "ta", "tamil" to "ta",
        "tel" to "te", "telugu" to "te",
        "mal" to "ml", "malayalam" to "ml",
        "kan" to "kn", "kannada" to "kn",
        "ara" to "ar", "arabic" to "ar",
        "tur" to "tr", "turkish" to "tr",
        "nld" to "nl", "dut" to "nl", "dutch" to "nl",
        "pol" to "pl", "polish" to "pl",
        "swe" to "sv", "swedish" to "sv",
        "nor" to "no", "norwegian" to "no",
        "dan" to "da", "danish" to "da",
        "fin" to "fi", "finnish" to "fi",
        "ces" to "cs", "cze" to "cs", "czech" to "cs",
        "hun" to "hu", "hungarian" to "hu",
        "ell" to "el", "gre" to "el", "greek" to "el",
        "heb" to "he", "iv" to "he",
        "tha" to "th", "thai" to "th",
        "vie" to "vi", "vietnamese" to "vi",
        "ind" to "id", "indonesian" to "id",
        "ron" to "ro", "rum" to "ro", "romanian" to "ro",
        "ukr" to "uk", "ukrainian" to "uk",
        "fas" to "fa", "per" to "fa", "persian" to "fa"
    )

    private val EPISODE_PATTERN = Pattern.compile("(S(\\d{1,2})E)(\\d{1,2})(?:(?:-?E|-)\\s*(\\d{1,2}))?", Pattern.CASE_INSENSITIVE)
    private val EPISODE_ALT_PATTERN = Pattern.compile("\\b(?:s|season\\s*)?(\\d{1,2})[xX](\\d{1,2})\\b", Pattern.CASE_INSENSITIVE)

    fun extractSubtitleLanguage(filename: String): String? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        val tokens = nameWithoutExt.lowercase().split(Regex("[._\\-\\s]+")).filter { it.isNotEmpty() }
        
        val lastTokens = tokens.takeLast(3).reversed()
        for (token in lastTokens) {
            if (token in LANGUAGE_CODES) {
                return LANGUAGE_CANONICAL[token] ?: token.take(2)
            }
        }
        return null
    }

    private fun subtitleStemVariants(filename: String): Set<String> {
        val stem = filename.substringBeforeLast(".").lowercase()
        val variants = mutableSetOf(stem)
        var current = stem
        
        // Remove trailing numeric components
        for (i in 0 until 3) {
            val reduced = current.replace(Regex("[._\\-\\s]+\\d+$"), "")
            if (reduced == current) break
            variants.add(reduced)
            current = reduced
        }

        val langsEscaped = LANGUAGE_CODES.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        val variantsCopy = variants.toList()
        for (value in variantsCopy) {
            val reduced = value.replace(Regex("[._\\-\\s]+(?:$langsEscaped)$", RegexOption.IGNORE_CASE), "")
            if (reduced != value) variants.add(reduced)
        }

        for (value in variantsCopy) {
            val reduced = value.replace(Regex("[._\\-\\s]+(?:$langsEscaped)[._\\-\\s]+\\d+$", RegexOption.IGNORE_CASE), "")
            if (reduced != value) variants.add(reduced)
        }

        return variants.filter { it.isNotEmpty() }.toSet()
    }

    private fun normalizedMediaStem(filename: String): String {
        var stem = filename.substringBeforeLast(".").lowercase()
        stem = stem.replace(Regex("[._\\-\\s]+"), " ")
        return stem.replace(Regex("\\s+"), " ").trim()
    }

    data class EpisodeSignature(val season: Int, val episode: Int, val episodeEnd: Int?)

    private fun episodeSignature(filename: String): EpisodeSignature? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        val m = EPISODE_PATTERN.matcher(nameWithoutExt)
        if (m.find()) {
            val s = m.group(2)?.toIntOrNull()
            val e = m.group(3)?.toIntOrNull()
            val eEnd = m.group(4)?.toIntOrNull()
            if (s != null && e != null) return EpisodeSignature(s, e, eEnd)
        }

        val m2 = EPISODE_ALT_PATTERN.matcher(nameWithoutExt)
        if (m2.find()) {
            val s = m2.group(1)?.toIntOrNull()
            val e = m2.group(2)?.toIntOrNull()
            if (s != null && e != null) return EpisodeSignature(s, e, null)
        }
        return null
    }

    private fun subtitleMatchScore(
        videoFile: File, subtitleFile: File,
        season: Int?, episode: Int?, episodeEnd: Int?
    ): Int {
        val videoName = videoFile.name
        val subName = subtitleFile.name

        val videoStem = videoName.substringBeforeLast(".").lowercase()
        val subVariants = subtitleStemVariants(subName)
        val vNorm = normalizedMediaStem(videoName)

        var score = 0
        if (subVariants.contains(videoStem)) {
            score = 1000
        } else {
            var sNorm = normalizedMediaStem(subName)
            val langsEscaped = LANGUAGE_CODES.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            sNorm = sNorm.replace(Regex("\\s+(?:$langsEscaped)$", RegexOption.IGNORE_CASE), "")
            sNorm = sNorm.replace(Regex("\\s+\\d+$"), "")

            if (sNorm == vNorm) {
                score = 900
            } else {
                score = 0
            }
        }

        val subEp = episodeSignature(subName)
        val hasVideoEp = season != null && episode != null

        if (hasVideoEp) {
            if (subEp != null) {
                if (subEp.season != season) return -1
                val eEnd = episodeEnd ?: episode!!
                if (!(subEp.episode in episode!!..eEnd || (subEp.episodeEnd != null && subEp.episodeEnd in episode..eEnd))) return -1
                score += 700
                if (episodeEnd != null && subEp.episodeEnd == episodeEnd) {
                    score += 100
                }
            } else {
                if (score < 900) return -1
            }
        }

        val videoDir = videoFile.parentFile?.absolutePath ?: ""
        val subDir = subtitleFile.parentFile?.absolutePath ?: ""

        if (videoDir == subDir) {
            score += 300
        } else {
            try {
                val relPath = subtitleFile.parentFile?.relativeTo(videoFile.parentFile!!)?.path ?: ""
                if (relPath != "" && relPath != "." && !relPath.startsWith("..")) {
                    val parts = relPath.split(File.separator)
                    val depth = parts.size
                    if (depth == 1 && parts[0].lowercase() in setOf("subs", "subtitles", "subtitle")) {
                        score += 260
                    } else {
                        score += maxOf(0, 180 - depth * 40)
                    }
                }
            } catch (e: Exception) {}
        }

        return score
    }

    private fun pathEpisodeSignature(path: File): EpisodeSignature? {
        var current: File? = path
        while (current != null) {
            val sig = episodeSignature(current.name)
            if (sig != null) return sig
            current = current.parentFile
        }
        return null
    }

    private fun dedicatedFolderForVideo(videoFile: File): Boolean {
        val dir = videoFile.parentFile ?: return false
        val videos = dir.listFiles { f -> MediaScanner.isVideoFile(f) } ?: return false
        return videos.size == 1
    }

    fun findCompanionSubtitlesAdv(
        videoFile: File,
        excludedPaths: List<String>,
        season: Int? = null,
        episode: Int? = null,
        episodeEnd: Int? = null
    ): List<File> {
        val videoDir = videoFile.parentFile ?: return emptyList()
        val candidates = mutableListOf<Triple<Int, String, File>>()

        fun walk(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (excludedPaths.any { f.absolutePath.startsWith(it) }) continue
                if (f.isDirectory) {
                    walk(f)
                } else if (MediaScanner.isSubtitleFile(f) && f.absolutePath != videoFile.absolutePath) {
                    var score = subtitleMatchScore(videoFile, f, season, episode, episodeEnd)
                    if (score < 0) continue

                    if (season != null && episode != null) {
                        val subEp = pathEpisodeSignature(f)
                        if (subEp != null) {
                            val eEnd = episodeEnd ?: episode
                            if (subEp.season != season || subEp.episode !in episode..eEnd) continue
                        }
                    }

                    if (score < 500) {
                        if (!dedicatedFolderForVideo(videoFile)) continue
                        val subEp = pathEpisodeSignature(f)
                        if (subEp != null && season != null && episode != null) {
                            if (subEp.season != season || subEp.episode != episode) continue
                        }
                        score = 200
                    }

                    val lang = extractSubtitleLanguage(f.name) ?: "zz"
                    candidates.add(Triple(score, lang, f))
                }
            }
        }
        walk(videoDir)
        
        return candidates.sortedWith(compareBy({ -it.first }, { it.second }, { it.third.name.lowercase() }))
            .map { it.third }
    }

    fun subtitleDestinationName(baseStem: String, subtitlePath: String, usedNames: Set<String>): String {
        val used = usedNames.map { it.lowercase() }.toSet()
        val ext = "." + subtitlePath.substringAfterLast(".", "")
        val lang = extractSubtitleLanguage(File(subtitlePath).name)

        if (lang != null) {
            var candidate = "$baseStem.$lang$ext"
            if (candidate.lowercase() !in used) return candidate

            var counter = 2
            while (true) {
                candidate = "$baseStem.$lang.$counter$ext"
                if (candidate.lowercase() !in used) return candidate
                counter++
            }
        }

        var candidate = "$baseStem$ext"
        if (candidate.lowercase() !in used) return candidate

        var counter = 2
        while (true) {
            candidate = "$baseStem.$counter$ext"
            if (candidate.lowercase() !in used) return candidate
            counter++
        }
    }
}
