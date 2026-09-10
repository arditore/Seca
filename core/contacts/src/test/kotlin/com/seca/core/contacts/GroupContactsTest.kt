package com.seca.core.contacts

import com.seca.core.model.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupContactsTest {

    @Test
    fun `attaches numbers to their contact and drops the same number written twice`() {
        val contacts = listOf(
            ContactRow(id = 1, name = "Camille Durand", starred = true, photoUri = null),
            ContactRow(id = 2, name = "Alex", starred = false, photoUri = null),
        )
        val phones = listOf(
            PhoneRow(contactId = 1, number = "06 12 34 56 78"),
            PhoneRow(contactId = 1, number = "0612345678"),
            PhoneRow(contactId = 1, number = "+33 7 11 22 33 44"),
        )

        val result = groupContacts(contacts, phones)

        assertEquals(listOf(1L, 2L), result.map { it.id })
        assertEquals(listOf("06 12 34 56 78", "+33 7 11 22 33 44"), result[0].phoneNumbers.map { it.raw })
        assertTrue(result[0].isFavorite)
        assertEquals(emptyList<PhoneNumber>(), result[1].phoneNumbers)
    }
}
