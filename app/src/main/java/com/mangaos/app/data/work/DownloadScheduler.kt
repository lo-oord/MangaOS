package com.mangaos.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Schedules queue execution on WorkManager. A single unique chain means one task runs at a
 * time (bounded memory, network and source load) and appended work is not lost when a new
 * chapter is queued while the worker is still finishing.
 */
class DownloadScheduler(private val context: Context) {

    fun pump(unmeteredOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private companion object {
        const val UNIQUE_NAME = "mangaos_download_queue"
        const val TAG = "mangaos_downloads"
    }
}
