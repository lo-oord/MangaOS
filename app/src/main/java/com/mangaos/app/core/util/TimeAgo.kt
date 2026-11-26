package com.mangaos.app.core.util

import android.content.Context
import com.mangaos.app.R
import java.util.concurrent.TimeUnit

/** Short, calm relative timestamps used below the cards on the Latest screen. */
fun relativeTime(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> context.appString(R.string.time_just_now)
        minutes < 60 -> context.appString(R.string.time_minutes_ago, minutes.toInt())
        hours < 2 -> context.appString(R.string.time_hour_ago)
        hours < 24 -> context.appString(R.string.time_hours_ago, hours.toInt())
        days < 2 -> context.appString(R.string.time_yesterday)
        days < 14 -> context.appString(R.string.time_days_ago, days.toInt())
        days < 60 -> context.appString(R.string.time_weeks_ago, (days / 7).toInt())
        else -> context.appString(R.string.time_months_ago, (days / 30).toInt())
    }
}
