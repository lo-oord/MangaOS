package com.mangaos.app.core.session

/**
 * The reader registers the work it currently displays. Local deletion checks this so a page
 * that is being read is never removed from under the reader; the UI then tells the user why.
 */
object ReaderSession {

    private val activeWorks = mutableSetOf<String>()

    @Synchronized
    fun enter(mangaKey: String) {
        activeWorks += mangaKey
    }

    @Synchronized
    fun exit(mangaKey: String) {
        activeWorks -= mangaKey
    }

    @Synchronized
    fun isReading(mangaKey: String): Boolean = mangaKey in activeWorks
}
