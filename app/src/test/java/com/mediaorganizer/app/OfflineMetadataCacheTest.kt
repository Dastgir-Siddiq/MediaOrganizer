package com.mediaorganizer.app

import com.mediaorganizer.app.data.CachedMovieEntity
import com.mediaorganizer.app.data.CachedShowEpisodesEntity
import com.mediaorganizer.app.data.MetadataCacheDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class OfflineMetadataCacheTest {

    private class FakeMetadataCacheDao : MetadataCacheDao {
        private val shows = mutableMapOf<String, CachedShowEpisodesEntity>()
        private val movies = mutableMapOf<String, CachedMovieEntity>()

        override suspend fun getShowEpisodes(cacheKey: String): CachedShowEpisodesEntity? = shows[cacheKey]

        override suspend fun upsertShowEpisodes(entity: CachedShowEpisodesEntity) {
            shows[entity.cacheKey] = entity
        }

        override suspend fun getMovie(lookupKey: String): CachedMovieEntity? = movies[lookupKey]

        override suspend fun upsertMovie(entity: CachedMovieEntity) {
            movies[entity.lookupKey] = entity
        }

        override suspend fun getCachedShowCount(): Int = shows.size
        override suspend fun getCachedMovieCount(): Int = movies.size
        override suspend fun deleteExpiredShows(now: Long) {
            shows.entries.removeIf { it.value.expiresAt < now }
        }
        override suspend fun deleteExpiredMovies(now: Long) {
            movies.entries.removeIf { it.value.expiresAt < now }
        }
        override suspend fun clearAllShows() = shows.clear()
        override suspend fun clearAllMovies() = movies.clear()
    }

    @Test
    fun cachesEpisodeTitlesWithTtlAndFallsBackOfflineWhenExpired() = runBlocking {
        val dao = FakeMetadataCacheDao()
        var remoteCalls = 0
        var isOffline = false

        val provider = object : MediaTitleFetcher.EpisodeRemoteProvider {
            override fun fetchTvMazeSeason(showName: String, season: Int): Map<Int, String> {
                remoteCalls++
                if (isOffline) throw IOException("No network")
                return mapOf(1 to "Good News About Hell", 2 to "Half Loop")
            }

            override fun fetchWikipediaSeason(showName: String, season: Int): Map<Int, String> {
                if (isOffline) throw IOException("No network")
                return emptyMap()
            }
        }

        val t0 = 1_000_000L
        val ttl = 60_000L

        // 1. Initial online fetch populates cache
        val first = MediaTitleFetcher.getEpisodeTitles(
            showName = "Severance",
            season = 1,
            episodeNumbers = listOf(1, 2),
            cacheDao = dao,
            ttlMillis = ttl,
            nowMillis = t0,
            remoteProvider = provider
        )
        assertEquals(mapOf(1 to "Good News About Hell", 2 to "Half Loop"), first)
        assertEquals(1, remoteCalls)
        assertEquals(1, dao.getCachedShowCount())

        // 2. Re-scan within TTL serves from cache with zero network calls even offline
        isOffline = true
        val second = MediaTitleFetcher.getEpisodeTitles(
            showName = "Severance",
            season = 1,
            episodeNumbers = listOf(1, 2),
            cacheDao = dao,
            ttlMillis = ttl,
            nowMillis = t0 + 10_000L,
            remoteProvider = provider
        )
        assertEquals(first, second)
        assertEquals(1, remoteCalls)

        // 3. After TTL expires while still offline, falls back cleanly to stale cached titles
        val third = MediaTitleFetcher.getEpisodeTitles(
            showName = "Severance",
            season = 1,
            episodeNumbers = listOf(1, 2),
            cacheDao = dao,
            ttlMillis = ttl,
            nowMillis = t0 + ttl + 5_000L,
            remoteProvider = provider
        )
        assertEquals(first, third)
        assertEquals(2, remoteCalls)
    }

    @Test
    fun cachesMovieLookupsWithTtlAndWorksOffline() = runBlocking {
        val dao = FakeMetadataCacheDao()
        var lookups = 0
        var isOffline = false

        val movieProvider = object : MovieMetadataFetcher.MovieRemoteProvider {
            override fun lookupMovieTitle(title: String, year: String): String? {
                lookups++
                if (isOffline) throw IOException("Offline")
                return "Spider-Man"
            }
        }

        val t0 = 5_000_000L
        val ttl = 30_000L

        val first = MovieMetadataFetcher.resolveMovieTitle(
            parsedTitle = "Spider Man",
            year = "2002",
            onlineLookupEnabled = true,
            cacheDao = dao,
            ttlMillis = ttl,
            nowMillis = t0,
            remoteProvider = movieProvider
        )
        assertEquals("Spider-Man", first)
        assertEquals(1, lookups)

        // Fresh cache hit offline
        isOffline = true
        val second = MovieMetadataFetcher.resolveMovieTitle(
            parsedTitle = "Spider Man",
            year = "2002",
            onlineLookupEnabled = true,
            cacheDao = dao,
            ttlMillis = ttl,
            nowMillis = t0 + 10_000L,
            remoteProvider = movieProvider
        )
        assertEquals("Spider-Man", second)
        assertEquals(1, lookups)

        // Expired cache offline fallback
        val third = MovieMetadataFetcher.resolveMovieTitle(
            parsedTitle = "Spider Man",
            year = "2002",
            onlineLookupEnabled = true,
            cacheDao = dao,
            ttlMillis = ttl,
            nowMillis = t0 + ttl + 1_000L,
            remoteProvider = movieProvider
        )
        assertEquals("Spider-Man", third)
        assertEquals(2, lookups)
    }

    @Test
    fun defaultOfflineCacheTtlIsNinetyDays() {
        val ninetyDaysMillis = 90L * 24L * 60L * 60L * 1000L
        assertEquals(ninetyDaysMillis, MediaTitleFetcher.DEFAULT_TTL_MILLIS)
    }
}
