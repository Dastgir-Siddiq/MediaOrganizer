package com.mediaorganizer.app

import com.mediaorganizer.app.MediaScanner.MediaItem
import com.mediaorganizer.app.data.CachedShowEpisodesEntity
import com.mediaorganizer.app.data.MetadataCacheDao
import com.mediaorganizer.app.data.RoomJsonConverters

object MediaTitleFetcher {

    const val DEFAULT_TTL_MILLIS = 90L * 24L * 60L * 60L * 1000L // 90 days

    interface EpisodeRemoteProvider {
        fun fetchTvMazeSeason(showName: String, season: Int): Map<Int, String>
        fun fetchWikipediaSeason(showName: String, season: Int): Map<Int, String>
    }

    private val defaultRemoteProvider = object : EpisodeRemoteProvider {
        override fun fetchTvMazeSeason(showName: String, season: Int): Map<Int, String> {
            return TvMazeApi.getSeasonEpisodesFromTvMaze(showName, season)
        }

        override fun fetchWikipediaSeason(showName: String, season: Int): Map<Int, String> {
            return WikipediaApi.getSeasonEpisodesFromWikipedia(showName, season)
        }
    }

    suspend fun getSeasonEpisodeMap(
        showName: String,
        season: Int,
        episodeNumbers: List<Int> = emptyList(),
        wikiFirst: Boolean = false,
        cacheDao: MetadataCacheDao? = null,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
        remoteProvider: EpisodeRemoteProvider = defaultRemoteProvider,
        onLog: (String) -> Unit = {}
    ): Map<Int, String> {
        val normalizedShow = showName.lowercase().trim()
        val primaryKey = buildCacheKey(normalizedShow, season, wikiFirst)
        val alternateKey = buildCacheKey(normalizedShow, season, !wikiFirst)

        val cachedEntity = cacheDao?.getShowEpisodes(primaryKey) ?: cacheDao?.getShowEpisodes(alternateKey)
        val cachedMap = cachedEntity?.let { RoomJsonConverters.decodeEpisodesMap(it.episodesJson) } ?: emptyMap()

        val isCacheFresh = cachedEntity != null && cachedEntity.expiresAt > nowMillis
        val maxCachedEp = cachedMap.keys.maxOrNull() ?: 0
        val maxRequestedEp = episodeNumbers.maxOrNull() ?: 0
        val coversAllOrFullSeason = cachedMap.isNotEmpty() &&
            (episodeNumbers.isEmpty() || maxCachedEp >= maxRequestedEp)

        if (isCacheFresh && coversAllOrFullSeason) {
            onLog("📦 Cache hit for $showName Season $season (${cachedMap.size} episodes cached)")
            return cachedMap
        }

        val fetched = fetchSeasonFromRemote(
            showName = showName,
            season = season,
            wikiFirst = wikiFirst,
            remoteProvider = remoteProvider,
            onLog = onLog
        )

        val merged = mutableMapOf<Int, String>()
        merged.putAll(cachedMap)
        merged.putAll(fetched)

        if (fetched.isNotEmpty() && cacheDao != null) {
            val sourceLabel = if (wikiFirst) "WIKI_FIRST" else "TVMAZE_FIRST"
            cacheDao.upsertShowEpisodes(
                CachedShowEpisodesEntity(
                    cacheKey = primaryKey,
                    showName = showName,
                    season = season,
                    episodesJson = RoomJsonConverters.encodeEpisodesMap(merged),
                    source = sourceLabel,
                    fetchedAt = nowMillis,
                    expiresAt = nowMillis + ttlMillis
                )
            )
        } else if (!isCacheFresh && fetched.isEmpty() && cachedMap.isNotEmpty()) {
            onLog("📦 Offline fallback: using cached titles for $showName Season $season")
        }

        return merged
    }

    suspend fun getEpisodeTitles(
        showName: String,
        season: Int,
        episodeNumbers: List<Int>,
        wikiFirst: Boolean = false,
        cacheDao: MetadataCacheDao? = null,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
        remoteProvider: EpisodeRemoteProvider = defaultRemoteProvider,
        onLog: (String) -> Unit = {}
    ): Map<Int, String> {
        val seasonMap = getSeasonEpisodeMap(
            showName = showName,
            season = season,
            episodeNumbers = episodeNumbers,
            wikiFirst = wikiFirst,
            cacheDao = cacheDao,
            ttlMillis = ttlMillis,
            nowMillis = nowMillis,
            remoteProvider = remoteProvider,
            onLog = onLog
        )
        if (episodeNumbers.isEmpty()) return seasonMap
        val filtered = mutableMapOf<Int, String>()
        for (epNum in episodeNumbers) {
            seasonMap[epNum]?.let { filtered[epNum] = it }
        }
        return filtered
    }

