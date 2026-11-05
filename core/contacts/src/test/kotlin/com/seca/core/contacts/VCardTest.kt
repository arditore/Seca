package com.seca.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {

    @Test
    fun `a written vCard reads back the same, commas and semicolons included`() {
        val camille = VCardContact(
            givenName = "Camille",
            familyName = "Durand; fils",
            displayName = "Camille Durand, fils",
            phones = listOf(ContactField(null, "+33 6 12 34 56 78", Phone.TYPE_MOBILE), ContactField(null, "01 23 45 67 89", Phone.TYPE_WORK)),
            emails = listOf(ContactField(null, "camille@example.org", Email.TYPE_HOME)),
        )

        val back = VCard.parse(VCard.write(listOf(camille))).single()

        assertEquals(camille.copy(phones = camille.phones.map { it.copy(id = null) }), back)
    }

    @Test
    fun `reads an older phone's export with quoted-printable accents`() {
        val export = """
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Martin;=C3=89lodie;;;
            FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=C3=89lodie Martin
            TEL;CELL:06 12 34 56 78
            TEL;HOME:01 23 45 67 89
            EMAIL;HOME:elodie@example.org
            END:VCARD
        """.trimIndent()

        val elodie = VCard.parse(export).single()

        assertEquals("Élodie", elodie.givenName)
        assertEquals("Martin", elodie.familyName)
        assertEquals("Élodie Martin", elodie.displayName)
        assertEquals(listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME), elodie.phones.map { it.type })
        assertEquals("elodie@example.org", elodie.emails.single().value)
    }

    @Test
    fun `a contact's profile travels in Seca's own property, other apps' categories are ignored`() {
        val camille = VCardContact("Camille", "Durand", "Camille Durand", emptyList(), emptyList(), profile = "Famille")

        val written = VCard.write(listOf(camille))
        val google = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Camille Durand\r\nCATEGORIES:myContacts,starred\r\nEND:VCARD\r\n"

        assertEquals(true, "CATEGORIES:Famille" in written)
        assertEquals("Famille", VCard.parse(written).single().profile)
        assertEquals("", VCard.parse(google).single().profile)
    }

    @Test
    fun `long lines are folded and unfolded`() {
        val long = VCardContact("Anne-Marie-Charlotte-Joséphine", "de La Rochefoucauld-Montmorency", "", emptyList(), emptyList())

        val written = VCard.write(listOf(long))

        assertEquals(true, written.lines().all { it.length <= 76 })
        assertEquals(long.copy(displayName = "Anne-Marie-Charlotte-Joséphine de La Rochefoucauld-Montmorency"), VCard.parse(written).single())
    }

    @Test
    fun `a field the owner named themselves keeps its name across a card`() {
        val camille = VCardContact(
            givenName = "Camille",
            familyName = "Durand",
            displayName = "Camille Durand",
            phones = listOf(
                ContactField(null, "+33 6 12 34 56 78", Phone.TYPE_MOBILE),
                ContactField(null, "+33 4 11 22 33 44", CUSTOM_TYPE, "Holiday house"),
            ),
            emails = listOf(ContactField(null, "camille@example.org", CUSTOM_TYPE, "ABC")),
            anniversary = "2015-06-20",
        )

        val written = VCard.write(listOf(camille))
        val back = VCard.parse(written).single()

        assertEquals(camille, back)
        // The label travels in its own line, tied to the field by the group in front of both.
        assertTrue(written, "item1.X-ABLabel:Holiday house" in written)
        assertTrue(written, "X-ANNIVERSARY:2015-06-20" in written)
    }

    @Test
    fun `a label written as the type itself, the way Android exports it, is read back`() {
        val export = """
            BEGIN:VCARD
            VERSION:3.0
            N:Durand;Camille;;;
            FN:Camille Durand
            TEL;TYPE=X-Holiday house:+33411223344
            ANNIVERSARY:2015-06-20
            END:VCARD
        """.trimIndent()

        val card = VCard.parse(export).single()

        assertEquals(ContactField(null, "+33411223344", CUSTOM_TYPE, "Holiday house"), card.phones.single())
        assertEquals("2015-06-20", card.anniversary)
    }

    @Test
    fun `a group with no label of its own leaves its field alone`() {
        val export = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Camille Durand
            item1.TEL;TYPE=CELL:+33612345678
            END:VCARD
        """.trimIndent()

        assertEquals(ContactField(null, "+33612345678", Phone.TYPE_MOBILE), VCard.parse(export).single().phones.single())
    }
}
