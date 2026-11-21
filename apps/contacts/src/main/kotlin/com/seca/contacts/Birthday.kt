package com.seca.contacts

import android.content.Context
import com.seca.core.contacts.normalisedDate
import com.seca.core.model.Calendars
import java.time.LocalDate
import java.time.MonthDay

/**
 * A birthday as people read it. The provider keeps "1990-05-12", or
 * "--05-12" for a day without a year; anything else is shown as it was saved.
 *
 * The same date with its dashes left out is read too, and not for tidiness: an address book
 * imported from Google arrives full of birthdays written "--0824", and every one of them was
 * showing as those six characters on the contact's page (#86). Importing puts them right from
 * now on, but the ones already saved are only put right here.
 *
 * Written in whichever calendar the owner reads dates in (#83). The date kept in the address
 * book stays Gregorian, as the provider and every other app expect; only the reading changes,
 * so a birthday saved on another phone still arrives as the right day.
 */
internal fun formatBirthday(context: Context, raw: String): String {
    val text = normalisedDate(raw)
    return runCatching {
        if (text.startsWith("--")) {
            // No year to convert from, so this year's is used: a day and a month on their own
            // do not name one day in another calendar, they name one per year.
            Calendars.text(context, MonthDay.parse(text).atYear(LocalDate.now().year), "dMMMM")
        } else {
            Calendars.text(context, LocalDate.parse(text), "dMMMMy")
        }
    }.getOrDefault(text)
}

/**
 * How old somebody is today, from a birthday that carries a year.
 *
 * Counted in the calendar the owner reads, which is the number the person themselves would
 * give: a Persian reader turns a year older on 19 Aban, and the Gregorian day that falls on
 * moves by one now and then (#83).
 *
 * Null where there is no year to count from, which is most of an address book: a contact saved
 * from a phone usually has a day and a month and nothing else. Also null for a year that cannot
 * be a person's — a date typed with a digit missing would otherwise announce a four-hundredth
 * birthday, and a date in the future a negative one.
 */
internal fun ageFrom(context: Context, raw: String, today: LocalDate = LocalDate.now()): Int? = runCatching {
    val text = normalisedDate(raw)
    if (text.startsWith("--")) return null
    Calendars.yearsBetween(context, LocalDate.parse(text), today).takeIf { it in 0..OLDEST }
}.getOrNull()

/** Older than anybody has been, so a year read wrong says nothing rather than something absurd. */
private const val OLDEST = 130

/**
 * The next day this yearly date falls on, for opening a calendar there.
 *
 * The year the date was saved with is the year it happened, not a day anybody wants to look at,
 * so what is offered is the coming one: today if it is today, otherwise the next time round.
 * Worked out in the calendar being read, so a birthday that is 19 Aban stays 19 Aban rather
 * than drifting with the Gregorian year. Null for a date that cannot be read at all, where
 * there is nowhere honest to go.
 */
internal fun nextOccurrence(context: Context, raw: String, today: LocalDate = LocalDate.now()): LocalDate? =
    runCatching {
        val text = normalisedDate(raw)
        val day = if (text.startsWith("--")) MonthDay.parse(text) else MonthDay.from(LocalDate.parse(text))
        // A 29th of February in a year that has no such day lands on the 28th rather than nowhere.
        val thisYear = day.atYear(today.year)
        Calendars.nextTimeRound(context, thisYear, today)
    }.getOrNull()
