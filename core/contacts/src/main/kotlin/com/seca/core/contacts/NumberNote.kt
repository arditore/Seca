package com.seca.core.contacts

import android.content.res.Resources

/** What can be said about a number being written, before it is put into words. */
sealed interface NumberNote {
    data class Country(val country: NumberCountry) : NumberNote
    data object Short : NumberNote
    data object Unknown : NumberNote
}

/** The line under a number in the phone's language: "🇫🇷 France", "Short number"…; null for an empty field. */
fun PhoneNumbers.describe(raw: String, resources: Resources): String? = when (val note = note(raw)) {
    null -> null
    is NumberNote.Country -> "${note.country.flag} ${note.country.displayName}"
    NumberNote.Short -> resources.getString(R.string.contacts_number_short)
    NumberNote.Unknown -> resources.getString(R.string.contacts_number_unknown)
}
