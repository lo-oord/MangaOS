package com.mangaos.app.data.repository

import android.content.Context
import com.mangaos.app.core.session.ReaderSession
import com.mangaos.app.data.download.ChapterFiles
import com.mangaos.app.data.local.AtomicFileWriter
import com.mangaos.app.data.local.toChapter
import com.mangaos.app.data.local.toJson
import com.mangaos.app.data.local.toManga
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.StoredWork
import com.mangaos.app.domain.repository.LocalStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Durable offline library. One record per work key; finished chapters from later downloads
 * merge into that record instead of creating a second card. Records are written atomically
 * after the chapter files were verified, and reads fall back to "not available" when a file
 * disappeared outside the app.
 */
class PersistentLocalStorageRepository(context: Context) : LocalStorageRepository {

    private val file = File(context.filesDir, RECORDS)
    private val lock = Any()

    private val _works = MutableStateFlow<List<StoredWork>>(emptyList())
    override val works: StateFlow<List<StoredWork>> = _works.asStateFlow()

    init {
        val restored = runCatching { decode(AtomicFileWriter.read(file)) }
            .getOrDefault(emptyList())
            .map { work -> work.copy(chapters = work.chapters.filter { it.isAvailable() }) }
            .filter { it.chapters.isNotEmpty() }
        _works.value = restored
    }

    override fun work(mangaId: String): StoredWork? =
        _works.value.firstOrNull { it.manga.key == mangaId }

    override fun localChapter(mangaId: String, chapterId: String): Chapter? =
        work(mangaId)?.chapters?.firstOrNull { it.id == chapterId && it.isAvailable() }

    override fun saveChapter(manga: Manga, chapter: Chapter, localCoverPath: String?) {
        synchronized(lock) {
            val current = _works.value
            val existing = current.firstOrNull { it.manga.key == manga.key }
            val snapshot = if (existing != null) mergeMetadata(existing.manga, manga) else manga
            val storedChapter = chapter.copy(
                downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                localPath = chapter.localPath,
            )
            val chapters = ((existing?.chapters ?: emptyList()) + storedChapter)
                .associateBy { it.id }
                .values
                .sortedByDescending { it.number.toFloatOrNull() ?: 0f }
            val record = StoredWork(
                manga = snapshot,
                chapters = chapters,
                localCoverPath = localCoverPath ?: existing?.localCoverPath,
                savedAt = existing?.savedAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
            val next = if (existing == null) {
                listOf(record) + current
            } else {
                current.map { if (it.manga.key == manga.key) record else it }
            }
            if (!persist(next)) {
                throw IOException("Could not write the local library record")
            }
            _works.value = next
        }
    }

    override fun deleteChapter(mangaId: String, chapterId: String): Boolean {
        if (ReaderSession.isReading(mangaId)) return false
        synchronized(lock) {
            val existing = _works.value.firstOrNull { it.manga.key == mangaId } ?: return true
            val target = existing.chapters.firstOrNull { it.id == chapterId } ?: return true
            val next = _works.value.mapNotNull { work ->
                if (work.manga.key != mangaId) {
                    work
                } else {
                    val remaining = work.chapters.filterNot { it.id == chapterId }
                    if (remaining.isEmpty()) null else work.copy(chapters = remaining)
                }
            }
            if (!persist(next)) return false
            _works.value = next
            deleteDirectory(target.localPath)
            return true
        }
    }

    override fun deleteChapters(mangaId: String, chapterIds: Set<String>): Boolean {
        if (ReaderSession.isReading(mangaId)) return false
        if (chapterIds.isEmpty()) return true
        synchronized(lock) {
            val existing = _works.value.firstOrNull { it.manga.key == mangaId } ?: return true
            val removed = existing.chapters.filter { it.id in chapterIds }
            val next = _works.value.mapNotNull { work ->
                if (work.manga.key != mangaId) {
                    work
                } else {
                    val remaining = work.chapters.filterNot { it.id in chapterIds }
                    if (remaining.isEmpty()) null else work.copy(chapters = remaining)
                }
            }
            if (!persist(next)) return false
            _works.value = next
            removed.forEach { deleteDirectory(it.localPath) }
            return true
        }
    }

    override fun deleteWork(mangaId: String): Boolean {
        if (ReaderSession.isReading(mangaId)) return false
        synchronized(lock) {
            val existing = _works.value.firstOrNull { it.manga.key == mangaId } ?: return true
            val next = _works.value.filterNot { it.manga.key == mangaId }
            if (!persist(next)) return false
            _works.value = next
            existing.chapters.forEach { deleteDirectory(it.localPath) }
            return true
        }
    }

    private fun mergeMetadata(stored: Manga, incoming: Manga): Manga = incoming.copy(
        altTitle = incoming.altTitle ?: stored.altTitle,
        description = incoming.description ?: stored.description,
        genres = if (incoming.genres.isEmpty()) stored.genres else incoming.genres,
        status = if (incoming.status == MangaStatus.UNKNOWN) stored.status else incoming.status,
        rating = incoming.rating ?: stored.rating,
        author = incoming.author ?: stored.author,
        artist = incoming.artist ?: stored.artist,
        year = incoming.year ?: stored.year,
        originalLanguage = incoming.originalLanguage ?: stored.originalLanguage,
        coverUrl = incoming.coverUrl ?: stored.coverUrl,
        sourceName = incoming.sourceName ?: stored.sourceName,
        url = incoming.url ?: stored.url,
    )

    private fun persist(works: List<StoredWork>): Boolean =
        AtomicFileWriter.write(file, encode(works))

    private fun encode(works: List<StoredWork>): String {
        val array = JSONArray()
        works.forEach { work ->
            array.put(
                JSONObject().apply {
                    put("manga", work.manga.toJson())
                    put("chapters", JSONArray().apply {
                        work.chapters.forEach { put(it.toJson()) }
                    })
                    work.localCoverPath?.let { put("localCoverPath", it) }
                    put("savedAt", work.savedAt)
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<StoredWork> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val manga = json.optJSONObject("manga")?.toManga() ?: return@mapNotNull null
            if (manga.id.isBlank() || manga.sourceId.isBlank()) return@mapNotNull null
            val chapters = json.optJSONArray("chapters")?.let { list ->
                (0 until list.length()).mapNotNull { position ->
                    list.optJSONObject(position)?.toChapter()
                }
            }.orEmpty()
            StoredWork(
                manga = manga,
                chapters = chapters,
                localCoverPath = json.optString("localCoverPath").takeIf { it.isNotBlank() },
                savedAt = json.optLong("savedAt"),
            )
        }
    }

    private fun Chapter.isAvailable(): Boolean {
        if (downloadStatus != ChapterDownloadStatus.DOWNLOADED) return false
        val path = localPath ?: return false
        return ChapterFiles.isComplete(File(path))
    }

    private fun deleteDirectory(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).deleteRecursively() }
    }

    private companion object {
        const val RECORDS = "local_library.json"
    }
}
