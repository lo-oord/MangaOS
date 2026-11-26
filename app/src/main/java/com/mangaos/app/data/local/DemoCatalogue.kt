package com.mangaos.app.data.local

import com.mangaos.app.R
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadStatus
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.MangaType
import com.mangaos.app.domain.model.NotificationType
import java.util.concurrent.TimeUnit

/**
 * Phase 1 ships no manga source. Everything below is clearly-labelled sample data so the
 * screens, states and interactions can be designed and reviewed. Replacing this object
 * with a real remote source is the only change the data layer needs later.
 */
object DemoCatalogue {

    private val startedAt: Long = System.currentTimeMillis()

    private fun minutes(value: Long): Long = TimeUnit.MINUTES.toMillis(value)
    private fun hours(value: Long): Long = TimeUnit.HOURS.toMillis(value)
    private fun days(value: Long): Long = TimeUnit.DAYS.toMillis(value)

    /** Declared before [works]: [entry] reads it while the object is being initialised. */
    private val covers = listOf(
        R.drawable.demo_cover_01, R.drawable.demo_cover_02, R.drawable.demo_cover_03,
        R.drawable.demo_cover_04, R.drawable.demo_cover_05, R.drawable.demo_cover_06,
        R.drawable.demo_cover_07, R.drawable.demo_cover_08, R.drawable.demo_cover_09,
        R.drawable.demo_cover_10, R.drawable.demo_cover_11, R.drawable.demo_cover_12,
        R.drawable.demo_cover_13, R.drawable.demo_cover_14, R.drawable.demo_cover_15,
        R.drawable.demo_cover_16, R.drawable.demo_cover_17, R.drawable.demo_cover_18,
    )

    val works: List<Manga> = listOf(
        entry("m01", "Azure Blade", MangaType.MANHWA, MangaStatus.ONGOING, 9.4, "128", minutes(2), 1),
        entry("m02", "Tide Walker", MangaType.MANHWA, MangaStatus.ONGOING, 8.9, "64", minutes(3), 2),
        entry("m03", "Hollow Crown", MangaType.MANGA, MangaStatus.COMPLETED, 9.8, "212", minutes(9), 3),
        entry("m04", "Paper Moon", MangaType.MANGA, MangaStatus.ONGOING, 8.2, "37", hours(1), 4),
        entry("m05", "Iron Lantern", MangaType.MANGA, MangaStatus.HIATUS, 7.9, "88", hours(3), 5),
        entry("m06", "Sakura Protocol", MangaType.MANGA, MangaStatus.ONGOING, 9.1, "45", hours(5), 6),
        entry("m07", "Neon Requiem", MangaType.MANHWA, MangaStatus.ONGOING, 9.6, "73", hours(8), 7),
        entry("m08", "Quiet Harbor", MangaType.MANGA, MangaStatus.COMPLETED, 8.4, "31", hours(20), 8),
        entry("m09", "Glass Sparrow", MangaType.MANGA, MangaStatus.CANCELLED, 6.8, "19", hours(26), 9),
        entry("m10", "Northern Static", MangaType.MANHWA, MangaStatus.ONGOING, 8.7, "102", days(2), 10),
        entry("m11", "Kite Season", MangaType.MANGA, MangaStatus.COMPLETED, 9.2, "58", days(3), 11),
        entry("m12", "Amber Circuit", MangaType.MANHWA, MangaStatus.ONGOING, 8.5, "27", days(5), 12),
        entry("m13", "Lost Signal", MangaType.MANHWA, MangaStatus.HIATUS, 7.4, "44", days(8), 13),
        entry("m14", "Garden of Wires", MangaType.MANGA, MangaStatus.ONGOING, 9.3, "96", days(12), 14),
        entry("m15", "Midnight Ferry", MangaType.MANGA, MangaStatus.ONGOING, 8.1, "22", days(20), 15),
        entry("m16", "Scarlet Ledger", MangaType.MANHWA, MangaStatus.COMPLETED, 9.0, "140", days(25), 16),
        entry("m17", "Soft Thunder", MangaType.MANGA, MangaStatus.ONGOING, 8.8, "51", days(40), 17),
        entry("m18", "Echo District", MangaType.MANHWA, MangaStatus.ONGOING, 9.5, "85", days(70), 18),
    )

