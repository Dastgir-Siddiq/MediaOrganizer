package com.mediaorganizer.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.regex.Pattern

object WikipediaApi {

    private val client = OkHttpClient.Builder().build()
    
    private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val WIKI_EPISODE_LIST_URL = "https://en.wikipedia.org/wiki/List_of_%s_episodes"
    private const val WIKI_SEASON_URL = "https://en.wikipedia.org/wiki/%s_(season_%d)"
    private const val WIKI_TV_SERIES_URL = "https://en.wikipedia.org/wiki/%s_(TV_series)"

    private val TRAILING_YEAR_REGEX = Pattern.compile("\\s+(19|20)\\d{2}$")
    private val SEASON_HEADING_REGEX = Pattern.compile("Season\\s+(\\d+)", Pattern.CASE_INSENSITIVE)

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
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEpisodeNumber(text: String): Int? {
        val m = Pattern.compile("(\\d+)").matcher(text.trim())
        return if (m.find()) m.group(1)?.toIntOrNull() else null
    }

    private fun parseWikiEpisodeTables(doc: Document): Map<Int, Map<Int, String>> {
        val result = mutableMapOf<Int, MutableMap<Int, String>>()
        val tables = doc.select("table")

        for (table in tables) {
            val rows = table.select("tr.vevent, tr.module-episode-list-row")
            if (rows.isEmpty()) continue

            // Find season heading for table
            var seasonNum: Int? = null
            
            // Traverse previous elements to find h2 or h3 heading
            var prevElement = table.previousElementSibling()
            while (prevElement != null) {
                if (prevElement.tagName().equals("h2", ignoreCase = true) || prevElement.tagName().equals("h3", ignoreCase = true)) {
                    val headingText = prevElement.text()
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

            val seasonEps = result.getOrPut(seasonNum) { mutableMapOf() }
            for (row in rows) {
                val summary = row.selectFirst("td.summary") ?: continue
                var title = summary.text().trim('"', '\'', '“', '”', ' ')

                val cells = row.select("td, th")
                var epInSeason: Int? = null

                var summaryIdx = -1
                for (i in 0 until cells.size) {
                    if (cells[i] == summary) {
                        summaryIdx = i
                        break
                    }
                }

                if (summaryIdx >= 1) {
                    val prevCell = cells[summaryIdx - 1]
                    epInSeason = extractEpisodeNumber(prevCell.text())
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

    fun getTitlesFromWikipedia(show: String, season: Int, episodeNumbers: List<Int>): Map<Int, String> {
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

        val seasonData = allSeasonData[season] ?: return emptyMap()
        val result = mutableMapOf<Int, String>()
        for (num in episodeNumbers) {
            if (seasonData.containsKey(num)) {
                result[num] = seasonData[num]!!
            }
        }
        return result
    }
}
