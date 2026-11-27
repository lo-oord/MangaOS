package com.mangaos.app.core.util

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {

    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val utc = TimeZone.getTimeZone("UTC")

    private fun age(millisAgo: Long): TimeAgoValue =
        timeAgoValue(now - millisAgo, now, utc)

    @Test
    fun `under a minute is just now`() {
        assertEquals(TimeAgoBucket.JUST_NOW, age(30_000L).bucket)
    }

    @Test
    fun `minutes and the first hour keep their precision`() {
        assertEquals(TimeAgoValue(TimeAgoBucket.MINUTES, 7), age(7 * 60_000L))
        assertEquals(TimeAgoValue(TimeAgoBucket.HOUR), age(75 * 60_000L))
    }

    @Test
    fun `same calendar day reports hours`() {
        assertEquals(TimeAgoValue(TimeAgoBucket.HOURS, 3), age(3 * 3_600_000L))
    }

    @Test
    fun `previous calendar day is yesterday`() {
        assertEquals(TimeAgoBucket.YESTERDAY, age(24 * 3_600_000L).bucket)
    }

    @Test
    fun `recent days weeks and older dates`() {
        assertEquals(TimeAgoValue(TimeAgoBucket.DAYS, 3), age(3 * 86_400_000L))
        assertEquals(TimeAgoBucket.WEEK, age(8 * 86_400_000L).bucket)
        assertEquals(TimeAgoValue(TimeAgoBucket.WEEKS, 3), age(21 * 86_400_000L))
        assertEquals(TimeAgoBucket.DATE, age(120 * 86_400_000L).bucket)
    }

    @Test
    fun `a future timestamp never reports a negative age`() {
        assertEquals(TimeAgoBucket.JUST_NOW, age(-60_000L).bucket)
    }
}
