package com.mediaorganizer.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mediaorganizer.app.data.CachedMovieEntity
import com.mediaorganizer.app.data.MetadataCacheDao
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MovieMetadataFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search?term=%s&media=movie&entity=movie&limit=5"

    interface MovieRemoteProvider {
        fun lookupMovieTitle(title: String, year: String): String?
    }

    private val defaultRemoteProvider = object : MovieRemoteProvider {
        override fun lookupMovieTitle(title: String, year: String): String? {
            return lookupViaItunes(title, year)
        }
    }

    suspend fun resolveMovieTitle(
        parsedTitle: String,
        year: String,
        onlineLookupEnabled: Boolean,
        cacheDao: MetadataCacheDao? = null,
        ttlMillis: Long = MediaTitleFetcher.DEFAULT_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
        remoteProvider: MovieRemoteProvider = defaultRemoteProvider,
        onLog: (String) -> Unit = {}
    ): String {
        val lookupKey = buildLookupKey(parsedTitle, year)
        val cached = cacheDao?.getMovie(lookupKey)

        if (cached != null && cached.expiresAt > nowMillis) {
            if (cached.resolvedTitle != parsedTitle) {
                onLog("📦 Movie cache hit: $parsedTitle → ${cached.resolvedTitle}")
            }
            return cached.resolvedTitle
        }

        if (!onlineLookupEnabled) {
            val resolved = cached?.resolvedTitle ?: parsedTitle
            if (cacheDao != null && cached == null) {
                cacheDao.upsertMovie(
                    CachedMovieEntity(
                        lookupKey = lookupKey,
                        queryTitle = parsedTitle,
                        resolvedTitle = resolved,
                        year = year,
                        source = "LOCAL",
                        fetchedAt = nowMillis,
                        expiresAt = nowMillis + ttlMillis
                    )
                )
            }
            return resolved
        }

        val remoteTitle = try {
            remoteProvider.lookupMovieTitle(parsedTitle, year)
        } catch (e: Exception) {
            null
        }

        if (remoteTitle != null) {
            val sanitized = MediaScanner.sanitizeFilename(remoteTitle)
            cacheDao?.upsertMovie(
                CachedMovieEntity(
                    lookupKey = lookupKey,
                    queryTitle = parsedTitle,
                    resolvedTitle = sanitized,
                    year = year,
                    source = "ITUNES",
                    fetchedAt = nowMillis,
                    expiresAt = nowMillis + ttlMillis
                )
            )
            if (sanitized != parsedTitle) {
                onLog("🎬 Movie metadata resolved: $parsedTitle → $sanitized ($year)")
            }
            return sanitized
        }

        if (cached != null) {
            onLog("📦 Offline fallback: using cached movie title for $parsedTitle ($year)")
            return cached.resolvedTitle
        }

        return parsedTitle
    }

    internal fun buildLookupKey(title: String, year: String): String {
        return "${normalizeForComparison(title)}|$year"
    }

    internal fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun lookupViaItunes(title: String, year: String): String? {
        val encoded = URLEncoder.encode("$title $year".trim(), "UTF-8")
        val request = Request.Builder()
            .url(String.format(ITUNES_SEARCH_URL, encoded))
            .header("User-Agent", "MediaOrganizer/1.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val parsed: Map<String, Any> = gson.fromJson(body, mapType) ?: return null
            val results = parsed["results"] as? List<*> ?: return null
            val targetNorm = normalizeForComparison(title)

            for (entry in results) {
                val item = entry as? Map<*, *> ?: continue
                val trackName = item["trackName"] as? String ?: continue
                val releaseDate = item["releaseDate"] as? String ?: ""
                val releaseYear = releaseDate.take(4)
                if (year.isNotEmpty() && releaseYear.isNotEmpty() && releaseYear != year) {
                    continue
                }
                if (normalizeForComparison(trackName) == targetNorm) {
                    return trackName
                }
            }
        }
        return null
    }
}
