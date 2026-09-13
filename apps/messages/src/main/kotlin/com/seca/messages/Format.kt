package com.seca.messages

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formats are built on each call rather than kept aside, so they follow the
// phone's language and 12/24-hour setting even when those change.

private fun zonedOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

/** The time of day, in the phone's 12- or 24-hour setting. */
internal fun timeOf(context: Context, millis: Long): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return zonedOf(millis).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** In the list: the time today, the weekday this week, else the date. */
internal fun shortDateOf(context: Context, millis: Long): String {
    val date = zonedOf(millis)
    val today = LocalDate.now()
    val day = date.toLocalDate()
    val locale = Locale.getDefault()
    return when {
        day == today -> timeOf(context, millis)
        day.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEE", locale))
        day.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}

/** The sections of the conversation list. */
internal fun periodOf(millis: Long): String {
    val day = zonedOf(millis).toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> "Aujourd'hui"
        day == today.minusDays(1) -> "Hier"
        day.isAfter(today.minusDays(7)) -> "Cette semaine"
        day.isAfter(today.minusDays(31)) -> "Ce mois-ci"
        else -> "Plus ancien"
    }
}

/** The separator between the days of a conversation. */
internal fun dayTitleOf(millis: Long): String {
    val day = zonedOf(millis).toLocalDate()
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    return when (day) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
}

internal fun sameDay(a: Long, b: Long): Boolean = zonedOf(a).toLocalDate() == zonedOf(b).toLocalDate()
