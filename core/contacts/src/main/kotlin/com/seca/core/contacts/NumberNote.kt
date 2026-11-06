package com.seca.core.contacts

import android.content.res.Resources

/** What can be said about a number being written, before it is put into words. */
sealed interface NumberNote {
    data class Country(val country: NumberCountry) : NumberNote
    data object Short : NumberNote
    data object Unknown : NumberNote
}

/**
 * The line under a number in the phone's language: "France", "Short number"…; null for an empty field.
 *
 * The country is named and not flagged. A flag is a picture of a state, which is not the same thing
 * as a dialling region - several regions share one, some have none, and the emoji is missing or
 * drawn as two letters on plenty of systems. The name is what the line is actually saying.
 */
fun PhoneNumbers.describe(raw: String, resources: Resources): String? = when (val note = note(raw)) {
    null -> null
    is NumberNote.Country -> note.country.displayName
    NumberNote.Short -> resources.getString(R.string.contacts_number_short)
    NumberNote.Unknown -> resources.getString(R.string.contacts_number_unknown)
}
