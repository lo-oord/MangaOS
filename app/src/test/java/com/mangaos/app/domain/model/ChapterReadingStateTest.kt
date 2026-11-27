package com.mangaos.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterReadingStateTest {

    @Test
    fun `a finished chapter is read`() {
        assertEquals(ChapterReadingState.READ, chapterReadingState(isRead = true, holdsReadingPosition = false))
        assertEquals(ChapterReadingState.READ, chapterReadingState(isRead = true, holdsReadingPosition = true))
    }

    @Test
    fun `a started chapter is in progress`() {
        assertEquals(
            ChapterReadingState.IN_PROGRESS,
            chapterReadingState(isRead = false, holdsReadingPosition = true),
        )
    }

    @Test
    fun `an untouched chapter is unread`() {
        assertEquals(
            ChapterReadingState.UNREAD,
            chapterReadingState(isRead = false, holdsReadingPosition = false),
        )
    }
}
