package com.seca.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.junit.Assert.assertEquals
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
    fun `long lines are folded and unfolded`() {
        val long = VCardContact("Anne-Marie-Charlotte-Joséphine", "de La Rochefoucauld-Montmorency", "", emptyList(), emptyList())

        val written = VCard.write(listOf(long))

        assertEquals(true, written.lines().all { it.length <= 76 })
        assertEquals(long.copy(displayName = "Anne-Marie-Charlotte-Joséphine de La Rochefoucauld-Montmorency"), VCard.parse(written).single())
    }
}
