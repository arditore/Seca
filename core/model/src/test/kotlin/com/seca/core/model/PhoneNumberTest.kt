package com.seca.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `digits strips formatting characters`() {
        assertEquals("0612345678", PhoneNumber("06 12 34 56 78").digits)
        assertEquals("+33612345678", PhoneNumber("+33 6-12.34.56.78").digits)
    }

    @Test
    fun `digits keeps a parenthesised trunk prefix`() {
        // "(0)" is a written convention, not a dialable digit, but stripping it
        // would need country-specific rules. Keeping it is safe because matches()
        // compares only the trailing significant digits.
        assertEquals("+330612345678", PhoneNumber("+33 (0)6-12.34.56.78").digits)
    }

    @Test
    fun `matches sees through a parenthesised trunk prefix`() {
        assertTrue(
            PhoneNumber("+33 (0)6-12.34.56.78").matches(PhoneNumber("06 12 34 56 78"))
        )
    }

    @Test
    fun `matches compares the last nine significant digits`() {
        assertTrue(PhoneNumber("+33612345678").matches(PhoneNumber("06 12 34 56 78")))
    }

    @Test
    fun `matches rejects different numbers`() {
        assertFalse(PhoneNumber("+33612345678").matches(PhoneNumber("0687654321")))
    }

    @Test
    fun `matches rejects numbers too short to compare safely`() {
        assertFalse(PhoneNumber("1234").matches(PhoneNumber("5678")))
    }
}
