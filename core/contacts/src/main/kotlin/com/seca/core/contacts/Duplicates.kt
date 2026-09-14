package com.seca.core.contacts

import com.seca.core.model.SecaContact
import java.text.Normalizer

private val Accents = Regex("\\p{Mn}+")
private val Spaces = Regex("\\s+")
private const val MIN_NAME_LETTERS = 3
private const val MIN_NUMBER_DIGITS = 6

/**
 * Contacts that are likely the same person: the same name once accents, case
 * and spaces are set aside, or a number in common however it was written.
 * Contacts linked through another one end up in the same group. Only groups
 * of two or more are returned, in the order of the list.
 */
fun findDuplicates(contacts: List<SecaContact>, numbers: PhoneNumbers): List<List<SecaContact>> {
    val parent = IntArray(contacts.size) { it }
    fun root(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }
    fun join(a: Int, b: Int) {
        parent[root(a)] = root(b)
    }

    val byName = HashMap<String, Int>()
    val byNumber = HashMap<String, Int>()
    contacts.forEachIndexed { index, contact ->
        nameKey(contact.displayName)?.let { key ->
            val seen = byName[key]
            if (seen == null) byName[key] = index else join(index, seen)
        }
        contact.phoneNumbers.forEach { phone ->
            val key = numbers.key(phone.raw)
            if (key.count(Char::isDigit) < MIN_NUMBER_DIGITS) return@forEach
            val seen = byNumber[key]
            if (seen == null) byNumber[key] = index else join(index, seen)
        }
    }
    return contacts.indices
        .groupBy(::root)
        .values
        .filter { it.size > 1 }
        .map { group -> group.map(contacts::get) }
}

/** Null for a name too short to tell two people apart, or one that is really a number. */
private fun nameKey(name: String): String? {
    val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Accents, "").lowercase().trim().replace(Spaces, " ")
    return plain.takeIf { text -> text.count(Char::isLetter) >= MIN_NAME_LETTERS }
}
