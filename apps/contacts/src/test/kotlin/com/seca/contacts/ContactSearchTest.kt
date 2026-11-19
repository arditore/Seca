package com.seca.contacts

import com.seca.core.contacts.PhoneNumbers
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {

    private val numbers = PhoneNumbers("FR")

    private val elodie = SecaContact(
        id = 1,
        displayName = "Élodie Martin",
        phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
        isFavorite = false,
        photoUri = null,
    )

    @Test
    fun `finds a name typed without its accents`() {
        assertTrue(matchesQuery(elodie, "elo", numbers))
    }

    @Test
    fun `finds a number from a few of its digits`() {
        assertTrue(matchesQuery(elodie, "1234", numbers))
    }

    @Test
    fun `finds a national number from its international form`() {
        assertTrue(matchesQuery(elodie, "+33 6 12", numbers))
    }

    @Test
    fun `does not match on a single digit`() {
        assertFalse(matchesQuery(elodie, "7", numbers))
    }

    @Test
    fun `finds a contact by the nickname they are actually called`() {
        val nan = elodie.copy(displayName = "Anne-Marie Lefebvre", nickname = "Nan")
        assertTrue(matchesQuery(nan, "nan", numbers))
        assertTrue(matchesQuery(nan, "Nan", numbers))
    }

    @Test
    fun `a nickname is matched without its accents too`() {
        assertTrue(matchesQuery(elodie.copy(nickname = "Loïc"), "loic", numbers))
    }

    @Test
    fun `a contact with no nickname is not matched by someone else's`() {
        assertFalse(matchesQuery(elodie, "nan", numbers))
    }
}
