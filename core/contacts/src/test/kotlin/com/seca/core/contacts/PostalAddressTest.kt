package com.seca.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An address held as its parts, and the guess that turns an old one-line address into them.
 *
 * The guess is the part worth testing hardest, because it runs over addresses people already
 * have. It is allowed to give up — a street with everything still in it is readable and whole —
 * and it is not allowed to invent, because a postcode put in the country field is worse than
 * no postcode at all.
 */
class PostalAddressTest {

    @Test
    fun `the line people read puts the postcode next to the town`() {
        val address = PostalAddress(street = "12 rue des Lilas", postcode = "21000", city = "Dijon", country = "France")

        assertEquals("12 rue des Lilas, 21000 Dijon, France", address.formatted)
    }

    @Test
    fun `missing parts leave no gaps behind them`() {
        assertEquals("12 rue des Lilas, Dijon", PostalAddress(street = "12 rue des Lilas", city = "Dijon").formatted)
        assertEquals("Dijon", PostalAddress(city = "Dijon").formatted)
        assertEquals("", PostalAddress().formatted)
    }

    @Test
    fun `an address with nothing in it says so`() {
        assertTrue(PostalAddress().isBlank)
        assertTrue(PostalAddress(street = "   ").isBlank)
        assertTrue(!PostalAddress(country = "France").isBlank)
    }

    @Test
    fun `a line Seca wrote itself is read back into its parts`() {
        val read = PostalAddress.from("12 rue des Lilas, 21000 Dijon, France")

        assertEquals("12 rue des Lilas", read.street)
        assertEquals("21000", read.postcode)
        assertEquals("Dijon", read.city)
        assertEquals("France", read.country)
    }

    @Test
    fun `a postcode that is not only digits is still a postcode`() {
        // Britain and the Netherlands, which is where a digits-only rule falls over.
        assertEquals("SW1A 1AA" to "London", PostalAddress.from("10 Downing St, SW1A 1AA London, UK").let { it.postcode to it.city })
        assertEquals("1012 AB" to "Amsterdam", PostalAddress.from("Dam 1, 1012 AB Amsterdam").let { it.postcode to it.city })
    }

    @Test
    fun `a line written across several lines is read the same way`() {
        val read = PostalAddress.from("12 rue des Lilas\n21000 Dijon\nFrance")

        assertEquals("12 rue des Lilas", read.street)
        assertEquals("Dijon", read.city)
        assertEquals("France", read.country)
    }

    @Test
    fun `a street with a comma in it stays in the street`() {
        val read = PostalAddress.from("Flat 3, 12 rue des Lilas, 21000 Dijon, France")

        assertEquals("Flat 3, 12 rue des Lilas", read.street)
        assertEquals("Dijon", read.city)
    }

    @Test
    fun `a town of two words is still a town`() {
        val read = PostalAddress.from("3 place du Marché, 93200 Saint Denis, France")

        assertEquals("93200", read.postcode)
        assertEquals("Saint Denis", read.city)
    }

    @Test
    fun `something that merely looks like a postcode is not one`() {
        // "Flat 3" is not how anybody writes a postcode, so the town is not taken from it.
        val read = PostalAddress.from("Rue Longue, Flat 3 London, UK")

        assertEquals("", read.postcode)
        assertEquals("", read.city)
    }

    @Test
    fun `a line it cannot read keeps everything rather than inventing`() {
        // Nothing here is unmistakably a postcode or a country, so nothing is claimed to be one.
        val read = PostalAddress.from("somewhere behind the church")

        assertEquals("somewhere behind the church", read.street)
        assertEquals("", read.postcode)
        assertEquals("", read.city)
        assertEquals("", read.country)
    }

    @Test
    fun `a country is never taken from an address of only two pieces`() {
        // "Dijon" here is the town, not the country: taking it would empty the address of a town.
        val read = PostalAddress.from("12 rue des Lilas, Dijon")

        assertEquals("", read.country)
        assertEquals("12 rue des Lilas, Dijon", read.street)
    }

    @Test
    fun `whatever it reads, the line comes back out the same`() {
        // The round trip that matters: nobody's address loses a piece by being understood.
        listOf(
            "12 rue des Lilas, 21000 Dijon, France",
            "Dam 1, 1012 AB Amsterdam",
            "somewhere behind the church",
            "Flat 3, 12 rue des Lilas, 21000 Dijon, France",
        ).forEach { line ->
            assertEquals(line, PostalAddress.from(line).formatted)
        }
    }
}
