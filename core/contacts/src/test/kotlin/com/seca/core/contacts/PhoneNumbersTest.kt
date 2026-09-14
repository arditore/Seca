package com.seca.core.contacts

import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    private val france = PhoneNumbers("FR")
    private val usa = PhoneNumbers("US")

    @Test
    fun `national and international forms of a French number are the same line`() {
        assertEquals("+33612345678", france.key("06 12 34 56 78"))
        assertEquals(france.key("06 12 34 56 78"), france.key("+33 6 12 34 56 78"))
        assertEquals(france.key("0612345678"), france.key("0033612345678"))
    }

    @Test
    fun `numbers keep the shape they were written in`() {
        assertEquals("06 12 34 56 78", france.display("0612345678"))
        assertEquals("+33 6 12 34 56 78", france.display("+33612345678"))
    }

    @Test
    fun `an American SIM needs no +1 for American numbers`() {
        assertEquals("+12015550123", usa.key("(201) 555-0123"))
        assertEquals("(201) 555-0123", usa.display("2015550123"))
    }

    @Test
    fun `a foreign number shows its country code and its country`() {
        assertEquals("+1 201-555-0123", france.display("+12015550123"))
        assertEquals("US", france.countryOf("+1 201 555 0123")?.region)
        assertEquals(false, france.countryOf("+1 201 555 0123")?.isHome)
        assertEquals(true, france.countryOf("06 12 34 56 78")?.isHome)
    }

    @Test
    fun `the editor line names the country or says why the number is not recognised`() {
        assertEquals("🇫🇷 France", france.describe("06 12 34 56 78"))
        assertEquals("Numéro court", france.describe("3639"))
        // A long number still being typed is not a short one.
        assertEquals("Numéro incomplet ou inconnu", france.describe("0612"))
        assertNull(france.describe(""))
    }

    @Test
    fun `typed digits find a number whichever way it was saved`() {
        assertTrue(france.matchesDigits("+33 6 12 34 56 78", "0612"))
        assertTrue(france.matchesDigits("06 12 34 56 78", "+33612"))
        assertTrue(france.matchesDigits("06 12 34 56 78", "0033612"))
        assertFalse(france.matchesDigits("06 12 34 56 78", "0699"))
    }

    @Test
    fun `a caller in international form is matched to a contact saved in national form`() {
        val camille = SecaContact(1, "Camille", listOf(PhoneNumber("06 12 34 56 78")), false, null)
        val index = NumberIndex(listOf(camille), france)

        assertEquals(1L, index.find("+33612345678")?.contact?.id)
        assertNull(index.find("+33699999999"))
    }
}
