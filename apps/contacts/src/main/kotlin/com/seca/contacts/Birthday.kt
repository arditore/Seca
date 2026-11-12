package com.seca.contacts

import java.time.LocalDate
import java.time.MonthDay
import java.time.Period
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

/**
 * How old somebody is today, from a birthday that carries a year.
 *
 * Null where there is no year to count from, which is most of an address book: a contact saved
 * from a phone usually has a day and a month and nothing else. Also null for a year that cannot
 * be a person's — a date typed with a digit missing would otherwise announce a four-hundredth
 * birthday, and a date in the future a negative one.
 */
internal fun ageFrom(raw: String, today: LocalDate = LocalDate.now()): Int? = runCatching {
    val text = raw.trim()
    if (text.startsWith("--")) return null
    Period.between(LocalDate.parse(text), today).years.takeIf { it in 0..OLDEST }
}.getOrNull()

/** Older than anybody has been, so a year read wrong says nothing rather than something absurd. */
private const val OLDEST = 130

/**
 * The next day this yearly date falls on, for opening a calendar there.
 *
 * The year the date was saved with is the year it happened, not a day anybody wants to look at,
 * so what is offered is the coming one: today if it is today, otherwise the next time round.
 * Null for a date that cannot be read at all, where there is nowhere honest to go.
 */
internal fun nextOccurrence(raw: String, today: LocalDate = LocalDate.now()): LocalDate? = runCatching {
    val text = raw.trim()
    val day = if (text.startsWith("--")) MonthDay.parse(text) else MonthDay.from(LocalDate.parse(text))
    // A 29th of February in a year that has no such day lands on the 28th rather than nowhere.
    val thisYear = day.atYear(today.year)
    if (thisYear < today) day.atYear(today.year + 1) else thisYear
}.getOrNull()
