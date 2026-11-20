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
 *
 * Where the numbering plan says more than the country, it is said: a Chinese number reads as its
 * city and province, an American one as its town and state (#93). The country is kept beside it,
 * because a town on its own is only useful to whoever already knows which country it is in.
 */
/**
 * Where a number is from, as a line beside a name (#93).
 *
 * The town and the country where the numbering plan says that much, the country alone where it
 * does not, and nothing at all for a number from home with nothing more to say: a French phone
 * does not need telling that a French number is French.
 */
fun PhoneNumbers.originOf(raw: String, resources: Resources): String? {
    val country = countryOf(raw) ?: return null
    val place = country.place
    return when {
        place != null -> resources.getString(R.string.contacts_number_place, place, country.displayName)
        country.isHome -> null
        else -> country.displayName
    }
}

fun PhoneNumbers.describe(raw: String, resources: Resources): String? = when (val note = note(raw)) {
    null -> null
    is NumberNote.Country -> note.country.place
        ?.let { resources.getString(R.string.contacts_number_place, it, note.country.displayName) }
        ?: note.country.displayName
    NumberNote.Short -> resources.getString(R.string.contacts_number_short)
    NumberNote.Unknown -> resources.getString(R.string.contacts_number_unknown)
}
