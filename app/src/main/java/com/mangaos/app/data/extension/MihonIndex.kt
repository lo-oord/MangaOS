package com.mangaos.app.data.extension

import com.mangaos.app.domain.model.ManagedExtension
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal protobuf wire reader. The index is a small, stable message, so a generated
 * protobuf runtime (and its compiler plugin) would be far more machinery than needed.
 */
internal class ProtoReader(private val data: ByteArray) {

    private var index = 0
    var field: Int = 0
        private set
    var wireType: Int = 0
        private set

    val hasMore: Boolean get() = index < data.size

    fun nextTag(): Boolean {
        if (!hasMore) return false
        val tag = readVarint().toInt()
        field = tag ushr 3
        wireType = tag and 0x7
        return true
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(index < data.size) { "Unexpected end of protobuf payload" }
            val byte = data[index++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            require(shift <= 63) { "Malformed protobuf varint" }
        }
    }

    fun readLengthDelimited(): ByteArray {
        val length = readVarint().toInt()
        require(length >= 0 && index + length <= data.size) { "Malformed protobuf length" }
        val out = data.copyOfRange(index, index + length)
        index += length
        return out
    }

    fun readString(): String = readLengthDelimited().toString(Charsets.UTF_8)

    fun skipCurrent() {
        when (wireType) {
            0 -> readVarint()
            1 -> index += 8
            2 -> readLengthDelimited()
            5 -> index += 4
            else -> throw IllegalArgumentException("Unsupported protobuf wire type $wireType")
        }
        require(index <= data.size) { "Unexpected end of protobuf payload" }
    }

    companion object {
        fun walk(payload: ByteArray, onField: (ProtoReader) -> Boolean) {
            val reader = ProtoReader(payload)
            while (reader.nextTag()) {
                if (!onField(reader)) reader.skipCurrent()
            }
        }
    }
}

/**
 * Reads the extension repository index that ships with MangaOS.
 *
 * The user only ever sees the resulting sources; the repository location and the index
 * format stay inside this file.
 */
object MihonIndexParser {

    const val INDEX_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"
    private const val ICON_BASE = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/icon/"
    private const val EXTENSION_FIELD = 101

    // The repository wraps its entries in a repeated field: ExtensionRepo.extensions (101)
    // is itself a message whose field 1 repeats for every extension. Older index revisions
    // emitted one 101 per entry directly, so both layouts are accepted.
    private const val EXTENSION_ENTRY_FIELD = 1

    fun parse(payload: ByteArray): List<ManagedExtension> = runCatching {
        parseOrThrow(payload)
    }.getOrDefault(emptyList())

    private fun parseOrThrow(payload: ByteArray): List<ManagedExtension> {
        val raw = gunzipIfNeeded(payload)
        val found = LinkedHashMap<String, ManagedExtension>()
        ProtoReader.walk(raw) { reader ->
            when (reader.field) {
                EXTENSION_FIELD -> {
                    val container = reader.readLengthDelimited()
                    val before = found.size
                    ProtoReader.walk(container) { entry ->
                        if (entry.field == EXTENSION_ENTRY_FIELD) {
                            parseExtension(entry.readLengthDelimited())
                                ?.let { found[it.pkgName] = it }
                            true
                        } else {
                            false
                        }
                    }
                    if (found.size == before) {
                        // Legacy layout: the 101 payload is a single extension message.
                        parseExtension(container)?.let { found[it.pkgName] = it }
                    }
                    true
                }
                else -> false
            }
        }
        return found.values.toList()
    }

    private fun gunzipIfNeeded(payload: ByteArray): ByteArray {
        val gzip = payload.size > 2 &&
            payload[0] == 0x1f.toByte() &&
            payload[1] == 0x8b.toByte()
        return if (gzip) {
            GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
        } else {
            payload
        }
    }

    private fun parseExtension(payload: ByteArray): ManagedExtension? {
        var name: String? = null
        var pkgName: String? = null
        var apkUrl: String? = null
        var iconUrl: String? = null
        var libVersion = ""
        var versionCode = 0L
        var versionName = ""
        var contentWarning = 0
        val languages = LinkedHashSet<String>()
        val baseUrls = mutableListOf<String>()

        ProtoReader.walk(payload) { reader ->
            when (reader.field) {
                1 -> name = reader.readString()
                2 -> pkgName = reader.readString()
                3 -> parseApk(reader.readLengthDelimited()) { apk, icon ->
                    if (apk != null) apkUrl = apk
                    if (icon != null) iconUrl = icon
                }
                4 -> libVersion = reader.readString()
                5 -> versionCode = reader.readVarint()
                6 -> versionName = reader.readString()
                7 -> contentWarning = reader.readVarint().toInt()
                8 -> parseSource(reader.readLengthDelimited()) { lang, baseUrl ->
                    if (lang.isNotBlank()) languages += lang
                    if (baseUrl.isNotBlank() && baseUrls.size < 4 && baseUrl !in baseUrls) {
                        baseUrls += baseUrl
                    }
                }
                else -> return@walk false
            }
            true
        }

        val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
        val resolvedPkg = pkgName?.takeIf { it.isNotBlank() } ?: return null
        val resolvedApk = apkUrl?.takeIf { it.startsWith("http") } ?: return null
        return ManagedExtension(
            name = resolvedName,
            pkgName = resolvedPkg,
            apkUrl = resolvedApk,
            iconUrl = iconUrl ?: "$ICON_BASE$resolvedPkg.png",
            libVersion = libVersion,
            versionCode = versionCode,
            versionName = versionName.ifBlank { libVersion },
            languages = languages.toList(),
            contentWarning = contentWarning,
            baseUrls = baseUrls,
        )
    }

    private fun parseApk(payload: ByteArray, onValue: (String?, String?) -> Unit) {
        var apk: String? = null
        var icon: String? = null
        ProtoReader.walk(payload) { reader ->
            when (reader.field) {
                1 -> apk = reader.readString()
                2 -> icon = reader.readString()
                else -> return@walk false
            }
            true
        }
        onValue(apk, icon)
    }

    private fun parseSource(payload: ByteArray, onValue: (String, String) -> Unit) {
        var lang = ""
        var baseUrl = ""
        ProtoReader.walk(payload) { reader ->
            when (reader.field) {
                3 -> lang = reader.readString()
                4 -> baseUrl = reader.readString()
                else -> return@walk false
            }
            true
        }
        onValue(lang, baseUrl)
    }
}
