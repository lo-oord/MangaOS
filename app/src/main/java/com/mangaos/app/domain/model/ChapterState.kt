package com.mangaos.app.domain.model

/**
 * Reading state of one chapter, independent of [ChapterDownloadStatus].
 *
 * A chapter is only READ when the reader actually reached its end; opening the details screen,
 * the chapter list or starting a download never changes this state. IN_PROGRESS means the
 * chapter holds the work's saved reading position but was not finished yet.
 */
enum class ChapterReadingState { UNREAD, IN_PROGRESS, READ }

/** The single rule every screen uses, so details, the list and the reader cannot disagree. */
fun chapterReadingState(isRead: Boolean, holdsReadingPosition: Boolean): ChapterReadingState = when {
    isRead -> ChapterReadingState.READ
    holdsReadingPosition -> ChapterReadingState.IN_PROGRESS
    else -> ChapterReadingState.UNREAD
}
