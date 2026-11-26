package com.mediaorganizer.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object WikipediaApi {

    private val client = OkHttpClient.Builder().build()

    private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val WIKI_EPISODE_LIST_URL = "https://en.wikipedia.org/wiki/List_of_%s_episodes"
    private const val WIKI_SEASON_URL = "https://en.wikipedia.org/wiki/%s_(season_%d)"
    private const val WIKI_TV_SERIES_URL = "https://en.wikipedia.org/wiki/%s_(TV_series)"

    private val TRAILING_YEAR_REGEX = Pattern.compile("\\s+(19|20)\\d{2}$")
    private val SEASON_HEADING_REGEX = Pattern.compile("Season\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
    private val FOOTNOTE_SUFFIX_REGEX = Regex("\\[[a-zA-Z0-9]+\\]")

    // Cache: show_name -> {season_num -> {ep_num -> title}}
    private val wikiCache = mutableMapOf<String, Map<Int, Map<Int, String>>>()

    private fun wikiNameVariants(showName: String): List<String> {
        val base = showName.replace(" ", "_")
        val stripped = TRAILING_YEAR_REGEX.matcher(showName).replaceAll("").trim().replace(" ", "_")

        val variants = mutableSetOf<String>()
        for (name in listOf(base, stripped)) {
            if (name.isNotBlank()) {
                variants.add(name)
                if (!name.endsWith("!")) {
                    variants.add("$name!")
                }
            }
        }
        return variants.toList()
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return null
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractEpisodeNumber(text: String): Int? {
        val m = Pattern.compile("(\\d+)").matcher(text.trim())
        return if (m.find()) m.group(1)?.toIntOrNull() else null
    }

    private fun extractHeadingText(element: Element): String? {
        val tag = element.tagName().lowercase()
        if (tag == "h2" || tag == "h3") return element.text()
        if (tag == "div" && element.hasClass("mw-heading")) {
            return element.selectFirst("h2, h3")?.text()
        }
        return null
    }

    private fun detectEpisodeColumnIndex(table: Element): Int? {
        val headerRow = table.selectFirst("tr") ?: return null
        val headers = headerRow.select("th, td").map { it.text().trim().lowercase() }
        val targets = listOf("no. in season", "no.inseason", "airdate order", "no. overall", "no.overall", "no.")
        for (target in targets) {
            for ((idx, h) in headers.withIndex()) {
                if (h.contains(target) && !h.contains("prod")) {
                    return idx
                }
            }
        }
        return null
    }

    internal fun parseWikiEpisodeTables(doc: Document): Map<Int, Map<Int, String>> {
        val result = mutableMapOf<Int, MutableMap<Int, String>>()
        val tables = doc.select("table")

        for (table in tables) {
            val rows = table.select("tr.vevent, tr.module-episode-list-row")
            if (rows.isEmpty()) continue

            var seasonNum: Int? = null

            var prevElement = table.previousElementSibling()
            while (prevElement != null) {
                val headingText = extractHeadingText(prevElement)
                if (headingText != null) {
                    val matcher = SEASON_HEADING_REGEX.matcher(headingText)
                    if (matcher.find()) {
                        seasonNum = matcher.group(1)?.toIntOrNull()
                        break
                    }
                }
                prevElement = prevElement.previousElementSibling()
            }

            if (seasonNum == null) {
                val h1 = doc.selectFirst("h1")
                if (h1 != null) {
                    val m = Pattern.compile("season\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(h1.text())
                    if (m.find()) {
                        seasonNum = m.group(1)?.toIntOrNull()
                    }
                }
            }

            if (seasonNum == null) continue

            val epColIdx = detectEpisodeColumnIndex(table)
            val seasonEps = result.getOrPut(seasonNum) { mutableMapOf() }

            for (row in rows) {
                val summary = row.selectFirst("td.summary") ?: continue
                summary.select("sup.reference").remove()
                val title = FOOTNOTE_SUFFIX_REGEX.replace(summary.text(), "")
                    .trim('"', '\'', '“', '”', ' ')

                val cells = row.select("td, th")
                var epInSeason: Int? = null

                if (epColIdx != null && epColIdx < cells.size) {
                    epInSeason = extractEpisodeNumber(cells[epColIdx].text())
                }
                if (epInSeason == null) {
                    var summaryIdx = -1
                    for (i in 0 until cells.size) {
                        if (cells[i] == summary) {
                            summaryIdx = i
                            break
                        }
                    }
                    if (summaryIdx >= 1) {
                        epInSeason = extractEpisodeNumber(cells[summaryIdx - 1].text())
                    }
                }

                if (epInSeason != null && title.isNotBlank()) {
                    seasonEps[epInSeason] = title
                }
            }
        }
        return result
    }

    private fun fetchWikiSeasonData(show: String): Map<Int, Map<Int, String>> {
        val normalizedShow = show.lowercase().trim()
        if (wikiCache.containsKey(normalizedShow)) {
            return wikiCache[normalizedShow]!!
        }

        val variants = wikiNameVariants(show)
        var allSeasonData: Map<Int, Map<Int, String>>? = null

        for (variant in variants) {
            val urls = listOf(
                String.format(WIKI_EPISODE_LIST_URL, variant),
                String.format(WIKI_TV_SERIES_URL, variant)
            )

            for (url in urls) {
                val html = fetchHtml(url) ?: continue
                val doc = Jsoup.parse(html)
                val parsed = parseWikiEpisodeTables(doc)
                if (parsed.isNotEmpty()) {
                    allSeasonData = parsed
                    break
                }
            }
            if (allSeasonData != null) break
        }

        val finalData = allSeasonData ?: emptyMap()
        wikiCache[normalizedShow] = finalData
        return finalData
    }

    fun getSeasonEpisodesFromWikipedia(show: String, season: Int): Map<Int, String> {
        val normalizedShow = show.lowercase().trim()
        val allSeasonData = fetchWikiSeasonData(show).toMutableMap()

        if (!allSeasonData.containsKey(season)) {
            val variants = wikiNameVariants(show)
            for (variant in variants) {
                val url = String.format(WIKI_SEASON_URL, variant, season)
                val html = fetchHtml(url) ?: continue
                val doc = Jsoup.parse(html)
                val parsed = parseWikiEpisodeTables(doc)
                if (parsed.isNotEmpty()) {
                    allSeasonData.putAll(parsed)
                    wikiCache[normalizedShow] = allSeasonData
                    break
                }
            }
        }

        return allSeasonData[season] ?: emptyMap()
    }

    fun getTitlesFromWikipedia(show: String, season: Int, episodeNumbers: List<Int>): Map<Int, String> {
        val seasonData = getSeasonEpisodesFromWikipedia(show, season)
        if (seasonData.isEmpty()) return emptyMap()
        if (episodeNumbers.isEmpty()) return seasonData

        val result = mutableMapOf<Int, String>()
        for (num in episodeNumbers) {
            seasonData[num]?.let { result[num] = it }
        }
        return result
    }
}
