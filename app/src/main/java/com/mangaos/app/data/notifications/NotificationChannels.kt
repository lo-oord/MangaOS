package com.mangaos.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.mangaos.app.R

/**
 * Two channels with different purposes: download progress is quiet and updated in place,
 * new-chapter alerts follow the user's sound/vibration preference.
 */
object NotificationChannels {

    fun ensure(context: Context, sound: Boolean, vibration: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val downloads = NotificationChannel(
            NotificationIntents.CHANNEL_DOWNLOADS,
            context.getString(R.string.notif_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_downloads_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(downloads)

        val chapters = NotificationChannel(
            NotificationIntents.CHANNEL_NEW_CHAPTERS,
            context.getString(R.string.notif_channel_chapters),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_chapters_desc)
            setShowBadge(true)
            enableVibration(vibration)
            if (sound) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
        }
        manager.createNotificationChannel(chapters)
    }
}
