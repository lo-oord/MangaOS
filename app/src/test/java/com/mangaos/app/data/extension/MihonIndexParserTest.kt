package com.mangaos.app.data.extension

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The index is real protobuf, so the parser is tested against hand-built messages. */
class MihonIndexParserTest {

    @Test
    fun `parses a repository message into extensions`() {
        val repo = bytes(
            stringField(1, "Keiyoushi"),
            messageField(EXTENSION_FIELD, extensionMessage()),
        )

        val result = MihonIndexParser.parse(repo)

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals("Zerobyw", name)
            assertEquals("eu.kanade.tachiyomi.extension.zh.zerobyw", pkgName)
            assertEquals("https://example.test/app.apk", apkUrl)
            assertEquals("https://example.test/icon.png", iconUrl)
            assertEquals("1.6", libVersion)
            assertEquals(106002L, versionCode)
            assertEquals("1.6.2", versionName)
            assertEquals(listOf("zh", "en"), languages)
            assertEquals("all", language)
            assertEquals(listOf("https://example.test"), baseUrls)
        }
    }

    @Test
    fun `accepts a gzipped index`() {
        val source = bytes(
            stringField(1, "Keiyoushi"),
            messageField(EXTENSION_FIELD, extensionMessage()),
        )
        val gzipped = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(source) }
        }.toByteArray()

        assertEquals(1, MihonIndexParser.parse(gzipped).size)
    }

    @Test
    fun `skips entries without a usable apk url`() {
        val broken = bytes(
            stringField(1, "NoApk"),
            stringField(2, "eu.kanade.tachiyomi.extension.all.noapk"),
            messageField(4, bytes()),
        )
        val repo = bytes(messageField(EXTENSION_FIELD, broken))

        assertTrue(MihonIndexParser.parse(repo).isEmpty())
    }

    @Test
    fun `keeps the last entry when a package is listed twice`() {
        val newer = extensionMessage(versionCode = 200, versionName = "2.0")
        val older = extensionMessage(versionCode = 100, versionName = "1.0")
        val repo = bytes(
            messageField(EXTENSION_FIELD, older),
            messageField(EXTENSION_FIELD, newer),
        )

        val result = MihonIndexParser.parse(repo)

        assertEquals(1, result.size)
        assertEquals(200L, result.first().versionCode)
    }

    @Test
    fun `ignores unrelated top level fields and malformed payloads`() {
        val repo = bytes(
            stringField(2, "KEI"),
            messageField(EXTENSION_FIELD, extensionMessage()),
        )

        assertEquals(1, MihonIndexParser.parse(repo).size)
        assertTrue(MihonIndexParser.parse(byteArrayOf(0x0a, 0x7f)).isEmpty())
    }

    @Test
    fun `missing icon falls back to the repository icon path`() {
        val apk = bytes(stringField(1, "https://example.test/app.apk"))
        val entry = bytes(
            stringField(1, "NoIcon"),
            stringField(2, "eu.kanade.tachiyomi.extension.all.noicon"),
            messageField(3, apk),
        )

        val result = MihonIndexParser.parse(bytes(messageField(EXTENSION_FIELD, entry)))

        assertTrue(result.first().iconUrl!!.endsWith("/icon/eu.kanade.tachiyomi.extension.all.noicon.png"))
        assertNull(result.first().apkUrl.takeIf { it.isBlank() })
    }

    private fun extensionMessage(
        versionCode: Long = 106002,
        versionName: String = "1.6.2",
    ): ByteArray {
        val apk = bytes(
            stringField(1, "https://example.test/app.apk"),
            stringField(2, "https://example.test/icon.png"),
            stringField(3, "https://example.test/app.jar"),
        )
        val source = bytes(
            varintField(1, 42),
            stringField(2, "Zerobyw"),
            stringField(3, "zh"),
            stringField(4, "https://example.test"),
        )
        val sourceEnglish = bytes(
            varintField(1, 43),
            stringField(2, "Zerobyw"),
            stringField(3, "en"),
            stringField(4, "https://example.test"),
        )
        return bytes(
            stringField(1, "Zerobyw"),
            stringField(2, "eu.kanade.tachiyomi.extension.zh.zerobyw"),
            messageField(3, apk),
            stringField(4, "1.6"),
            varintField(5, versionCode),
            stringField(6, versionName),
            varintField(7, 2),
            messageField(8, source),
            messageField(8, sourceEnglish),
        )
    }

    private fun bytes(vararg parts: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        parts.forEach(output::write)
        return output.toByteArray()
    }

    private fun varint(value: Long): ByteArray {
        var remaining = value
        val output = ByteArrayOutputStream()
        while (true) {
            val byte = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining == 0L) {
                output.write(byte)
                return output.toByteArray()
            }
            output.write(byte or 0x80)
        }
    }

    private fun varintField(number: Int, value: Long): ByteArray =
        bytes(varint((number shl 3).toLong() or 0L), varint(value))

    private fun stringField(number: Int, value: String): ByteArray =
        messageField(number, value.toByteArray(Charsets.UTF_8))

    private fun messageField(number: Int, payload: ByteArray): ByteArray =
        bytes(varint((number shl 3).toLong() or 2L), varint(payload.size.toLong()), payload)

    private companion object {
        const val EXTENSION_FIELD = 101
    }
}
