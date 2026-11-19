package com.seca.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shapes a date arrives in, and the one shape the provider keeps (#86).
 *
 * A user tracking birthdays imported his address book out of Google Takeout and found every
 * birthday saved without a year showing on the contact's page as the characters `--0824`.
 * That is a perfectly ordinary vCard date; it was simply never read.
 */
class DateValueTest {

    @Test
    fun `a day and a month with no year and no dashes, which is what Google exports`() {
        assertEquals("--08-24", normalisedDate("--0824"))
    }

    @Test
    fun `a whole date with no dashes`() {
        assertEquals("1990-08-24", normalisedDate("19900824"))
    }

    @Test
    fun `the shapes the provider already keeps are left alone`() {
        assertEquals("1990-08-24", normalisedDate("1990-08-24"))
        assertEquals("--08-24", normalisedDate("--08-24"))
    }

    @Test
    fun `a time of day after the date is dropped`() {
        assertEquals("1990-08-24", normalisedDate("1990-08-24T00:00:00Z"))
    }

    @Test
    fun `whitespace around it is not part of the date`() {
        assertEquals("--08-24", normalisedDate("  --0824 "))
    }

    @Test
    fun `anything else is handed back as it was, rather than lost`() {
        assertEquals("sometime in August", normalisedDate("sometime in August"))
        assertEquals("", normalisedDate(""))
        // Eight digits that are not a date still keep their shape; it is not this function's
        // job to decide what is a real day, and the reader after it says so.
        assertEquals("9999-99-99", normalisedDate("99999999"))
    }

    @Test
    fun `a card written with the dashless form reads back as a date`() {
        val card = """
            BEGIN:VCARD
            VERSION:3.0
            N:Venski;Aliaksei;;;
            FN:Aliaksei Venski
            BDAY:--0824
            X-ANNIVERSARY:19900824
            TEL;CELL:06 12 34 56 78
            END:VCARD
        """.trimIndent()

        val read = VCard.parse(card).single()

        assertEquals("--08-24", read.birthday)
        assertEquals("1990-08-24", read.anniversary)
    }
}
