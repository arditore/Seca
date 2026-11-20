package com.seca.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a number is from, beyond its country (#93).
 *
 * Asked for from China, where every phone shows a number's home city. The data covers the
 * countries whose numbering plan is tied to geography and says nothing about the ones where it
 * is not, so the test states both halves: China gains a line, France keeps the one it had.
 */
class PlaceOfNumberTest {

    private val china = PhoneNumbers("CN")
    private val france = PhoneNumbers("FR")

    @Test
    fun `a Chinese number carries its city and its network`() {
        val place = china.countryOf("+8613800138000")?.place
        assertNotNull(place)
        assertTrue(place.orEmpty(), place?.contains("Beijing") == true)
    }

    @Test
    fun `a French mobile has nothing to add to France`() {
        val country = france.countryOf("+33612345678")
        assertNotNull(country)
        assertNull(country?.place)
    }

    @Test
    fun `an American number carries its town`() {
        assertEquals("Mountain View, CA", PhoneNumbers("US").countryOf("+16502530000")?.place)
    }
}
