package com.seca.contacts

import com.seca.core.model.SecaContact
import java.text.Normalizer

private val Accents = Regex("\\p{M}+")

/** Lower-cases and strips accents, so "elo" finds "Élodie". */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Accents, "").lowercase()

/** Matches on the name, or on the digits of any number once two digits are typed. */
internal fun matchesQuery(contact: SecaContact, query: String): Boolean {
    val normalized = normalizeForSearch(query.trim())
    if (normalized.isEmpty()) return true
    if (normalizeForSearch(contact.displayName).contains(normalized)) return true
    val digits = query.filter(Char::isDigit)
    return digits.length >= 2 &&
        contact.phoneNumbers.any { number -> number.digits.filter(Char::isDigit).contains(digits) }
}
