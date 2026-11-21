package com.seca.core.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Dates in the calendar their reader actually uses (#83).
 *
 * The example is the one from the report: 10 November is 19 Aban, and it stays 19 Aban every
 * year even though the two calendars count their leap days differently. So what is held here
 * is not a format but an agreement — the day a Persian reader sees, and the age they would
 * give themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecaCalendarTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = Calendars.remember(context, SecaCalendar.Phone)

    @Test
    fun `the phone's own calendar is what nothing chosen means`() {
        assertEquals(SecaCalendar.Phone, Calendars.of(context))
        assertTrue(Calendars.isPhones(context))
    }

    @Test
    fun `a day in November is a day of Aban`() {
        Calendars.remember(context, SecaCalendar.Persian)

        val written = Calendars.text(context, LocalDate.of(2026, 11, 10), "dMMMMy")

        assertTrue(written, written.contains("Aban"))
        assertTrue(written, written.contains("19"))
        assertTrue(written, written.contains("1405"))
    }

    @Test
    fun `the same day in the Gregorian calendar says November`() {
        Calendars.remember(context, SecaCalendar.Gregorian)

        val written = Calendars.text(context, LocalDate.of(2026, 11, 10), "dMMMMy")

        assertTrue(written, written.contains("November"))
        assertTrue(written, written.contains("2026"))
    }

    @Test
    fun `an age turns on the day of the reader's own calendar`() {
        Calendars.remember(context, SecaCalendar.Persian)
        val born = LocalDate.of(1990, 11, 10)

        assertEquals(35, Calendars.yearsBetween(context, born, LocalDate.of(2026, 11, 9)))
        assertEquals(36, Calendars.yearsBetween(context, born, LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun `the next time round is the same day of that calendar, not of ours`() {
        Calendars.remember(context, SecaCalendar.Persian)
        val born = LocalDate.of(1990, 11, 10)

        val next = Calendars.nextTimeRound(context, born, today = LocalDate.of(2026, 6, 1))

        // Whatever Gregorian day it lands on, it is 19 Aban again.
        assertTrue(next.toString(), Calendars.text(context, next, "dMMMM").contains("19"))
        assertTrue(next.toString(), Calendars.text(context, next, "dMMMM").contains("Aban"))
        assertTrue(next.toString(), next.isAfter(LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `a birthday still to come this year is this year's`() {
        Calendars.remember(context, SecaCalendar.Gregorian)

        val next = Calendars.nextTimeRound(context, LocalDate.of(1990, 11, 10), today = LocalDate.of(2026, 1, 5))

        assertEquals(LocalDate.of(2026, 11, 10), next)
    }

    @Test
    fun `a birthday already past this year is next year's`() {
        Calendars.remember(context, SecaCalendar.Gregorian)

        val next = Calendars.nextTimeRound(context, LocalDate.of(1990, 11, 10), today = LocalDate.of(2026, 12, 1))

        assertEquals(LocalDate.of(2027, 11, 10), next)
    }
}
