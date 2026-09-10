package com.seca.contacts

import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {

    private val elodie = SecaContact(
        id = 1,
        displayName = "Élodie Martin",
        phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
        isFavorite = false,
        photoUri = null,
    )

    @Test
    fun `finds a name typed without its accents`() {
        assertTrue(matchesQuery(elodie, "elo"))
    }

    @Test
    fun `finds a number from a few of its digits`() {
        assertTrue(matchesQuery(elodie, "1234"))
    }

    @Test
    fun `does not match on a single digit`() {
        assertFalse(matchesQuery(elodie, "7"))
    }
}
