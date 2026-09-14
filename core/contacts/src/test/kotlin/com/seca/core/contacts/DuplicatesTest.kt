package com.seca.core.contacts

import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {

    private val numbers = PhoneNumbers("FR")

    private fun contact(id: Long, name: String, vararg phones: String) =
        SecaContact(id = id, displayName = name, phoneNumbers = phones.map(::PhoneNumber), isFavorite = false, photoUri = null)

    @Test
    fun `the same name written differently is one person`() {
        val groups = findDuplicates(listOf(contact(1, "Élodie Martin"), contact(2, "elodie  martin"), contact(3, "Paul Durand")), numbers)
        assertEquals(listOf(listOf(1L, 2L)), groups.map { group -> group.map { it.id } })
    }

    @Test
    fun `the same number written differently is one person`() {
        val groups = findDuplicates(listOf(contact(1, "Maman", "06 12 34 56 78"), contact(2, "Mère", "+33612345678")), numbers)
        assertEquals(listOf(listOf(1L, 2L)), groups.map { group -> group.map { it.id } })
    }

    @Test
    fun `short names and short codes are not enough`() {
        val groups = findDuplicates(listOf(contact(1, "Al", "3631"), contact(2, "Al", "3631"), contact(3, "Jo")), numbers)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `contacts linked through a third one form a single group`() {
        val contacts = listOf(
            contact(1, "Lucas Bernard", "0611111111"),
            contact(2, "Lucas B", "0611111111", "0622222222"),
            contact(3, "Travail Lucas", "+33 6 22 22 22 22"),
        )
        assertEquals(listOf(listOf(1L, 2L, 3L)), findDuplicates(contacts, numbers).map { group -> group.map { it.id } })
    }
}
