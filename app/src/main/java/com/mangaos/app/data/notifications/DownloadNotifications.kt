package com.mangaos.app.data.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mangaos.app.MainActivity
import com.mangaos.app.R
import com.mangaos.app.core.util.appString
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadChapterState
import com.mangaos.app.domain.model.DownloadState
import com.mangaos.app.domain.model.AppSettings
import java.io.File
import kotlin.math.roundToInt

/**
 * Android notifications for download tasks. One stable notification id per task means progress
 * updates replace the existing notification instead of flooding the shade; a missing cover only
 * removes the thumbnail and never blocks the download.
 */
class DownloadNotifications(private val context: Context) {

    private val coverCache = object : android.util.LruCache<String, Bitmap>(6) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun ensureChannels(settings: AppSettings) {
        NotificationChannels.ensure(context, settings.notificationSound, settings.notificationVibration)
    }

    fun isEnabled(settings: AppSettings): Boolean = settings.notificationEnabled()

    fun build(task: Download): Notification {
        val state = task.state
        val text = contentText(task)
        val cover = thumbnail(task)
        val builder = NotificationCompat.Builder(context, NotificationIntents.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_stat_mangaos)
            .setColor(0xFF018AFE.toInt())
            .setContentTitle(task.mangaTitle)
            .setContentText(text)
            .setSubText(subText(task))
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(if (task.createdAt > 0L) task.createdAt else System.currentTimeMillis())
            .setShowWhen(false)
            .setContentIntent(openTask(task))
            .setOngoing(state == DownloadState.DOWNLOADING || state == DownloadState.QUEUED)

        // The real cover of the work, taken from the app-private copy the manager cached, so
        // the notification never waits on the network and never shows another work's cover.
        if (cover != null) {
            builder.setLargeIcon(cover)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(cover)
                    .setBigContentTitle(task.mangaTitle)
                    .setSummaryText(text),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        when (state) {
            DownloadState.QUEUED -> builder.setProgress(0, 0, true)
            DownloadState.DOWNLOADING -> builder.setProgress(
                100,
                (task.progress * 100f).roundToInt().coerceIn(0, 100),
                false,
            )
            DownloadState.PAUSED -> builder.setProgress(
                100,
                (task.progress * 100f).roundToInt().coerceIn(0, 100),
                false,
            )
            DownloadState.COMPLETED -> builder.setAutoCancel(true)
            DownloadState.FAILED, DownloadState.PARTIAL -> builder.setAutoCancel(false)
            DownloadState.CANCELLED -> builder.setAutoCancel(true)
        }

        when (state) {
            DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                builder.addAction(
                    R.mipmap.ic_stat_mangaos,
                    context.appString(R.string.notif_action_pause),
                    action(task.id, NotificationIntents.ACTION_PAUSE, 1),
                )
                builder.addAction(
                    R.mipmap.ic_stat_mangaos,
                    context.appString(R.string.notif_action_cancel),
                    action(task.id, NotificationIntents.ACTION_CANCEL, 2),
                )
            }
            DownloadState.PAUSED -> {
                builder.addAction(
                    R.mipmap.ic_stat_mangaos,
                    context.appString(R.string.notif_action_resume),
                    action(task.id, NotificationIntents.ACTION_RESUME, 3),
                )
                builder.addAction(
                    R.mipmap.ic_stat_mangaos,
                    context.appString(R.string.notif_action_cancel),
                    action(task.id, NotificationIntents.ACTION_CANCEL, 4),
                )
            }
            DownloadState.FAILED, DownloadState.PARTIAL -> {
                builder.addAction(
                    R.mipmap.ic_stat_mangaos,
                    context.appString(R.string.notif_action_retry),
                    action(task.id, NotificationIntents.ACTION_RETRY, 5),
                )
                builder.addAction(
                    R.mipmap.ic_stat_mangaos,
                    context.appString(R.string.notif_action_cancel),
                    action(task.id, NotificationIntents.ACTION_CANCEL, 6),
                )
            }
            DownloadState.COMPLETED, DownloadState.CANCELLED -> Unit
        }

        return builder.build()
    }

