package com.mangaos.app.data.local

import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Minimal JSON codecs for the on-device stores. Chapter images stay plain files; these records
 * only describe them, so a record is written after its files were verified.
 */

internal fun Manga.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("sourceId", sourceId)
    put("title", title)
    altTitle?.let { put("altTitle", it) }
    description?.let { put("description", it) }
    if (genres.isNotEmpty()) put("genres", JSONArray(genres))
    put("status", status.name)
    rating?.let { put("rating", it) }
    author?.let { put("author", it) }
    artist?.let { put("artist", it) }
    year?.let { put("year", it) }
    originalLanguage?.let { put("originalLanguage", it) }
    coverUrl?.let { put("coverUrl", it) }
    latestChapter?.let { put("latestChapter", it) }
    latestChapterId?.let { put("latestChapterId", it) }
    updatedAt?.let { put("updatedAt", it) }
    url?.let { put("url", it) }
    sourceName?.let { put("sourceName", it) }
}

internal fun JSONObject.toManga(): Manga = Manga(
    id = optString("id"),
    sourceId = optString("sourceId"),
    title = optString("title"),
    altTitle = optString("altTitle").takeIf { it.isNotBlank() },
    description = optString("description").takeIf { it.isNotBlank() },
    genres = optJSONArray("genres")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.orEmpty(),
    status = runCatching { MangaStatus.valueOf(optString("status")) }
        .getOrDefault(MangaStatus.UNKNOWN),
    rating = if (has("rating")) optDouble("rating") else null,
    author = optString("author").takeIf { it.isNotBlank() },
    artist = optString("artist").takeIf { it.isNotBlank() },
    year = if (has("year")) optInt("year") else null,
    originalLanguage = optString("originalLanguage").takeIf { it.isNotBlank() },
    coverUrl = optString("coverUrl").takeIf { it.isNotBlank() },
    latestChapter = optString("latestChapter").takeIf { it.isNotBlank() },
    latestChapterId = optString("latestChapterId").takeIf { it.isNotBlank() },
    updatedAt = if (has("updatedAt")) optLong("updatedAt") else null,
    url = optString("url").takeIf { it.isNotBlank() },
    sourceName = optString("sourceName").takeIf { it.isNotBlank() },
)

internal fun Chapter.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("mangaId", mangaId)
    put("number", number)
    title?.let { put("title", it) }
    publishedAt?.let { put("publishedAt", it) }
    put("downloadStatus", downloadStatus.name)
    sizeBytes?.let { put("sizeBytes", it) }
    localPath?.let { put("localPath", it) }
}

internal fun JSONObject.toChapter(): Chapter = Chapter(
    id = optString("id"),
    mangaId = optString("mangaId"),
    number = optString("number"),
    title = optString("title").takeIf { it.isNotBlank() },
    publishedAt = if (has("publishedAt")) optLong("publishedAt") else null,
    downloadStatus = runCatching { ChapterDownloadStatus.valueOf(optString("downloadStatus")) }
        .getOrDefault(ChapterDownloadStatus.NOT_DOWNLOADED),
    sizeBytes = if (has("sizeBytes")) optLong("sizeBytes") else null,
    localPath = optString("localPath").takeIf { it.isNotBlank() },
)

/** Writes a record atomically: a half-written file can never be read back as valid data. */
internal object AtomicFileWriter {

    fun read(file: File): String? = runCatching {
        if (file.exists() && file.length() > 0L) file.readText() else null
    }.getOrNull()

    fun write(file: File, content: String): Boolean = runCatching {
        val parent = file.parentFile ?: return@runCatching false
        if (!parent.exists() && !parent.mkdirs()) return@runCatching false
        val temporary = File(parent, "${file.name}.tmp")
        temporary.writeText(content)
        if (file.exists() && !file.delete()) {
            temporary.delete()
            return@runCatching false
        }
        if (temporary.renameTo(file)) {
            true
        } else {
            file.writeText(content)
            temporary.delete()
            true
        }
    }.getOrDefault(false)
}
