package com.mediaorganizer.app

import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LogEntryPayload(
    val timestamp: String,
    val message: String,
    val raw: String
)

data class LogExportPayload(
    val exportedAt: String,
    val appVersion: String,
    val device: String,
    val androidSdk: Int,
    val configSummary: ConfigSummaryPayload,
    val totalEntries: Int,
    val logs: List<LogEntryPayload>
)

data class ConfigSummaryPayload(
    val basePath: String,
    val movieDir: String,
    val tvDir: String,
    val movieTemplate: String,
    val tvTemplate: String,
    val wikiFirst: Boolean
)

object LogExporter {
    private val LOG_LINE_REGEX = Regex("^\\[([^\\]]+)\\]\\s*(.*)$")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun isoTimestamp(nowMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(nowMillis))
    }

    fun formatAsText(
        logs: List<String>,
        config: AppConfig,
        appVersion: String = "1.0.0",
        device: String = "Android",
        sdkInt: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("MediaOrganizer Diagnostic Log")
        sb.appendLine("Exported At    : ${isoTimestamp(nowMillis)}")
        sb.appendLine("App Version    : $appVersion")
        if (sdkInt > 0) {
            sb.appendLine("Device         : $device (SDK $sdkInt)")
        }
        sb.appendLine("Source Dir     : ${config.basePath}")
        sb.appendLine("Movie Target   : ${config.movieDir}")
        sb.appendLine("TV Target      : ${config.tvDir}")
        sb.appendLine("Movie Template : ${config.movieTemplate}")
        sb.appendLine("TV Template    : ${config.tvTemplate}")
        sb.appendLine("Wiki First     : ${config.wikiFirst}")
        sb.appendLine("=".repeat(60))
        if (logs.isEmpty()) {
            sb.appendLine("(No log entries recorded)")
        } else {
            logs.forEach { sb.appendLine(it) }
        }
        return sb.toString()
    }

    fun formatAsJson(
        logs: List<String>,
        config: AppConfig,
        appVersion: String = "1.0.0",
        device: String = "Android",
        sdkInt: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val entries = logs.map { line ->
            val match = LOG_LINE_REGEX.matchEntire(line)
            if (match != null) {
                LogEntryPayload(
                    timestamp = match.groupValues[1],
                    message = match.groupValues[2],
                    raw = line
                )
            } else {
                LogEntryPayload(timestamp = "", message = line, raw = line)
            }
        }

        val payload = LogExportPayload(
            exportedAt = isoTimestamp(nowMillis),
            appVersion = appVersion,
            device = device,
            androidSdk = sdkInt,
            configSummary = ConfigSummaryPayload(
                basePath = config.basePath,
                movieDir = config.movieDir,
                tvDir = config.tvDir,
                movieTemplate = config.movieTemplate,
                tvTemplate = config.tvTemplate,
                wikiFirst = config.wikiFirst
            ),
            totalEntries = entries.size,
            logs = entries
        )
        return gson.toJson(payload)
    }
}
