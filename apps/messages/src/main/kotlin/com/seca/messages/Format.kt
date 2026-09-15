package com.seca.messages

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.seca.core.model.uiLocale

// Formats are built on each call rather than kept aside, so they follow the
// phone's language and 12/24-hour setting even when those change.

private fun zonedOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

/** The time of day, in the phone's 12- or 24-hour setting. */
internal fun timeOf(context: Context, millis: Long): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return zonedOf(millis).format(DateTimeFormatter.ofPattern(pattern, uiLocale()))
}

/** In the list: the time today, the weekday this week, else the date. */
internal fun shortDateOf(context: Context, millis: Long): String {
    val date = zonedOf(millis)
    val today = LocalDate.now()
    val day = date.toLocalDate()
    val locale = uiLocale()
    return when {
        day == today -> timeOf(context, millis)
        day.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEE", locale))
        day.year == today.year -> date.format(patternOf(locale, "dMMM"))
        else -> date.format(patternOf(locale, "dMMMyyyy"))
    }
}

/** The sections of the conversation list. */
internal fun periodOf(context: Context, millis: Long): String {
    val day = zonedOf(millis).toLocalDate()
    val today = LocalDate.now()
    return context.getString(
        when {
            day == today -> R.string.today
            day == today.minusDays(1) -> R.string.yesterday
            day.isAfter(today.minusDays(7)) -> R.string.this_week
            day.isAfter(today.minusDays(31)) -> R.string.this_month
            else -> R.string.older
        },
    )
}

/** The separator between the days of a conversation. */
internal fun dayTitleOf(context: Context, millis: Long): String {
    val day = zonedOf(millis).toLocalDate()
    val today = LocalDate.now()
    val locale = uiLocale()
    return when (day) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> day.format(patternOf(locale, if (day.year == today.year) "EEEEdMMMM" else "EEEEdMMMMyyyy"))
            .replaceFirstChar { it.titlecase(locale) }
    }
}

internal fun sameDay(a: Long, b: Long): Boolean = zonedOf(a).toLocalDate() == zonedOf(b).toLocalDate()

/** When a scheduled message leaves: "today at 19:00", "tomorrow at 08:00", "Mon, Sep 15 at 08:00". */
internal fun scheduleTimeOf(context: Context, millis: Long): String {
    val day = zonedOf(millis).toLocalDate()
    val today = LocalDate.now()
    val time = timeOf(context, millis)
    return when (day) {
        today -> context.getString(R.string.today_at, time)
        today.plusDays(1) -> context.getString(R.string.tomorrow_at, time)
        else -> context.getString(R.string.day_at, day.format(patternOf(uiLocale(), if (day.year == today.year) "EEEdMMM" else "EEEdMMMyyyy")), time)
    }
}

/** A date in the order the language writes it: "12 mai" in French, "May 12" in English. */
private fun patternOf(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
