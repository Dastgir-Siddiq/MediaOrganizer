package com.mediaorganizer.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mediaorganizer.app.MediaScanner.MediaItem
import com.mediaorganizer.app.data.AppDatabase
import java.io.File
import java.util.concurrent.TimeUnit

object WatchdogNotificationHelper {
    const val CHANNEL_ID = "media_watchdog_channel"
    const val NOTIFICATION_ID_SCAN_PROMPT = 1001
    const val NOTIFICATION_ID_ORGANIZE_RESULT = 1002

    const val ACTION_PREVIEW_SCAN = "com.mediaorganizer.app.ACTION_PREVIEW_SCAN"
    const val ACTION_ORGANIZE_NOW = "com.mediaorganizer.app.ACTION_ORGANIZE_NOW"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Watchdog",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for detected movies and TV episodes in Source Directory"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showScanReadyNotification(context: Context, readyCount: Int) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val previewIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_PREVIEW_SCAN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val previewPendingIntent = PendingIntent.getActivity(
            context,
            101,
            previewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val organizeIntent = Intent(context, WatchdogActionReceiver::class.java).apply {
            action = ACTION_ORGANIZE_NOW
        }
        val organizePendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            organizeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val noun = if (readyCount == 1) "file" else "files"
        val bodyText = "$readyCount new $noun ready — Preview or Organize Now"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_scan)
            .setContentTitle("Media Organizer")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(previewPendingIntent)
            .addAction(0, "Preview", previewPendingIntent)
            .addAction(0, "Organize Now", organizePendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SCAN_PROMPT, notification)
    }

    fun showManualNoFilesNotification(context: Context, message: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            103,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_scan)
            .setContentTitle("Media Scan Complete")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SCAN_PROMPT, notification)
    }

    fun showOrganizeResultNotification(context: Context, result: BatchExecutionResult) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            104,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = buildString {
            append("Organized ${result.movieCount} movie(s), ${result.tvCount} episode(s)")
            if (result.skippedMovieCount + result.skippedTvCount > 0) {
                append(" (${result.skippedMovieCount + result.skippedTvCount} skipped)")
            }
            if (result.batchId != null) {
                append(" • Open app to Undo Batch #${result.batchId}")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_scan)
            .setContentTitle("Media Organized")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ORGANIZE_RESULT, notification)
    }

    fun cancelScanPrompt(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SCAN_PROMPT)
    }
}

internal fun hasAllFilesStorageAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }
}

class WatchdogScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val isManualTrigger = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)
        val config = AppPreferences.loadConfig(applicationContext)

        if (!hasAllFilesStorageAccess(applicationContext)) {
            AppPreferences.setLastScanSubtitle(applicationContext, "Grant storage access")
            requestTileRefresh(applicationContext)
            if (isManualTrigger) {
                WatchdogNotificationHelper.showManualNoFilesNotification(
                    applicationContext,
                    "Storage permission required to scan Source Directory."
                )
            }
            return Result.success()
        }

        val baseDir = File(config.basePath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            AppPreferences.setLastScanSubtitle(applicationContext, "Invalid source path")
            requestTileRefresh(applicationContext)
            if (isManualTrigger) {
                WatchdogNotificationHelper.showManualNoFilesNotification(
                    applicationContext,
                    "Source Directory not found: ${config.basePath}"
                )
            }
            return Result.success()
        }

        val rawItems = MediaScanner.scanDirectory(baseDir, config.excludedPaths)
        val readyCount = countReadyItems(rawItems, config)

        if (readyCount > 0) {
            val noun = if (readyCount == 1) "file" else "files"
            AppPreferences.setLastScanSubtitle(applicationContext, "$readyCount $noun ready")
            WatchdogNotificationHelper.showScanReadyNotification(applicationContext, readyCount)
        } else {
            AppPreferences.setLastScanSubtitle(applicationContext, "No new media")
            if (isManualTrigger) {
                WatchdogNotificationHelper.showManualNoFilesNotification(
                    applicationContext,
                    "No new movies or TV episodes found in ${baseDir.name}."
                )
            } else {
                WatchdogNotificationHelper.cancelScanPrompt(applicationContext)
            }
        }

        requestTileRefresh(applicationContext)
        return Result.success()
    }

    private fun countReadyItems(items: List<MediaItem>, config: AppConfig): Int {
        var ready = 0
        for (item in items) {
            when (item) {
                is MediaItem.Movie -> {
                    val destName = NamingTemplateFormatter.formatMovieFilename(
                        template = config.movieTemplate,
                        movieName = item.movieName,
                        year = item.year,
                        tags = item.tags,
                        ext = item.file.extension
                    )
                    if (!File(config.movieDir, destName).exists()) {
                        ready++
                    }
                }
                is MediaItem.TvEpisode -> {
                    val seasonFolder = File(File(config.tvDir, item.showName), String.format("%02d", item.season))
                    val prefix = "${item.showName} ${item.tag}".lowercase()
                    val alreadyOrganized = seasonFolder.listFiles()?.any { f ->
                        MediaScanner.isVideoFile(f) && (
                            f.name.lowercase().startsWith(prefix) ||
                                MediaScanner.classifyTvEpisode(item.showName, f.name)?.let { ep ->
                                    ep.season == item.season && ep.episode == item.episode
                                } == true
                            )
                    } == true
                    if (!alreadyOrganized) {
                        ready++
                    }
                }
            }
        }
        return ready
    }

    companion object {
        const val KEY_MANUAL_TRIGGER = "manual_trigger"

        fun requestTileRefresh(context: Context) {
            try {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, MediaScanTileService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}

class OrganizeNowWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        WatchdogNotificationHelper.cancelScanPrompt(applicationContext)

        if (!hasAllFilesStorageAccess(applicationContext)) {
            return Result.failure()
        }

        val config = AppPreferences.loadConfig(applicationContext)
        val baseDir = File(config.basePath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            return Result.failure()
        }

        val rawItems = MediaScanner.scanDirectory(baseDir, config.excludedPaths)
        if (rawItems.isEmpty()) {
            AppPreferences.setLastScanSubtitle(applicationContext, "No new media")
            WatchdogScanWorker.requestTileRefresh(applicationContext)
            WatchdogNotificationHelper.showManualNoFilesNotification(
                applicationContext,
                "No media files found to organize."
            )
            return Result.success()
        }

        val previewItems = MediaBatchExecutor.buildPreviewItems(
            rawItems = rawItems,
            config = config,
            context = applicationContext
        )

        val dao = AppDatabase.getInstance(applicationContext).transactionDao()
        val result = MediaBatchExecutor.executeBatch(
            items = previewItems,
            transactionDao = dao
        )

        AppPreferences.setLastScanSubtitle(
            applicationContext,
            "Moved ${result.movieCount + result.tvCount} file(s)"
        )
        WatchdogScanWorker.requestTileRefresh(applicationContext)
        WatchdogNotificationHelper.showOrganizeResultNotification(applicationContext, result)
        return Result.success()
    }
}

class WatchdogActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == WatchdogNotificationHelper.ACTION_ORGANIZE_NOW) {
            WatchdogNotificationHelper.cancelScanPrompt(context)
            val work = OneTimeWorkRequestBuilder<OrganizeNowWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    "media_watchdog_organize_now",
                    ExistingWorkPolicy.REPLACE,
                    work
                )
        }
    }
}

object WatchdogScheduler {
    private const val PERIODIC_WORK_NAME = "media_watchdog_periodic"
    private const val IMMEDIATE_SCAN_WORK_NAME = "media_watchdog_immediate_scan"

    fun syncWithConfig(context: Context, config: AppConfig) {
        if (config.watchdogEnabled) {
            schedulePeriodic(
                context = context,
                intervalHours = config.watchdogIntervalHours,
                requireCharging = config.watchdogRequireCharging,
                requireIdle = config.watchdogRequireIdle
            )
        } else {
            cancelPeriodic(context)
        }
    }

    fun schedulePeriodic(
        context: Context,
        intervalHours: Int,
        requireCharging: Boolean,
        requireIdle: Boolean
    ) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(requireCharging)
            .setRequiresDeviceIdle(requireIdle)
            .build()

        val request = if (intervalHours <= 0) {
            PeriodicWorkRequestBuilder<WatchdogScanWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
        } else {
            PeriodicWorkRequestBuilder<WatchdogScanWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        }

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun triggerImmediateScan(context: Context, isManualTrigger: Boolean = true) {
        val request = OneTimeWorkRequestBuilder<WatchdogScanWorker>()
            .setInputData(workDataOf(WatchdogScanWorker.KEY_MANUAL_TRIGGER to isManualTrigger))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                IMMEDIATE_SCAN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
