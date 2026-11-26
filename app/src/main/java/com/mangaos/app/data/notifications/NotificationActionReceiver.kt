package com.mangaos.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mangaos.app.di.ServiceLocator

/**
 * Notification actions are real: pause/resume/cancel/retry go through the same download
 * manager the screens use, so the notification and the in-app state never disagree.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(NotificationIntents.EXTRA_TASK_ID) ?: return
        val downloads = ServiceLocator.downloads
        when (intent.action) {
            NotificationIntents.ACTION_PAUSE -> downloads.pause(taskId)
            NotificationIntents.ACTION_RESUME -> downloads.resume(taskId)
            NotificationIntents.ACTION_CANCEL -> downloads.cancel(taskId)
            NotificationIntents.ACTION_RETRY -> downloads.retry(taskId)
        }
    }
}