    /**
     * Aligns each scanned [MediaItem.TvEpisode] against the fetched [seasonMap].
     * Handles shows where episode numbering differs from TVMaze/Wikipedia (e.g. pulled/missing
     * episodes shifting S03E01..E10 by +1) by matching filename title hints against [seasonMap].
     */
    fun resolveEpisodeTitlesForSeason(
        itemsInSeason: List<MediaItem.TvEpisode>,
        seasonMap: Map<Int, String>,
        onLog: (String) -> Unit = {}
    ): Map<MediaItem.TvEpisode, String> {
        if (itemsInSeason.isEmpty()) return emptyMap()

        data class MatchInfo(
            val item: MediaItem.TvEpisode,
            val bestEp: Int?,
            val bestScore: Double,
            val directScore: Double
        )

        val matchInfos = itemsInSeason.map { item ->
            if (item.titleHint.isBlank() || item.episodeEnd != null || seasonMap.isEmpty()) {
                MatchInfo(item, null, 0.0, 0.0)
            } else {
                val directTitle = seasonMap[item.episode]
                val directScore = if (directTitle != null) titleSimilarity(item.titleHint, directTitle) else 0.0

                var bestEp: Int? = null
                var bestScore = 0.0
                for ((epNum, title) in seasonMap) {
                    val s = titleSimilarity(item.titleHint, title)
                    if (s > bestScore) {
                        bestScore = s
                        bestEp = epNum
                    }
                }
                MatchInfo(item, bestEp, bestScore, directScore)
            }
        }

        // Detect uniform numbering shift (e.g. +1 when episode 1 was omitted from release)
        val offsetVotes = mutableMapOf<Int, Int>()
        var zeroVotes = 0
        for (info in matchInfos) {
            if (info.bestEp != null && info.bestScore >= 0.78) {
                val offset = info.bestEp - info.item.episode
                if (offset == 0) {
                    zeroVotes++
                } else {
                    offsetVotes[offset] = (offsetVotes[offset] ?: 0) + 1
                }
            }
        }

        val bestNonZero = offsetVotes.maxByOrNull { it.value }
        val chosenOffset = if (bestNonZero != null &&
            bestNonZero.value > zeroVotes &&
            (bestNonZero.value >= 2 || (zeroVotes == 0 && matchInfos.any { it.bestScore >= 0.85 }))
        ) {
            onLog("🧭 Detected episode numbering offset ${if (bestNonZero.key > 0) "+${bestNonZero.key}" else "${bestNonZero.key}"} for ${itemsInSeason.first().showName} Season ${itemsInSeason.first().season}")
            bestNonZero.key
        } else {
            0
        }

        val resolved = LinkedHashMap<MediaItem.TvEpisode, String>()
        for (info in matchInfos) {
            val item = info.item
            if (item.episodeEnd != null) {
                val epTitles = (item.episode..item.episodeEnd).mapNotNull {
                    seasonMap[it + chosenOffset] ?: seasonMap[it]
                }
                if (epTitles.isNotEmpty()) {
                    resolved[item] = epTitles.joinToString(" & ")
                }
                continue
            }

            if (item.titleHint.isNotBlank()) {
                if (chosenOffset == 0 && info.directScore >= 0.78 && seasonMap.containsKey(item.episode)) {
                    resolved[item] = seasonMap[item.episode]!!
                } else if (info.bestEp != null && info.bestScore >= 0.78 && seasonMap.containsKey(info.bestEp)) {
                    resolved[item] = seasonMap[info.bestEp]!!
                } else if (chosenOffset != 0 && seasonMap.containsKey(item.episode + chosenOffset)) {
                    resolved[item] = seasonMap[item.episode + chosenOffset]!!
                } else if (seasonMap.containsKey(item.episode) && info.directScore >= 0.30) {
                    resolved[item] = seasonMap[item.episode]!!
                } else {
                    // Filename already carries a clear distinct title that doesn't match the remote numbering:
                    // preserve the filename's title rather than assigning a mismatched episode title.
                    resolved[item] = item.titleHint
                }
            } else {
                val targetEp = item.episode + chosenOffset
                val title = seasonMap[targetEp] ?: seasonMap[item.episode]
                if (!title.isNullOrBlank()) {
                    resolved[item] = title
                }
            }
        }
        return resolved
    }

    internal fun normalizeTitleForMatch(text: String): String {
        return text.lowercase()
            .replace("&", " and ")
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun titleSimilarity(a: String, b: String): Double {
        val na = normalizeTitleForMatch(a)
        val nb = normalizeTitleForMatch(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0

        val compactA = na.replace(" ", "")
        val compactB = nb.replace(" ", "")
        if (compactA == compactB) return 0.98

        val tokensA = na.split(" ").filter { it.isNotEmpty() }.toSet()
        val tokensB = nb.split(" ").filter { it.isNotEmpty() }.toSet()
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    internal fun buildCacheKey(normalizedShow: String, season: Int, wikiFirst: Boolean): String {
        val mode = if (wikiFirst) "wiki" else "tvmaze"
        return "$normalizedShow|$season|$mode|v2"
    }

    private fun fetchSeasonFromRemote(
        showName: String,
        season: Int,
        wikiFirst: Boolean,
        remoteProvider: EpisodeRemoteProvider,
        onLog: (String) -> Unit
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()

        if (wikiFirst) {
            try {
                onLog("Fetching titles from Wikipedia for $showName Season $season...")
                result.putAll(remoteProvider.fetchWikipediaSeason(showName, season))
            } catch (e: Exception) {
                onLog("⚠️ Wikipedia error for $showName: ${e.message}")
            }

            if (result.isNotEmpty()) return result

            try {
                onLog("Fetching missing titles from TVMaze for $showName Season $season...")
                result.putAll(remoteProvider.fetchTvMazeSeason(showName, season))
            } catch (e: Exception) {
                onLog("⚠️ TVMaze error for $showName: ${e.message}")
            }
        } else {
            try {
                result.putAll(remoteProvider.fetchTvMazeSeason(showName, season))
            } catch (e: Exception) {
                onLog("⚠️ TVMaze error for $showName: ${e.message}")
            }

            if (result.isNotEmpty()) return result

            try {
                onLog("Fetching missing titles from Wikipedia for $showName Season $season...")
                result.putAll(remoteProvider.fetchWikipediaSeason(showName, season))
            } catch (e: Exception) {
                onLog("⚠️ Wikipedia error for $showName: ${e.message}")
            }
        }

        return result
    }
}
