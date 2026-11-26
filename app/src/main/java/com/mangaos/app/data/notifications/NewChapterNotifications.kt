package com.mangaos.app.data.notifications

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
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.AppSettings
import java.io.File

/**
 * New-chapter alerts: one aggregated notification per work, so five new chapters do not mean
 * five notifications. Tapping it opens the work itself through source + manga ids.
 */
class NewChapterNotifications(private val context: Context) {

    fun notify(
        manga: Manga,
        chapters: List<Chapter>,
        appNotificationId: String?,
        thumbnailPath: String?,
        settings: AppSettings,
    ) {
        if (!settings.notificationsEnabled || !settings.newChapterNotifications) return
        if (chapters.isEmpty()) return
        NotificationChannels.ensure(context, settings.notificationSound, settings.notificationVibration)

        val style = NotificationCompat.InboxStyle()
        chapters.take(MAX_LINES).forEach { chapter ->
            style.addLine(
                context.appString(R.string.notif_new_chapter_line, chapter.number),
            )
        }
        if (chapters.size > MAX_LINES) {
            style.setSummaryText(
                context.appString(R.string.notif_new_chapter_more, chapters.size - MAX_LINES),
            )
        }

        val builder = NotificationCompat.Builder(context, NotificationIntents.CHANNEL_NEW_CHAPTERS)
            .setSmallIcon(R.mipmap.ic_stat_mangaos)
            .setColor(0xFF018AFE.toInt())
            .setContentTitle(manga.title)
            .setSubText(context.appString(R.string.notif_new_chapter_available))
            .setContentText(
                context.appString(R.string.notif_new_chapter_line, chapters.first().number),
            )
            .setStyle(style)
            .setAutoCancel(true)
            .setGroup(NotificationIntents.GROUP_NEW_CHAPTERS)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOnlyAlertOnce(false)
            .setContentIntent(openManga(manga, appNotificationId))

        thumbnail(thumbnailPath)?.let { builder.setLargeIcon(it) }

        runCatching {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(
                NotificationIntents.chapterNotificationId(manga.key),
                builder.build(),
            )
            manager.notify(
                NotificationIntents.SUMMARY_NOTIFICATION_ID,
                NotificationCompat.Builder(context, NotificationIntents.CHANNEL_NEW_CHAPTERS)
                    .setSmallIcon(R.mipmap.ic_stat_mangaos)
                    .setColor(0xFF018AFE.toInt())
                    .setContentTitle(context.appString(R.string.notif_new_chapter_available))
                    .setContentText(context.appString(R.string.notif_new_chapter_group_text))
                    .setGroup(NotificationIntents.GROUP_NEW_CHAPTERS)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    fun cancel(mangaKey: String) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(NotificationIntents.chapterNotificationId(mangaKey))
        }
    }

    private fun openManga(manga: Manga, appNotificationId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntents.EXTRA_SOURCE_ID, manga.sourceId)
            putExtra(NotificationIntents.EXTRA_MANGA_ID, manga.id)
            putExtra(NotificationIntents.EXTRA_LOCAL, false)
            appNotificationId?.let {
                putExtra(NotificationIntents.EXTRA_NOTIFICATION_ID, it)
            }
        }
        return PendingIntent.getActivity(
            context,
            NotificationIntents.chapterNotificationId(manga.key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun thumbnail(path: String?): Bitmap? {
        val local = path?.let { if (it.startsWith("file://")) it.removePrefix("file://") else it }
            ?: return null
        return runCatching {
            if (!File(local).exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(local, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
                sample *= 2
            }
            BitmapFactory.decodeFile(local, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private companion object {
        const val MAX_LINES = 6
    }
}
