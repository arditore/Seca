package com.seca.contacts

import com.seca.core.contacts.PhoneNumbers
import com.seca.core.model.SecaContact
import java.text.Normalizer

private val Accents = Regex("\\p{M}+")

/** Lower-cases and strips accents, so "elo" finds "Élodie". */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Accents, "").lowercase()

/**
 * Matches on the name, on the nickname, or, once two digits are typed, on any
 * number however it was saved: "0612" finds "+33 6 12…" and the other way round.
 *
 * The nickname counts because it is what the person is actually called: somebody who
 * filed a contact as "Nan" looks for Nan, not for the name on her passport (#57).
 */
internal fun matchesQuery(contact: SecaContact, query: String, numbers: PhoneNumbers): Boolean {
    val normalized = normalizeForSearch(query.trim())
    if (normalized.isEmpty()) return true
    if (normalizeForSearch(contact.displayName).contains(normalized)) return true
    if (contact.nickname.isNotEmpty() && normalizeForSearch(contact.nickname).contains(normalized)) return true
    val organization = contact.organization
    if (organization != null && normalizeForSearch(organization).contains(normalized)) return true
    if (query.count(Char::isDigit) < 2) return false
    return contact.phoneNumbers.any { numbers.matchesDigits(it.raw, query) }
}
