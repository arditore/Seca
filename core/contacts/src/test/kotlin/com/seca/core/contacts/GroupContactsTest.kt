package com.seca.core.contacts

import com.seca.core.model.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupContactsTest {

    @Test
    fun `attaches numbers to their contact and drops the same number written twice`() {
        val contacts = listOf(
            ContactRow(id = 1, lookupKey = "k1", name = "Camille Durand", starred = true, photoUri = null),
            ContactRow(id = 2, lookupKey = "k2", name = "Alex", starred = false, photoUri = null),
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
        assertEquals("k1", result[0].lookupKey)
        assertEquals(emptyList<PhoneNumber>(), result[1].phoneNumbers)
    }

    /**
     * The duplicate a user with 625 contacts kept seeing (#74), and the one the old rule let
     * through: two accounts holding one line, one in its national form and one international.
     * They are formatted the same before anybody sees them, so the card showed the number
     * twice, identically, and there was nothing on screen to explain it.
     */
    @Test
    fun `a line written nationally by one account and internationally by another is one number`() {
        val contacts = listOf(ContactRow(id = 1, lookupKey = "k1", name = "Adam", starred = false, photoUri = null))
        val phones = listOf(
            PhoneRow(contactId = 1, number = "+33 7 43 72 27 39", account = Place("com.google", "a@gmail.com")),
            PhoneRow(contactId = 1, number = "07 43 72 27 39", account = Place("org.telegram.messenger", "8345900371")),
        )

        val result = groupContacts(contacts, phones)

        assertEquals(listOf("+33 7 43 72 27 39"), result[0].phoneNumbers.map { it.raw })
    }

    /** Two genuinely different lines stay two, however alike they look. */
    @Test
    fun `numbers that differ in their last digits are kept apart`() {
        val contacts = listOf(ContactRow(id = 1, lookupKey = "k1", name = "Adam", starred = false, photoUri = null))
        val phones = listOf(
            PhoneRow(contactId = 1, number = "+33 7 43 72 27 39"),
            PhoneRow(contactId = 1, number = "+33 7 43 72 27 30"),
        )

        val result = groupContacts(contacts, phones)

        assertEquals(2, result[0].phoneNumbers.size)
    }

    /** Short codes are not lines and must not swallow each other. */
    @Test
    fun `two short codes stay two`() {
        val contacts = listOf(ContactRow(id = 1, lookupKey = "k1", name = "Service", starred = false, photoUri = null))
        val phones = listOf(
            PhoneRow(contactId = 1, number = "36180"),
            PhoneRow(contactId = 1, number = "38123"),
        )

        val result = groupContacts(contacts, phones)

        assertEquals(2, result[0].phoneNumbers.size)
    }
}
