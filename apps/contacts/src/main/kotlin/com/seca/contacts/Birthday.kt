package com.seca.contacts

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import android.text.format.DateFormat
import com.seca.core.model.uiLocale

/**
 * A birthday as people read it. The provider keeps "1990-05-12", or
 * "--05-12" for a day without a year; anything else is shown as it was saved.
 *
 * The format is built here rather than kept aside, so it follows the phone's
 * language even when that changes while the app is running.
 */
internal fun formatBirthday(raw: String): String {
    val text = raw.trim()
    val locale = uiLocale()
    return runCatching {
        // Day and month in the order the language writes them: "12 mai" in French, "May 12" in English.
        if (text.startsWith("--")) {
            MonthDay.parse(text).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale))
        } else {
            LocalDate.parse(text).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMMyyyy"), locale))
        }
    }.getOrDefault(text)
}
