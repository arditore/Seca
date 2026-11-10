package com.seca.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * How old a birthday makes somebody, and when it makes them nothing at all.
 *
 * The day is passed in rather than read from the clock, because a test that asks what today is
 * passes until the day it does not.
 */
class BirthdayAgeTest {

    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `a birthday already past this year counts the whole year`() {
        assertEquals(26, ageFrom("2000-01-01", today))
    }

    @Test
    fun `a birthday still to come this year does not`() {
        // The hard case: the year subtracts to 26, but they are 25 until December.
        assertEquals(25, ageFrom("2000-12-25", today))
    }

    @Test
    fun `a birthday today is the day it turns over`() {
        assertEquals(26, ageFrom("2000-11-10", today))
        assertEquals(25, ageFrom("2000-11-11", today))
    }

    @Test
    fun `a day and a month with no year says nothing`() {
        // Most of an address book: a contact saved from a phone rarely carries a year.
        assertNull(ageFrom("--05-12", today))
    }

    @Test
    fun `a year that cannot be a person's says nothing rather than something absurd`() {
        assertNull(ageFrom("1600-05-12", today))
        assertNull(ageFrom("2030-05-12", today))
    }

    @Test
    fun `anything that is not a date says nothing`() {
        assertNull(ageFrom("", today))
        assertNull(ageFrom("sometime in May", today))
    }

    @Test
    fun `a date written with spaces around it is still a date`() {
        assertEquals(26, ageFrom("  2000-01-01  ", today))
    }
}
