package com.mangaos.app.data.download

import android.content.Context
import java.io.File
import java.io.IOException

/** Why the storage layer refused to start a chapter. Each value has its own user message. */
enum class StorageFailure {
    /** The volume itself reports less space than a chapter needs. */
    NO_SPACE,
    /** No usable directory (removed, unmounted, external storage unavailable). */
    UNAVAILABLE,
    /** The directory exists but is not writable. */
    READ_ONLY,
    /** The chapter directory could not be created. */
    CANNOT_CREATE,
    /** A write into an existing directory failed. */
    CANNOT_WRITE,
}

class StorageException(val failure: StorageFailure) : IOException(failure.name)

/** What the UI needs to explain the storage state without guessing. */
data class StorageStatus(
    val location: String,
    /** Free bytes of the volume; 0 means Android could not report it (unknown, not full). */
    val freeBytes: Long,
    val writable: Boolean,
    val failure: StorageFailure? = null,
)

/**
 * Chapter storage is app-specific external storage, which needs no runtime permission on any
 * supported API level and survives a restart. The important detail here is that free space is
 * read from the nearest existing ancestor: `File.usableSpace` returns 0 for a path that does
 * not exist yet, and treating that 0 as "no space" was reporting a full disk on every first
 * download.
 */
object StorageInspector {

    /** A single chapter needs far less, but a chapter is downloaded in one go. */
    const val MIN_FREE_BYTES = 16L * 1024L * 1024L

    private const val DIRECTORY = "downloads"

    fun root(context: Context): File {
        val external = context.getExternalFilesDir(null)?.let { File(it, DIRECTORY) }
        val legacy = File(context.filesDir, DIRECTORY)
        // Chapters downloaded by earlier builds live in internal storage: keep them readable
        // instead of orphaning files the user already has.
        if (external != null &&
            !external.exists() &&
            legacy.isDirectory &&
            legacy.list()?.isNotEmpty() == true
        ) {
            return legacy
        }
        return external ?: legacy
    }

    /** Free bytes of the volume holding [path]; 0 means unknown, never "full". */
    fun freeBytes(path: File): Long {
        var current: File? = path
        while (current != null) {
            if (current.exists()) {
                val usable = runCatching { current.usableSpace }.getOrDefault(0L)
                return if (usable > 0L) usable else 0L
            }
            current = current.parentFile
        }
        return 0L
    }

    fun ensureRoot(context: Context): File {
        val root = root(context)
        if (!root.isDirectory) {
            if (!root.mkdirs() && !root.isDirectory) {
                throw StorageException(StorageFailure.CANNOT_CREATE)
            }
        }
        if (!root.canWrite()) throw StorageException(StorageFailure.READ_ONLY)
        return root
    }

    /**
     * Writes a tiny probe and removes it again. Permission checks alone are not proof: the
     * only reliable way to know a directory accepts data is to write one file into it.
     */
    fun probe(directory: File, prefix: String = ".mangaos-probe"): StorageFailure? {
        val probe = File(directory, "$prefix-${System.nanoTime()}")
        return try {
            probe.outputStream().use { it.write(byteArrayOf(1, 2, 3)) }
            if (probe.length() != 3L) StorageFailure.CANNOT_WRITE else null
        } catch (_: Throwable) {
            StorageFailure.CANNOT_WRITE
        } finally {
            runCatching { probe.delete() }
        }
    }

    /** Full preflight: directory, writability, a real probe write, and free space. */
    fun check(context: Context): StorageStatus {
        val root = root(context)
        val free = freeBytes(root)
        val failure = try {
            val directory = ensureRoot(context)
            probe(directory)
        } catch (error: StorageException) {
            error.failure
        } catch (_: Throwable) {
            StorageFailure.UNAVAILABLE
        } ?: if (free in 1L until MIN_FREE_BYTES) StorageFailure.NO_SPACE else null
        return StorageStatus(
            location = root.absolutePath,
            freeBytes = free,
            writable = failure == null,
            failure = failure,
        )
    }
}
