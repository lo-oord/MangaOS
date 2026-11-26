package com.mangaos.app.data.download

import com.mangaos.app.data.local.AtomicFileWriter
import org.json.JSONArray
import java.io.File

/**
 * One chapter is a directory of page files plus a manifest. The manifest is the single source
 * of page order, and a chapter only counts as downloaded when every page it lists exists and
 * has content, so a record can never point at a half-written chapter.
 */
object ChapterFiles {

    const val MANIFEST = "manifest.json"

    fun writeManifest(directory: File, names: List<String>): Boolean =
        AtomicFileWriter.write(File(directory, MANIFEST), JSONArray(names).toString())

    fun pageNames(directory: File): List<String> = runCatching {
        val manifest = File(directory, MANIFEST)
        if (!manifest.exists()) return emptyList()
        val array = JSONArray(manifest.readText())
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun pageFiles(directory: File): List<File> = pageNames(directory).map { File(directory, it) }

    fun pages(directory: File): List<String> = pageFiles(directory)
        .filter { it.exists() && it.length() > 0L }
        .map { it.absolutePath }

    fun isComplete(directory: File): Boolean {
        val names = pageNames(directory)
        if (names.isEmpty()) return false
        return names.all { name ->
            val file = File(directory, name)
            file.exists() && file.isFile && file.length() > 0L
        }
    }
}
