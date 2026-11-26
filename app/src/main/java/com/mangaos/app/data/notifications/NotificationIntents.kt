package com.mangaos.app.data.notifications

/** Channel ids, broadcast actions and intent extras shared by every notification producer. */
object NotificationIntents {

    const val CHANNEL_DOWNLOADS = "mangaos_downloads"
    const val CHANNEL_NEW_CHAPTERS = "mangaos_new_chapters"

    const val ACTION_PAUSE = "com.mangaos.app.action.DOWNLOAD_PAUSE"
    const val ACTION_RESUME = "com.mangaos.app.action.DOWNLOAD_RESUME"
    const val ACTION_CANCEL = "com.mangaos.app.action.DOWNLOAD_CANCEL"
    const val ACTION_RETRY = "com.mangaos.app.action.DOWNLOAD_RETRY"

    const val EXTRA_TASK_ID = "com.mangaos.app.extra.TASK_ID"
    const val EXTRA_SOURCE_ID = "com.mangaos.app.extra.SOURCE_ID"
    const val EXTRA_MANGA_ID = "com.mangaos.app.extra.MANGA_ID"
    const val EXTRA_LOCAL = "com.mangaos.app.extra.LOCAL"
    const val EXTRA_NOTIFICATION_ID = "com.mangaos.app.extra.NOTIFICATION_ID"

    const val GROUP_NEW_CHAPTERS = "mangaos.new_chapters"

    /** Stable, non-zero notification id for a task, so updates replace the same notification. */
    fun downloadNotificationId(taskId: String): Int {
        val hash = taskId.hashCode()
        val value = (hash and 0x7FFFFFFF) % 90000 + 1000
        return value
    }

    fun chapterNotificationId(mangaKey: String): Int {
        val hash = mangaKey.hashCode()
        return (hash and 0x7FFFFFFF) % 40000 + 100000
    }

    const val SUMMARY_NOTIFICATION_ID = 900001
}
