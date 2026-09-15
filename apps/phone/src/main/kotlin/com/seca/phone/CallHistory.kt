package com.seca.phone

import com.seca.core.contacts.NumberMatch
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.model.SecaContact
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.Context
import android.text.format.DateFormat
import com.seca.core.model.uiLocale

/** Consecutive calls with the same number on the same day, shown as one row. */
data class CallGroup(val calls: List<CallRecord>, val match: NumberMatch?) {
    val latest: CallRecord get() = calls.first()
}

/**
 * Splits a newest-first history into rows: a call joins the row above when it
 * has the same [keyOf] and falls on the same [dayOf].
 */
internal fun groupCalls(
    calls: List<CallRecord>,
    keyOf: (CallRecord) -> String,
    dayOf: (Long) -> Long,
): List<List<CallRecord>> {
    val groups = mutableListOf<MutableList<CallRecord>>()
    for (call in calls) {
        val previous = groups.lastOrNull()?.first()
        if (previous != null && keyOf(previous) == keyOf(call) && dayOf(previous.date) == dayOf(call.date)) {
            groups.last() += call
        } else {
            groups += mutableListOf(call)
        }
    }
    return groups
}

/** The day [millis] falls on in the phone's time zone, as a day count. */
internal fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

/** "Today", "Yesterday", else the date — with its year only when it is not this year's. */
internal fun dayLabel(context: Context, millis: Long, today: LocalDate = LocalDate.now()): String {
    val day = LocalDate.ofEpochDay(dayOf(millis))
    val locale = uiLocale()
    val pattern = DateFormat.getBestDateTimePattern(locale, if (day.year == today.year) "EEEEdMMMM" else "EEEEdMMMMyyyy")
    return when (day) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> day.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }
}

private val Accents = Regex("\\p{M}+")

/** A name spelled on a phone keypad: "Éric" becomes "3742". */
internal fun keypadDigitsOf(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD).replace(Accents, "").lowercase(Locale.ROOT).map { c ->
        when (c) {
            in 'a'..'c' -> '2'
            in 'd'..'f' -> '3'
            in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v' -> '8'
            in 'w'..'z' -> '9'
            else -> if (c.isDigit()) c else ' '
        }
    }.joinToString("")

/**
 * Whether [digits] typed on the keypad spell the start of one of the name's
 * words, or of the whole name: "742" finds "Éric Picard".
 */
internal fun matchesKeypadName(name: String, digits: String): Boolean {
    // Only 2 to 9 carry letters; two keys at least, or every name would match.
    if (digits.length < 2 || digits.any { it !in '2'..'9' }) return false
    val words = keypadDigitsOf(name).split(' ').filter { it.isNotEmpty() }
    return words.any { it.startsWith(digits) } || words.joinToString("").startsWith(digits)
}

/**
 * The contacts the keypad suggests for [typed]: numbers containing those
 * digits first — the exact number before all others — then names spelled by
 * them.
 */
internal fun keypadSuggestions(
    typed: String,
    contacts: List<SecaContact>,
    numbers: PhoneNumbers,
    limit: Int = 4,
): List<NumberMatch> {
    if (typed.count(Char::isDigit) < 2) return emptyList()
    val typedKey = numbers.key(typed)
    val byNumber = contacts
        .flatMap { contact ->
            contact.phoneNumbers.filter { numbers.matchesDigits(it.raw, typed) }.map { NumberMatch(contact, it) }
        }
        .sortedByDescending { numbers.key(it.number.raw) == typedKey }
    val found = byNumber.map { it.contact.id }.toSet()
    val byName = contacts
        .filter { it.id !in found && matchesKeypadName(it.displayName, typed) }
        .mapNotNull { contact -> contact.phoneNumbers.firstOrNull()?.let { NumberMatch(contact, it) } }
    return (byNumber + byName).distinctBy { it.contact.id }.take(limit)
}
