package com.seca.core.contacts

/**
 * A date as Android's contacts provider keeps it, from any of the shapes a vCard carries it in.
 *
 * The provider wants `1990-08-24`, or `--08-24` for a day with no year, and that is what every
 * contacts app on the phone reads. A vCard may use either of those or the same date with the
 * dashes left out, which is what Google Contacts exports: a birthday saved there without a year
 * comes out as `--0824`, and importing that put the characters `--0824` straight into the
 * provider, where nothing could read them back as a date and the contact's page showed them as
 * a line of text (#86).
 *
 * Anything this does not recognise is handed back untouched. A date in a shape nobody here
 * anticipated is still the owner's data, and losing it would be worse than keeping it as it is.
 */
fun normalisedDate(raw: String): String {
    // A time of day after the date, which some exports add and the provider has no use for.
    val text = raw.trim().substringBefore('T').trim()
    return when {
        NO_YEAR_BASIC.matches(text) -> "--${text.substring(2, 4)}-${text.substring(4, 6)}"
        FULL_BASIC.matches(text) -> "${text.substring(0, 4)}-${text.substring(4, 6)}-${text.substring(6, 8)}"
        else -> text
    }
}

/** `--0824`, a day and a month with no year and no dashes. */
private val NO_YEAR_BASIC = Regex("""--\d{4}""")

/** `19900824`, a whole date with no dashes. */
private val FULL_BASIC = Regex("""\d{8}""")
