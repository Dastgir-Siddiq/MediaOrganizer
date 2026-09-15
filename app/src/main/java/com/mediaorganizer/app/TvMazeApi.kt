package com.mediaorganizer.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

object TvMazeApi {

    private val client = OkHttpClient.Builder().build()
    private val gson = Gson()

    private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val TVMAZE_SEARCH_URL = "https://api.tvmaze.com/singlesearch/shows?q=%s"
    private const val TVMAZE_MULTI_SEARCH_URL = "https://api.tvmaze.com/search/shows?q=%s"
    private const val TVMAZE_EPISODES_URL = "https://api.tvmaze.com/shows/%d/episodes"

    private val TRAILING_YEAR_REGEX = Pattern.compile("\\s+(19|20)\\d{2}$")

    // Cache: show_name -> show_id
    private val showIdCache = mutableMapOf<String, Int?>()
    // Cache: show_name -> {(season, number) -> title}
    private val tvmazeEpisodesCache = mutableMapOf<String, Map<Pair<Int, Int>, String>>()

    private fun <T> retryRequest(maxRetries: Int = 3, backoffMillis: Long = 1500, block: () -> T): T {
        var lastErr: Exception? = null
        for (attempt in 0 until maxRetries) {
            try {
                return block()
            } catch (e: IOException) {
                lastErr = e
            } catch (e: Exception) {
                lastErr = e
            }
            if (attempt < maxRetries - 1) {
                try {
                    Thread.sleep(backoffMillis * (attempt + 1))
                } catch (ignored: InterruptedException) {
                }
            }
        }
        throw lastErr ?: IOException("Request failed after $maxRetries retries")
    }

    private fun singlesearch(query: String): Int? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = String.format(TVMAZE_SEARCH_URL, encoded)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(bodyString, mapType)
            (map["id"] as? Double)?.toInt()
        }
    }

    private fun multisearch(query: String): Int? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = String.format(TVMAZE_MULTI_SEARCH_URL, encoded)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val results: List<Map<String, Any>> = gson.fromJson(bodyString, listType)
            if (results.isNotEmpty()) {
                val firstShow = results[0]["show"] as? Map<*, *>
                (firstShow?.get("id") as? Double)?.toInt()
            } else {
                null
            }
        }
    }

    private fun findShowId(show: String): Int? {
        val normalized = show.lowercase().trim()
        if (showIdCache.containsKey(normalized)) {
            return showIdCache[normalized]
        }

        // 1. Try exact name via singlesearch
        var id = retryRequest { singlesearch(show) }
        if (id != null) {
            showIdCache[normalized] = id
            return id
        }

        // 2. Strip trailing year and retry (e.g. "Sugar 2024" -> "Sugar")
        val stripped = TRAILING_YEAR_REGEX.matcher(show).replaceAll("").trim()
        if (stripped.isNotEmpty() && stripped != show) {
            id = retryRequest { singlesearch(stripped) }
            if (id != null) {
                showIdCache[normalized] = id
                return id
            }
        }

        // 3. Fall back to multi-result search
        id = retryRequest { multisearch(show) }
        if (id != null) {
            showIdCache[normalized] = id
            return id
        }

        // 4. Try multi-search with stripped name
        if (stripped.isNotEmpty() && stripped != show) {
            id = retryRequest { multisearch(stripped) }
            if (id != null) {
                showIdCache[normalized] = id
                return id
            }
        }

        showIdCache[normalized] = null
        return null
    }

    private fun fetchTvMazeEpisodes(show: String): Map<Pair<Int, Int>, String> {
        val normalized = show.lowercase().trim()
        if (tvmazeEpisodesCache.containsKey(normalized)) {
            return tvmazeEpisodesCache[normalized]!!
        }

        val showId = findShowId(show) ?: run {
            tvmazeEpisodesCache[normalized] = emptyMap()
            return emptyMap()
        }

        val episodesUrl = String.format(TVMAZE_EPISODES_URL, showId)
        val request = Request.Builder()
            .url(episodesUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val allEpisodes: List<Map<String, Any>> = retryRequest {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("TVMaze episodes fetch HTTP ${response.code}")
                val bodyString = response.body?.string() ?: throw IOException("Empty episodes body")
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                gson.fromJson(bodyString, listType)
            }
        }

        val epLookup = mutableMapOf<Pair<Int, Int>, String>()
        for (e in allEpisodes) {
            val s = (e["season"] as? Double)?.toInt()
            val n = (e["number"] as? Double)?.toInt()
            val name = e["name"] as? String
            if (s != null && n != null && !name.isNullOrBlank()) {
                epLookup[Pair(s, n)] = name
            }
        }

        tvmazeEpisodesCache[normalized] = epLookup
        return epLookup
    }

    fun getTitlesFromTvMaze(show: String, season: Int, episodeNumbers: List<Int>): Map<Int, String> {
        val epLookup = fetchTvMazeEpisodes(show)
        if (epLookup.isEmpty()) return emptyMap()

        // 1. Exact season match
        var result = mutableMapOf<Int, String>()
        for (epNum in episodeNumbers) {
            val key = Pair(season, epNum)
            if (epLookup.containsKey(key)) {
                result[epNum] = epLookup[key]!!
            }
        }

        if (result.size == episodeNumbers.size) {
            return result
        }

        // 2. Try adjacent seasons (±1, ±2) if missing episodes
        val offsets = listOf(1, -1, 2, -2)
        for (offset in offsets) {
            val altSeason = season + offset
            if (altSeason < 1) continue

            val altResult = mutableMapOf<Int, String>()
            for (epNum in episodeNumbers) {
                val key = Pair(altSeason, epNum)
                if (epLookup.containsKey(key)) {
                    altResult[epNum] = epLookup[key]!!
                }
            }

            if (altResult.size > result.size) {
                result = altResult
                if (result.size == episodeNumbers.size) {
                    return result
                }
            }
        }

        return result
    }
}