    fun notify(task: Download, settings: AppSettings) {
        if (!isEnabled(settings)) return
        ensureChannels(settings)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NotificationIntents.downloadNotificationId(task.id), build(task))
        }
    }

    fun cancel(taskId: String) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(NotificationIntents.downloadNotificationId(taskId))
        }
    }

    private fun contentText(task: Download): String {
        val stateLabel = context.appString(stateLabel(task.state))
        val percent = (task.progress * 100f).roundToInt().coerceIn(0, 100)
        return when {
            task.totalChapters <= 1 -> {
                val chapter = task.chapters.firstOrNull()?.number.orEmpty()
                when (task.state) {
                    DownloadState.DOWNLOADING -> context.appString(
                        R.string.notif_download_progress_single,
                        chapter,
                        percent,
                    )
                    DownloadState.COMPLETED -> context.appString(
                        R.string.notif_download_completed_single,
                        chapter,
                    )
                    else -> context.appString(R.string.notif_download_chapter_state, chapter, stateLabel)
                }
            }
            else -> when (task.state) {
                DownloadState.DOWNLOADING, DownloadState.PAUSED -> context.appString(
                    R.string.notif_download_progress_multi,
                    task.completedChapters,
                    task.totalChapters,
                    percent,
                )
                DownloadState.COMPLETED -> context.appString(
                    R.string.notif_download_completed_multi,
                    task.totalChapters,
                )
                else -> context.appString(
                    R.string.notif_download_task_state,
                    task.completedChapters,
                    task.totalChapters,
                    stateLabel,
                )
            }
        }
    }

    /** Second line: the chapter, plus the real page counter while it is downloading. */
    private fun subText(task: Download): String? {
        if (task.totalChapters != 1) return null
        val chapter = task.chapters.firstOrNull() ?: return null
        val chapterLabel = context.appString(R.string.chapter_number, chapter.number)
        val pages = if (
            chapter.state == DownloadChapterState.DOWNLOADING && chapter.pageCount > 0
        ) {
            context.appString(
                R.string.notif_pages_progress,
                chapter.downloadedPages,
                chapter.pageCount,
            )
        } else {
            null
        }
        return listOfNotNull(chapterLabel, pages)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }

    private fun stateLabel(state: DownloadState): Int = when (state) {
        DownloadState.QUEUED -> R.string.download_status_queued
        DownloadState.DOWNLOADING -> R.string.download_status_downloading
        DownloadState.PAUSED -> R.string.download_status_paused
        DownloadState.PARTIAL -> R.string.download_status_partial
        DownloadState.COMPLETED -> R.string.download_status_completed
        DownloadState.FAILED -> R.string.download_status_failed
        DownloadState.CANCELLED -> R.string.download_status_cancelled
    }

    private fun openTask(task: Download): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntents.EXTRA_SOURCE_ID, task.manga.sourceId)
            putExtra(NotificationIntents.EXTRA_MANGA_ID, task.manga.id)
            putExtra(NotificationIntents.EXTRA_LOCAL, true)
        }
        return PendingIntent.getActivity(
            context,
            NotificationIntents.downloadNotificationId(task.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(taskId: String, actionName: String, offset: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = actionName
            putExtra(NotificationIntents.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationIntents.downloadNotificationId(taskId) * 10 + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun thumbnail(task: Download): Bitmap? {
        val path = task.localCoverPath?.let { uri ->
            if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
        } ?: return null
        coverCache.get(path)?.let { return it }
        return runCatching {
            if (!File(path).exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, options)?.also { coverCache.put(path, it) }
        }.getOrNull()
    }

    private fun AppSettings.notificationEnabled(): Boolean =
        notificationsEnabled && downloadNotifications
}
