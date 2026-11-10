package com.seca.core.contacts

import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An address has to leave this phone in the components the format defines, and come back into
 * them.
 *
 * This was the whole of issue #43: Seca kept an address as one line, so writing it put the
 * street, the postcode, the town and the country together into the street component. Every
 * other client then showed one crammed line, and an address written properly elsewhere could
 * not be edited here a field at a time. A test that only round-trips through our own reader
 * would have passed the entire time, so the written line itself is checked here.
 */
class VCardAddressTest {

    /** A contact with nothing but a name, so each test says only what it is about. */
    private fun camilleWith(addresses: List<PostalAddress>) = VCardContact(
        givenName = "Camille",
        familyName = "",
        displayName = "Camille",
        phones = emptyList(),
        emails = emptyList(),
        addresses = addresses,
    )

    private val home = PostalAddress(
        street = "12 rue des Lilas",
        postcode = "21000",
        city = "Dijon",
        region = "Bourgogne",
        country = "France",
        type = StructuredPostal.TYPE_HOME,
    )

    @Test
    fun `the written line puts each part in its own component`() {
        val written = VCard.write(listOf(camilleWith(addresses = listOf(home))))

        // Box, extended, street, town, region, postcode, country — the order the format fixes.
        assertTrue(written, "ADR;TYPE=HOME:;;12 rue des Lilas;Dijon;Bourgogne;21000;France" in written)
    }

    @Test
    fun `a street with a comma in it is escaped rather than splitting the components`() {
        val flat = home.copy(street = "Flat 3, 12 rue des Lilas")
        val written = VCard.write(listOf(camilleWith(addresses = listOf(flat))))

        assertTrue(written, "Flat 3\\, 12 rue des Lilas;Dijon" in written)
        assertEquals(flat, VCard.parse(written).single().addresses.single())
    }

    @Test
    fun `an address goes out and comes back with every part where it was`() {
        val back = VCard.parse(VCard.write(listOf(camilleWith(addresses = listOf(home)))))

        assertEquals(home, back.single().addresses.single())
    }

    @Test
    fun `an address written by another client is read into its parts`() {
        // What Thunderbird puts on the wire, which Seca used to read as one blob.
        val card = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Camille
            ADR;TYPE=WORK:;;1 Hauptstrasse;Berlin;;10115;Deutschland
            END:VCARD
        """.trimIndent()

        val address = VCard.parse(card).single().addresses.single()

        assertEquals("1 Hauptstrasse", address.street)
        assertEquals("Berlin", address.city)
        assertEquals("10115", address.postcode)
        assertEquals("Deutschland", address.country)
        assertEquals(StructuredPostal.TYPE_WORK, address.type)
    }

    @Test
    fun `the extended component is kept rather than dropped on the floor`() {
        // Nobody in Seca can type one, and an address that arrives with one must not lose it.
        val card = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Camille
            ADR;TYPE=HOME:;Appartement 4;12 rue des Lilas;Dijon;;21000;France
            END:VCARD
        """.trimIndent()

        val address = VCard.parse(card).single().addresses.single()

        assertTrue(address.street, "Appartement 4" in address.street)
        assertTrue(address.street, "12 rue des Lilas" in address.street)
    }

    @Test
    fun `an address the owner named themselves keeps its name`() {
        val cottage = home.copy(type = CUSTOM_TYPE, label = "La campagne")
        val written = VCard.write(listOf(camilleWith(addresses = listOf(cottage))))

        assertTrue(written, "X-ABLabel:La campagne" in written)
        assertEquals(cottage, VCard.parse(written).single().addresses.single())
    }

    @Test
    fun `an empty address is not written at all`() {
        val written = VCard.write(listOf(camilleWith(addresses = listOf(PostalAddress()))))

        assertTrue(written, "ADR" !in written)
    }

    @Test
    fun `an address with only a town survives the trip`() {
        val sparse = PostalAddress(city = "Dijon", type = StructuredPostal.TYPE_HOME)

        val back = VCard.parse(VCard.write(listOf(camilleWith(addresses = listOf(sparse)))))

        assertEquals(sparse, back.single().addresses.single())
    }
}
