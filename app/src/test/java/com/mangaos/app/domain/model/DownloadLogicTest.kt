package com.mangaos.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task progress/state derivation and new-chapter detection are pure and unit tested here. */
class DownloadLogicTest {

    private fun chapter(
        id: String,
        number: String,
        state: DownloadChapterState,
        pages: Int = 0,
        downloaded: Int = 0,
    ) = DownloadChapter(
        chapterId = id,
        number = number,
        state = state,
        pageCount = pages,
        downloadedPages = downloaded,
    )

    @Test
    fun `single chapter progress reflects written pages`() {
        val chapters = listOf(
            chapter("c1", "1", DownloadChapterState.DOWNLOADING, pages = 20, downloaded = 5),
        )
        assertEquals(0.25f, downloadProgress(chapters), 0.0001f)
        assertEquals(DownloadState.DOWNLOADING, downloadState(chapters))
    }

    @Test
    fun `completed chapter counts fully inside a multi chapter task`() {
        val chapters = listOf(
            chapter("c1", "1", DownloadChapterState.COMPLETED),
            chapter("c2", "2", DownloadChapterState.DOWNLOADING, pages = 10, downloaded = 5),
            chapter("c3", "3", DownloadChapterState.QUEUED),
            chapter("c4", "4", DownloadChapterState.FAILED),
        )
        // 1 + 0.5 + 0 + 0 over four chapters
        assertEquals(0.375f, downloadProgress(chapters), 0.0001f)
        assertEquals(DownloadState.DOWNLOADING, downloadState(chapters))
    }

    @Test
    fun `partial failure keeps completed chapters and is not completed`() {
        val chapters = listOf(
            chapter("c1", "1", DownloadChapterState.COMPLETED),
            chapter("c2", "2", DownloadChapterState.COMPLETED),
            chapter("c3", "3", DownloadChapterState.FAILED),
        )
        assertEquals(DownloadState.PARTIAL, downloadState(chapters))
        assertEquals(2, chapters.count { it.state == DownloadChapterState.COMPLETED })
    }

    @Test
    fun `a failed chapter is never counted as complete`() {
        val chapters = listOf(
            chapter("c1", "1", DownloadChapterState.COMPLETED),
            chapter("c2", "2", DownloadChapterState.FAILED),
        )
        assertTrue(downloadProgress(chapters) < 1f)
        assertEquals(1, chapters.count { it.state == DownloadChapterState.COMPLETED })
    }

    @Test
    fun `first new chapter sync is a silent baseline`() {
        val chapters = listOf(Chapter("1", "m", "1"), Chapter("2", "m", "2"))
        assertTrue(detectNewChapters(chapters, knownIds = null, baselineNumber = null).isEmpty())
    }

    @Test
    fun `only unseen chapters above the baseline are new`() {
        val chapters = listOf(
            Chapter("a", "m", "10"),
            Chapter("b", "m", "11"),
            Chapter("c", "m", "12"),
            Chapter("x", "m", "1"),
        )
        val detected = detectNewChapters(
            chapters = chapters,
            knownIds = setOf("a", "x"),
            baselineNumber = 10f,
        )
        assertEquals(listOf("11", "12"), detected.map { it.number })
    }

    @Test
    fun `known chapters are never announced twice`() {
        val chapters = listOf(Chapter("b", "m", "11"))
        assertTrue(
            detectNewChapters(chapters, knownIds = setOf("b"), baselineNumber = 10f).isEmpty(),
        )
    }

    @Test
    fun `latest chapter picks the highest numeric label`() {
        val chapters = listOf(
            Chapter("a", "m", "9"),
            Chapter("b", "m", "10.5"),
            Chapter("c", "m", "special"),
        )
        assertEquals("10.5", latestChapterOf(chapters)?.number)
        assertEquals(10.5f, latestChapterNumber(chapters) ?: 0f, 0.0001f)
    }

    @Test
    fun `task finder maps a chapter to its owning task`() {
        val manga = Manga(id = "m", sourceId = "s", title = "Work")
        val tasks = listOf(
            Download(
                id = "t1",
                manga = manga,
                chapters = listOf(chapter("c1", "1", DownloadChapterState.COMPLETED)),
            ),
            Download(
                id = "t2",
                manga = manga,
                chapters = listOf(chapter("c2", "2", DownloadChapterState.QUEUED)),
            ),
        )
        assertEquals("t2", tasks.findChapter(manga.key, "c2")?.first?.id)
        assertEquals(null, tasks.findChapter(manga.key, "missing"))
    }
}