    private fun entry(
        id: String,
        title: String,
        type: MangaType,
        status: MangaStatus,
        rating: Double,
        latestChapter: String,
        updatedAgo: Long,
        coverIndex: Int,
    ) = Manga(
        id = id,
        title = title,
        type = type,
        status = status,
        rating = rating,
        latestChapter = latestChapter,
        updatedAt = startedAt - updatedAgo,
        localCoverRes = covers[coverIndex - 1],
    )

    fun mangaById(id: String): Manga? = works.firstOrNull { it.id == id }

    fun chaptersFor(mangaId: String): List<Chapter> {
        val manga = mangaById(mangaId) ?: return emptyList()
        val latest = manga.latestChapter?.toIntOrNull() ?: 12
        return (1..12).map { index ->
            val number = (latest - index + 1).coerceAtLeast(1)
            Chapter(
                id = "$mangaId-$number",
                mangaId = mangaId,
                number = number.toString(),
                publishedAt = startedAt - days(index.toLong()),
                isRead = false,
                downloadStatus = ChapterDownloadStatus.NOT_DOWNLOADED,
            )
        }
    }

    /** Two works already saved on the device, so the local storage screen has real rows. */
    val seededStoredWorks: Map<String, List<Chapter>> = mapOf(
        "m03" to chaptersFor("m03").take(4).map {
            it.copy(
                isRead = true,
                downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                sizeBytes = 24_500_000L,
                localPath = "mangaos/m03/${it.number}.cbz",
            )
        },
        "m07" to chaptersFor("m07").take(2).map {
            it.copy(
                downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                sizeBytes = 31_200_000L,
                localPath = "mangaos/m07/${it.number}.cbz",
            )
        },
    )

    val seededLibrary: Set<String> = setOf("m03", "m07", "m14")

    fun seedDownloads(): List<Download> = listOf(
        Download(
            id = "dl-1",
            mangaId = "m01",
            mangaTitle = "Azure Blade",
            chapterNumber = "127",
            localCoverRes = mangaById("m01")?.localCoverRes,
            status = DownloadStatus.DOWNLOADING,
            progress = 0.34f,
            totalBytes = 28_400_000L,
            downloadedBytes = 9_656_000L,
        ),
        Download(
            id = "dl-2",
            mangaId = "m12",
            mangaTitle = "Amber Circuit",
            chapterNumber = "26",
            localCoverRes = mangaById("m12")?.localCoverRes,
            status = DownloadStatus.PAUSED,
            progress = 0.62f,
            totalBytes = 19_800_000L,
            downloadedBytes = 12_276_000L,
        ),
        Download(
            id = "dl-3",
            mangaId = "m14",
            mangaTitle = "Garden of Wires",
            chapterNumber = "95",
            localCoverRes = mangaById("m14")?.localCoverRes,
            status = DownloadStatus.FAILED,
            progress = 0.18f,
            totalBytes = 33_100_000L,
            downloadedBytes = 5_958_000L,
        ),
        Download(
            id = "dl-4",
            mangaId = "m06",
            mangaTitle = "Sakura Protocol",
            chapterNumber = "44",
            localCoverRes = mangaById("m06")?.localCoverRes,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            totalBytes = 21_700_000L,
            downloadedBytes = 0L,
        ),
    )

    fun seedNotifications(): List<AppNotification> = listOf(
        AppNotification(
            id = "n1",
            title = "Download failed",
            message = "Garden of Wires — chapter 95",
            type = NotificationType.DOWNLOAD_FAILED,
            createdAt = startedAt - minutes(4),
            isRead = false,
        ),
        AppNotification(
            id = "n2",
            title = "Download complete",
            message = "Hollow Crown — chapter 212 is ready in local storage",
            type = NotificationType.DOWNLOAD_COMPLETED,
            createdAt = startedAt - minutes(52),
            isRead = false,
        ),
        AppNotification(
            id = "n3",
            title = "Download started",
            message = "Amber Circuit — chapter 26",
            type = NotificationType.DOWNLOAD_STATUS,
            createdAt = startedAt - hours(5),
            isRead = true,
        ),
        AppNotification(
            id = "n4",
            title = "MangaOS",
            message = "This is a sample notification.",
            type = NotificationType.SYSTEM,
            createdAt = startedAt - days(2),
            isRead = true,
        ),
    )
}
