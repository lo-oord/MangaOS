package com.mangaos.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mangaos.app.R
import com.mangaos.app.data.repository.ChapterWatchStore
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.model.baselineChapterIds
import com.mangaos.app.domain.model.detectNewChapters
import com.mangaos.app.domain.model.latestChapterNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The check runs every 20 minutes; Android only guarantees an approximate interval. */
class NewChapterScheduler(private val context: Context) {

    fun schedule(accountId: String? = null) {
        val request = PeriodicWorkRequestBuilder<NewChapterCheckWorker>(
            CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName(accountId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(accountId: String? = null) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(accountId))
    }

    /** Signing out stops the check for the account instead of leaving it running for nobody. */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    companion object {
        const val CHECK_INTERVAL_MINUTES = 20L
        private const val TAG = "mangaos_new_chapter_check"

        /** One periodic job per account, so two signed-in accounts cannot overwrite each other. */
        private fun uniqueName(accountId: String?): String =
            "mangaos_new_chapter_check_${accountId ?: "local"}"
    }
}

/**
 * Looks only at works saved in the user's library, compares the source's chapter list with the
 * stored baseline, and notifies exactly once per new chapter. A work without a baseline is
 * initialised silently, and a failing source never produces an alert.
 */
class NewChapterCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val preferences = ServiceLocator.settings.appSettings.first()
        if (!preferences.notificationsEnabled || !preferences.newChapterNotifications) {
            return Result.success()
        }

        val library = ServiceLocator.library
        val mangaRepository = ServiceLocator.manga
        val watchStore = ServiceLocator.chapterWatch
        val notifications = ServiceLocator.notifications
        val notifier = ServiceLocator.newChapterNotifications
        val covers = ServiceLocator.coverStore

        val works = runCatching { library.worksSnapshot() }.getOrDefault(emptyList())
        var failures = 0

        works.forEach { manga ->
            try {
                val chapters = mangaRepository.chapters(manga.sourceId, manga.id)
                if (chapters.isEmpty()) return@forEach
                val existing = watchStore.watch(manga.key)
                val latestNumber = latestChapterNumber(chapters)

                if (existing == null) {
                    // First sync is a baseline only: never announce the back catalogue.
                    watchStore.save(
                        manga.key,
                        ChapterWatchStore.Watch(
                            knownIds = baselineChapterIds(chapters),
                            lastNumber = latestNumber,
                        ),
                    )
                    return@forEach
                }

                val newChapters = detectNewChapters(
                    chapters = chapters,
                    knownIds = existing.knownIds,
                    baselineNumber = existing.lastNumber,
                )

                if (newChapters.isNotEmpty()) {
                    val thumbnail = covers.cache(
                        manga.key,
                        manga.coverUrl,
                        mangaRepository.imageHeaders(manga.sourceId),
                    )
                    val createdAt = System.currentTimeMillis()
                    val records = newChapters.take(MAX_IN_APP_RECORDS).map { chapter ->
                        AppNotification(
                            id = UUID.randomUUID().toString(),
                            title = manga.title,
                            message = applicationContext.getString(
                                R.string.notif_new_chapter_line,
                                chapter.number,
                            ),
                            type = NotificationType.NEW_CHAPTER,
                            createdAt = createdAt,
                            sourceId = manga.sourceId,
                            mangaId = manga.id,
                            chapterNumber = chapter.number,
                            coverUrl = manga.coverUrl,
                            thumbnailPath = thumbnail,
                        )
                    }
                    records.forEach(notifications::add)
                    notifier.notify(
                        manga = manga,
                        chapters = newChapters,
                        appNotificationId = records.firstOrNull()?.id,
                        thumbnailPath = thumbnail,
                        settings = preferences,
                    )
                }

                watchStore.save(
                    manga.key,
                    ChapterWatchStore.Watch(
                        knownIds = baselineChapterIds(chapters, existing.knownIds),
                        lastNumber = maxOfNotNull(latestNumber, existing.lastNumber),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
            }
        }

        watchStore.lastCheckAt = System.currentTimeMillis()
        watchStore.lastCheckResult = if (failures > 0) "failed:$failures" else "ok"
        return Result.success()
    }

    private fun maxOfNotNull(first: Float?, second: Float?): Float? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private companion object {
        const val MAX_IN_APP_RECORDS = 20
    }
}
