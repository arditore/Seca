package com.seca.contacts

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A birthday as people read it. The provider keeps "1990-05-12", or
 * "--05-12" for a day without a year; anything else is shown as it was saved.
 *
 * The format is built here rather than kept aside, so it follows the phone's
 * language even when that changes while the app is running.
 */
internal fun formatBirthday(raw: String): String {
    val text = raw.trim()
    val locale = Locale.getDefault()
    return runCatching {
        if (text.startsWith("--")) {
            MonthDay.parse(text).format(DateTimeFormatter.ofPattern("d MMMM", locale))
        } else {
            LocalDate.parse(text).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        }
    }.getOrDefault(text)
}
