package com.seca.core.contacts

import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact

/** A contact and the number of theirs that matched. */
data class NumberMatch(val contact: SecaContact, val number: PhoneNumber)

/**
 * Finds which contact a number belongs to, however either was written.
 *
 * Lookups go by [PhoneNumbers.key] first, so "06 12 34 56 78" in the
 * directory matches a call from "+33612345678". A number saved in a shape the
 * SIM's country cannot read — a foreign number saved without its country
 * code — falls back on its last digits, as Android's own matching does, but
 * only when a single contact has them.
 */
class NumberIndex(contacts: List<SecaContact>, private val numbers: PhoneNumbers) {

    private val byKey = HashMap<String, NumberMatch>()
    private val bySuffix = HashMap<String, MutableList<NumberMatch>>()

    init {
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                val match = NumberMatch(contact, number)
                byKey.putIfAbsent(numbers.key(number.raw), match)
                suffixOf(number.raw)?.let { bySuffix.getOrPut(it) { mutableListOf() } += match }
            }
        }
    }

    fun find(raw: String): NumberMatch? {
        byKey[numbers.key(raw)]?.let { return it }
        val candidates = suffixOf(raw)?.let { bySuffix[it] }.orEmpty()
        return candidates.distinctBy { it.contact.id }.singleOrNull()
    }

    private fun suffixOf(raw: String): String? =
        raw.filter(Char::isDigit).takeIf { it.length >= SUFFIX_DIGITS }?.takeLast(SUFFIX_DIGITS)

    private companion object {
        const val SUFFIX_DIGITS = 8
    }
}
