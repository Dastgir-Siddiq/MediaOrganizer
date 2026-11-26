package com.mediaorganizer.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExporterAndConfigBackupTest {

    @Test
    fun exportsLogsAsTextAndJson() {
        val config = AppConfig(
            basePath = "/storage/emulated/0/Download",
            movieDir = "/storage/emulated/0/Movies",
            tvDir = "/storage/emulated/0/TV",
            movieTemplate = "{title} ({year}) [{resolution}]",
            tvTemplate = "{show} {tag} - {episode_title}"
        )
        val logs = listOf(
            "[07:10:00] 🎬 MEDIA ORGANIZER SCAN STARTED",
            "[07:10:01] ✅ Moved: Inception.2010.1080p.mkv → Inception (2010) [1080p].mkv"
        )

        val txt = LogExporter.formatAsText(logs, config, device = "OnePlus 12", sdkInt = 34, nowMillis = 1_700_000_000_000L)
        assertTrue(txt.contains("MediaOrganizer Diagnostic Log"))
        assertTrue(txt.contains("OnePlus 12 (SDK 34)"))
        assertTrue(txt.contains("{title} ({year}) [{resolution}]"))
        assertTrue(txt.contains("Inception.2010.1080p.mkv"))

        val json = LogExporter.formatAsJson(logs, config, device = "OnePlus 12", sdkInt = 34, nowMillis = 1_700_000_000_000L)
        val parsed = Gson().fromJson(json, LogExportPayload::class.java)
        assertEquals(2, parsed.totalEntries)
        assertEquals("07:10:00", parsed.logs[0].timestamp)
        assertEquals("🎬 MEDIA ORGANIZER SCAN STARTED", parsed.logs[0].message)
        assertEquals("/storage/emulated/0/Download", parsed.configSummary.basePath)
    }

    @Test
    fun configBackupRoundTripPreservesAllSettings() {
        val original = AppConfig(
            basePath = "/storage/emulated/0/CustomDownloads",
            movieDir = "/storage/emulated/0/Library/Movies",
            tvDir = "/storage/emulated/0/Library/Shows",
            wikiFirst = true,
            movieLookupOnline = true,
            junkPaths = listOf("/storage/emulated/0/JunkA", "/storage/emulated/0/JunkB"),
            excludedPaths = listOf("/storage/emulated/0/DCIM", "Private"),
            movieTemplate = "{title} ({year}) [{resolution}] [{codec}]",
            tvTemplate = "{show} - {tag} - {episode_title} [{audio}]",
            watchdogEnabled = true,
            watchdogIntervalHours = 12,
            watchdogRequireCharging = true,
            watchdogRequireIdle = true
        )

        val json = ConfigBackupManager.exportConfigToJson(original)
        val restored = ConfigBackupManager.importConfigFromJson(json, AppConfig())

        assertEquals(original, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun configImportRejectsInvalidJson() {
        ConfigBackupManager.importConfigFromJson("{\"unrelated\": true}", AppConfig())
    }
}
