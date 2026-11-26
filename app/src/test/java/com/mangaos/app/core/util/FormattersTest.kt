package com.mangaos.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `non positive sizes render as zero megabytes`() {
        assertEquals("0 MB", formatBytes(0))
        assertEquals("0 MB", formatBytes(-42))
    }

    @Test
    fun `megabyte sizes keep one decimal`() {
        assertEquals("1.0 MB", formatBytes(1_048_576))
        assertEquals("42.5 MB", formatBytes((42.5 * 1_048_576).toLong()))
        assertEquals("0.5 MB", formatBytes(524_288))
    }

    @Test
    fun `sizes from one gigabyte upward use gigabytes`() {
        assertEquals("1.0 GB", formatBytes(1_073_741_824))
        assertEquals("2.5 GB", formatBytes((2.5 * 1_073_741_824).toLong()))
    }

    @Test
    fun `percent is clamped and rounded down`() {
        assertEquals("0%", formatPercent(0f))
        assertEquals("45%", formatPercent(0.456f))
        assertEquals("100%", formatPercent(1f))
        assertEquals("100%", formatPercent(1.4f))
        assertEquals("0%", formatPercent(-0.3f))
    }
}
