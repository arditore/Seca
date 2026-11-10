package com.seca.core.contacts

import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A text from a shop must not arrive under somebody else's name.
 *
 * Issue #40: every message from a bank, a shop or a carrier appeared under the name of one
 * contact. Senders written in letters carry no digits, were keyed by their digits, and so all
 * keyed to the empty string — where the first contact whose number also had no digits was
 * waiting to answer for every one of them.
 *
 * The reporter found it by deleting that contact and watching his bank get its name back.
 */
class SenderNameTest {

    private val numbers = PhoneNumbers("FR")

    private fun contact(id: Long, name: String, number: String) =
        SecaContact(
            id = id,
            displayName = name,
            phoneNumbers = listOf(PhoneNumber(number)),
            isFavorite = false,
            photoUri = null,
        )

    @Test
    fun `a contact with no digits does not answer for every sender written in letters`() {
        val index = NumberIndex(listOf(contact(1, "Globaltel SMS", "Globaltel SMS")), numbers)

        assertNull(index.find("MARIONNAUD"))
        assertNull(index.find("MyBank"))
        assertNull(index.find("CHRONOPOST"))
    }

    @Test
    fun `a sender saved as a contact still finds it`() {
        // The legitimate half: somebody who deliberately saved the sender keeps its name.
        val index = NumberIndex(listOf(contact(1, "My bank", "MYBANK")), numbers)

        assertEquals("My bank", index.find("MYBANK")?.contact?.displayName)
        assertEquals("My bank", index.find("mybank")?.contact?.displayName)
    }

    @Test
    fun `two senders written in letters do not collide with each other`() {
        val index = NumberIndex(
            listOf(contact(1, "Shop", "MARIONNAUD"), contact(2, "Courier", "CHRONOPOST")),
            numbers,
        )

        assertEquals("Shop", index.find("MARIONNAUD")?.contact?.displayName)
        assertEquals("Courier", index.find("CHRONOPOST")?.contact?.displayName)
    }

    @Test
    fun `a contact with a blank number answers for nobody at all`() {
        val index = NumberIndex(listOf(contact(1, "Half-saved", "   ")), numbers)

        assertNull(index.find(""))
        assertNull(index.find("ANYBODY"))
        assertNull(index.find("+33612345678"))
    }

    @Test
    fun `ordinary numbers are untouched by any of this`() {
        val index = NumberIndex(listOf(contact(1, "Camille", "+33 6 12 34 56 78")), numbers)

        assertEquals("Camille", index.find("+33612345678")?.contact?.displayName)
        assertEquals("Camille", index.find("06 12 34 56 78")?.contact?.displayName)
        assertNull(index.find("+33612345679"))
    }
}
