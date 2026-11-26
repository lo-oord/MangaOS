package com.mangaos.app.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mangaos.app.data.download.LocalDownloadRepository
import com.mangaos.app.data.notifications.NotificationIntents
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.CancellationException

/**
 * Runs the queue that the download manager owns. WorkManager keeps the process alive, retries
 * when the network is unavailable, and restores the work after the app was closed or the
 * device restarted. The notification is the same object the manager keeps updating.
 */
class DownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = ServiceLocator.downloads as? LocalDownloadRepository
            ?: return Result.success()
        return try {
            val drained = repository.runQueue { task ->
                val notification = repository.notificationFor(task)
                val id = NotificationIntents.downloadNotificationId(task.id)
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ForegroundInfo(
                        id,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    ForegroundInfo(id, notification)
                }
                // Foreground promotion can be refused while the app is in the background on
                // some OEM builds; the download still runs, it is just not a foreground one.
                runCatching { setForeground(info) }
            }
            if (drained) {
                repository.pumpIfPending()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            repository.pumpIfPending()
            Result.failure()
        }
    }
}
