package com.mediaorganizer.app

import com.google.gson.GsonBuilder

data class ConfigBackupPayload(
    val backupVersion: Int = 1,
    val exportedAt: String = "",
    val basePath: String? = null,
    val movieDir: String? = null,
    val tvDir: String? = null,
    val excludedPaths: List<String>? = null,
    val junkPaths: List<String>? = null,
    val movieTemplate: String? = null,
    val tvTemplate: String? = null,
    val wikiFirst: Boolean? = null,
    val movieLookupOnline: Boolean? = null,
    val watchdogEnabled: Boolean? = null,
    val watchdogIntervalHours: Int? = null,
    val watchdogRequireCharging: Boolean? = null,
    val watchdogRequireIdle: Boolean? = null
)

object ConfigBackupManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun exportConfigToJson(config: AppConfig, nowMillis: Long = System.currentTimeMillis()): String {
        val payload = ConfigBackupPayload(
            backupVersion = 1,
            exportedAt = LogExporter.isoTimestamp(nowMillis),
            basePath = config.basePath,
            movieDir = config.movieDir,
            tvDir = config.tvDir,
            excludedPaths = config.excludedPaths,
            junkPaths = config.junkPaths,
            movieTemplate = config.movieTemplate,
            tvTemplate = config.tvTemplate,
            wikiFirst = config.wikiFirst,
            movieLookupOnline = config.movieLookupOnline,
            watchdogEnabled = config.watchdogEnabled,
            watchdogIntervalHours = config.watchdogIntervalHours,
            watchdogRequireCharging = config.watchdogRequireCharging,
            watchdogRequireIdle = config.watchdogRequireIdle
        )
        return gson.toJson(payload)
    }

    fun importConfigFromJson(json: String, current: AppConfig = AppConfig()): AppConfig {
        require(json.isNotBlank()) { "Backup file is empty" }
        val payload = gson.fromJson(json, ConfigBackupPayload::class.java)
            ?: throw IllegalArgumentException("Invalid JSON backup format")

        val hasAnyKnownField = listOf(
            payload.basePath,
            payload.movieDir,
            payload.tvDir,
            payload.excludedPaths,
            payload.junkPaths,
            payload.movieTemplate,
            payload.tvTemplate
        ).any { it != null }

        require(hasAnyKnownField) { "Backup JSON does not contain MediaOrganizer configuration fields" }

        return current.copy(
            basePath = payload.basePath?.takeIf { it.isNotBlank() } ?: current.basePath,
            movieDir = payload.movieDir?.takeIf { it.isNotBlank() } ?: current.movieDir,
            tvDir = payload.tvDir?.takeIf { it.isNotBlank() } ?: current.tvDir,
            excludedPaths = payload.excludedPaths?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.sorted()
                ?: current.excludedPaths,
            junkPaths = payload.junkPaths?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.sorted()
                ?: current.junkPaths,
            movieTemplate = payload.movieTemplate?.takeIf { it.isNotBlank() } ?: current.movieTemplate,
            tvTemplate = payload.tvTemplate?.takeIf { it.isNotBlank() } ?: current.tvTemplate,
            wikiFirst = payload.wikiFirst ?: current.wikiFirst,
            movieLookupOnline = payload.movieLookupOnline ?: current.movieLookupOnline,
            watchdogEnabled = payload.watchdogEnabled ?: current.watchdogEnabled,
            watchdogIntervalHours = payload.watchdogIntervalHours?.coerceAtLeast(0) ?: current.watchdogIntervalHours,
            watchdogRequireCharging = payload.watchdogRequireCharging ?: current.watchdogRequireCharging,
            watchdogRequireIdle = payload.watchdogRequireIdle ?: current.watchdogRequireIdle
        )
    }
}
