package com.mangaos.app.core.util

import android.content.Context
import com.mangaos.app.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Relative timestamps for real source dates. The bucket is computed from the publication
 * timestamp and the device's calendar, never from the time the request completed, and anything
 * older than a few weeks falls back to the device's own date format instead of a made-up age.
 */
enum class TimeAgoBucket { JUST_NOW, MINUTES, HOUR, HOURS, YESTERDAY, DAYS, WEEK, WEEKS, DATE }

data class TimeAgoValue(val bucket: TimeAgoBucket, val amount: Int = 0)

fun timeAgoValue(
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): TimeAgoValue {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = (diff / 60_000L).toInt()
    val hours = (diff / 3_600_000L).toInt()
    val days = (diff / 86_400_000L).toInt()

    if (minutes < 1) return TimeAgoValue(TimeAgoBucket.JUST_NOW)
    if (minutes < 60) return TimeAgoValue(TimeAgoBucket.MINUTES, minutes)
    if (hours < 2) return TimeAgoValue(TimeAgoBucket.HOUR)

    // Yesterday is a calendar question, not an arithmetic one: 23:50 → 00:10 is "yesterday".
    return when (calendarDaysBetween(timestamp, now, zone)) {
        0 -> TimeAgoValue(TimeAgoBucket.HOURS, hours)
        1 -> TimeAgoValue(TimeAgoBucket.YESTERDAY)
        in 2..6 -> TimeAgoValue(TimeAgoBucket.DAYS, days)
        in 7..13 -> TimeAgoValue(TimeAgoBucket.WEEK)
        in 14..34 -> TimeAgoValue(TimeAgoBucket.WEEKS, days / 7)
        else -> TimeAgoValue(TimeAgoBucket.DATE)
    }
}

fun relativeTime(
    context: Context,
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val value = timeAgoValue(timestamp, now)
    return when (value.bucket) {
        TimeAgoBucket.JUST_NOW -> context.appString(R.string.time_just_now)
        TimeAgoBucket.MINUTES -> context.appString(R.string.time_minutes_ago, value.amount)
        TimeAgoBucket.HOUR -> context.appString(R.string.time_hour_ago)
        TimeAgoBucket.HOURS -> context.appString(R.string.time_hours_ago, value.amount)
        TimeAgoBucket.YESTERDAY -> context.appString(R.string.time_yesterday)
        TimeAgoBucket.DAYS -> context.appString(R.string.time_days_ago, value.amount)
        TimeAgoBucket.WEEK -> context.appString(R.string.time_week_ago)
        TimeAgoBucket.WEEKS -> context.appString(R.string.time_weeks_ago, value.amount)
        TimeAgoBucket.DATE -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(timestamp))
    }
}

private fun calendarDaysBetween(from: Long, to: Long, zone: TimeZone): Int {
    val start = startOfDay(from, zone)
    val end = startOfDay(to, zone)
    return ((end - start) / 86_400_000L).toInt().coerceAtLeast(0)
}

private fun startOfDay(millis: Long, zone: TimeZone): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
