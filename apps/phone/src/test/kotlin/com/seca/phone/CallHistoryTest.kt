package com.seca.phone

import com.seca.core.contacts.PhoneNumbers
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryTest {

    private val numbers = PhoneNumbers("FR")
    private val day = 24L * 60 * 60 * 1000

    private fun call(id: Long, number: String, date: Long) =
        CallRecord(id, number, CallType.Incoming, date, 60, Presentation.Allowed, null)

    @Test
    fun `consecutive calls from the same number on the same day become one row`() {
        val calls = listOf(
            call(1, "06 12 34 56 78", 10 * day + 300),
            call(2, "+33 6 12 34 56 78", 10 * day + 200),
            call(3, "07 00 00 00 00", 10 * day + 100),
            call(4, "06 12 34 56 78", 9 * day),
        )

        val groups = groupCalls(calls, keyOf = { numbers.key(it.number) }, dayOf = { it / day })

        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L)), groups.map { row -> row.map { it.id } })
    }

    @Test
    fun `keypad letters find a name, accents included`() {
        assertTrue(matchesKeypadName("Éric Picard", "742"))
        assertTrue(matchesKeypadName("Éric Picard", "374"))
        assertFalse(matchesKeypadName("Éric Picard", "743"))
        assertFalse(matchesKeypadName("Éric Picard", "7"))
    }

    @Test
    fun `the keypad suggests the exact number first, then names spelled by the digits`() {
        val eric = SecaContact(1, "Éric Picard", listOf(PhoneNumber("06 44 90 48 91")), false, null)
        val camille = SecaContact(2, "Camille", listOf(PhoneNumber("+33 6 12 34 56 78")), false, null)

        assertEquals(listOf(2L), keypadSuggestions("0612345678", listOf(eric, camille), numbers).map { it.contact.id })
        assertEquals(listOf(1L), keypadSuggestions("742", listOf(eric, camille), numbers).map { it.contact.id })
    }
}
