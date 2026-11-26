package com.mediaorganizer.app

import android.content.Context

data class AppConfig(
    val basePath: String = DEFAULT_BASE_PATH,
    val movieDir: String = DEFAULT_MOVIE_DIR,
    val tvDir: String = DEFAULT_TV_DIR,
    val wikiFirst: Boolean = false,
    val movieLookupOnline: Boolean = false,
    val junkPaths: List<String> = DEFAULT_JUNK_PATHS,
    val excludedPaths: List<String> = DEFAULT_EXCLUDED_PATHS,
    val movieTemplate: String = NamingTemplateFormatter.DEFAULT_MOVIE_TEMPLATE,
    val tvTemplate: String = NamingTemplateFormatter.DEFAULT_TV_TEMPLATE,
    val watchdogEnabled: Boolean = false,
    val watchdogIntervalHours: Int = DEFAULT_WATCHDOG_INTERVAL_HOURS,
    val watchdogRequireCharging: Boolean = true,
    val watchdogRequireIdle: Boolean = false
) {
    companion object {
        const val DEFAULT_BASE_PATH = "/storage/emulated/0/Download"
        const val DEFAULT_MOVIE_DIR = "/storage/emulated/0/Entertainment/Movies"
        const val DEFAULT_TV_DIR = "/storage/emulated/0/Entertainment"
        const val DEFAULT_WATCHDOG_INTERVAL_HOURS = 6

        val DEFAULT_JUNK_PATHS = listOf(
            "/storage/emulated/0/.FileManagerRecycler",
            "/storage/emulated/0/Download/.com_oplus_rom",
            "/storage/emulated/0/Download/.content_portal",
            "/storage/emulated/0/Download/com_account_usercenter_new",
            "/storage/emulated/0/Movies/.thumbnails",
            "/storage/emulated/0/Music/.thumbnails",
            "/storage/emulated/0/oua_classifier",
            "/storage/emulated/0/Pictures/.thumbnails"
        )

        val DEFAULT_EXCLUDED_PATHS = listOf(
            "/storage/emulated/0/Android",
            "/storage/emulated/0/DCIM"
        )
    }
}

object AppPreferences {
    private const val PREFS_NAME = "MediaOrganizerPrefs"
    private const val CURRENT_PREFS_VERSION = 2

    fun loadConfig(context: Context): AppConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val version = prefs.getInt("prefs_version", 1)
        val junkPaths: List<String>
        val excludedPaths: List<String>

        if (version < CURRENT_PREFS_VERSION) {
            junkPaths = AppConfig.DEFAULT_JUNK_PATHS
            excludedPaths = AppConfig.DEFAULT_EXCLUDED_PATHS
            prefs.edit()
                .putStringSet("junkPaths", junkPaths.toSet())
                .putStringSet("excludedPaths", excludedPaths.toSet())
                .putInt("prefs_version", CURRENT_PREFS_VERSION)
                .apply()
        } else {
            val savedJunk = prefs.getStringSet("junkPaths", null)
            junkPaths = if (savedJunk != null) savedJunk.toList().sorted() else AppConfig.DEFAULT_JUNK_PATHS

            val savedExcluded = prefs.getStringSet("excludedPaths", null)
            excludedPaths = if (savedExcluded != null) savedExcluded.toList().sorted() else AppConfig.DEFAULT_EXCLUDED_PATHS
        }

        return AppConfig(
            basePath = prefs.getString("basePath", AppConfig.DEFAULT_BASE_PATH) ?: AppConfig.DEFAULT_BASE_PATH,
            movieDir = prefs.getString("movieDir", AppConfig.DEFAULT_MOVIE_DIR) ?: AppConfig.DEFAULT_MOVIE_DIR,
            tvDir = prefs.getString("tvDir", AppConfig.DEFAULT_TV_DIR) ?: AppConfig.DEFAULT_TV_DIR,
            wikiFirst = prefs.getBoolean("wikiFirst", false),
            movieLookupOnline = prefs.getBoolean("movieLookupOnline", false),
            junkPaths = junkPaths,
            excludedPaths = excludedPaths,
            movieTemplate = prefs.getString("movieTemplate", NamingTemplateFormatter.DEFAULT_MOVIE_TEMPLATE)
                ?.takeIf { it.isNotBlank() } ?: NamingTemplateFormatter.DEFAULT_MOVIE_TEMPLATE,
            tvTemplate = prefs.getString("tvTemplate", NamingTemplateFormatter.DEFAULT_TV_TEMPLATE)
                ?.takeIf { it.isNotBlank() } ?: NamingTemplateFormatter.DEFAULT_TV_TEMPLATE,
            watchdogEnabled = prefs.getBoolean("watchdogEnabled", false),
            watchdogIntervalHours = prefs.getInt("watchdogIntervalHours", AppConfig.DEFAULT_WATCHDOG_INTERVAL_HOURS).coerceAtLeast(0),
            watchdogRequireCharging = prefs.getBoolean("watchdogRequireCharging", true),
            watchdogRequireIdle = prefs.getBoolean("watchdogRequireIdle", false)
        )
    }

    fun saveConfig(context: Context, config: AppConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("basePath", config.basePath)
            .putString("movieDir", config.movieDir)
            .putString("tvDir", config.tvDir)
            .putBoolean("wikiFirst", config.wikiFirst)
            .putBoolean("movieLookupOnline", config.movieLookupOnline)
            .putStringSet("junkPaths", config.junkPaths.toSet())
            .putStringSet("excludedPaths", config.excludedPaths.toSet())
            .putString("movieTemplate", config.movieTemplate)
            .putString("tvTemplate", config.tvTemplate)
            .putBoolean("watchdogEnabled", config.watchdogEnabled)
            .putInt("watchdogIntervalHours", config.watchdogIntervalHours)
            .putBoolean("watchdogRequireCharging", config.watchdogRequireCharging)
            .putBoolean("watchdogRequireIdle", config.watchdogRequireIdle)
            .putInt("prefs_version", CURRENT_PREFS_VERSION)
            .apply()
    }

    fun saveJunkPaths(context: Context, paths: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("junkPaths", paths.toSet())
            .apply()
    }

    fun saveExcludedPaths(context: Context, paths: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("excludedPaths", paths.toSet())
            .apply()
    }

    fun getLastScanSubtitle(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("lastTileScanSummary", "Tap to scan") ?: "Tap to scan"
    }

    fun setLastScanSubtitle(context: Context, summary: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("lastTileScanSummary", summary)
            .apply()
    }
}
