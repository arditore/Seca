package com.seca.core.model

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.Calendar
import androidx.core.content.edit
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * The calendar dates are written in (#83).
 *
 * Asked for by someone who lives by the Persian calendar and was made to convert every date in
 * his head. The point of the setting is that a calendar is not a language: he reads English or
 * French screens and still counts the days in Solar Hijri, so taking the calendar from the
 * phone's language would give him the wrong one either way.
 *
 * [id] is the calendar's name in the Unicode standard, which is what the formatter is asked
 * for. [Phone] means whatever the phone's own locale implies, which for most of the world is
 * the Gregorian calendar and in Iran or Thailand is not.
 */
enum class SecaCalendar(val id: String?) {
    Phone(null),
    Gregorian("gregory"),
    Persian("persian"),
    Islamic("islamic-umalqura"),
    Buddhist("buddhist"),
    Hebrew("hebrew"),
    Indian("indian"),
    Japanese("japanese"),
    ;

    companion object {

        /** The one saved under this name, or [Phone] for a name nothing answers to. */
        fun named(name: String?): SecaCalendar =
            entries.firstOrNull { it.name == name } ?: Phone
    }
}

/**
 * Which calendar this phone writes dates in, and the dates themselves.
 *
 * The choice is the suite's, kept by Seca Contacts and shared with the other two apps like the
 * palette is. Each app also keeps its own copy, because a date is drawn in the first frame and
 * the shared one arrives a moment later: without the copy, every list would flash Gregorian
 * before settling.
 *
 * Dates go through ICU rather than `java.time`, which only knows the ISO calendar. ICU is in
 * Android itself, so this costs nothing and speaks every calendar listed above.
 */
object Calendars {

    @Volatile
    private var chosen: SecaCalendar? = null

    /** What this app is set to, reading the phone's copy the first time it is asked. */
    fun of(context: Context): SecaCalendar = chosen ?: read(context).also { chosen = it }

    /** Keeps the choice this app draws with; the suite's own copy is written by the caller. */
    fun remember(context: Context, calendar: SecaCalendar) {
        chosen = calendar
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, calendar.name) }
    }

    /** The language the screens are in, carrying the calendar the owner reads dates in. */
    fun locale(context: Context): Locale = localeOf(of(context))

    /**
     * Whether a date has to be picked in a calendar Android's own picker cannot draw.
     *
     * Android's picker is a Gregorian grid whatever the locale, so it serves the two choices
     * that are Gregorian in practice and nothing else.
     */
    fun needsItsOwnPicker(context: Context): Boolean =
        of(context) !in setOf(SecaCalendar.Phone, SecaCalendar.Gregorian)

    /** Whether dates are written the way Android itself would write them. */
    fun isPhones(context: Context): Boolean = of(context) == SecaCalendar.Phone

    /**
     * A moment, written out.
     *
     * [skeleton] is a Unicode date skeleton — "dMMM", "EEEEdMMMM" — and not a pattern: it says
     * which pieces are wanted and lets each language put them in its own order, which is how
     * "12 mai" and "May 12" come out of one line of code.
     */
    fun text(context: Context, millis: Long, skeleton: String): String =
        formatter(context, skeleton).format(Date(millis))

    /**
     * The same date, written in a calendar that is not the chosen one.
     *
     * For the chooser, which has to show what each calendar would say without first becoming
     * each of them in turn.
     */
    fun textIn(calendar: SecaCalendar, date: LocalDate, skeleton: String): String =
        DateFormat.getInstanceForSkeleton(tidy(skeleton), localeOf(calendar))
            .format(Date(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))

    /** The same, for a day with no time of day in it. */
    fun text(context: Context, date: LocalDate, skeleton: String): String =
        text(context, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), skeleton)

    /**
     * How many birthdays somebody has had, counted in the calendar they are read in.
     *
     * Not the same thing as counting Gregorian years. A date keeps the same day and month in
     * one calendar and drifts by a day in another, because their leap years fall differently,
     * so the day somebody turns a year older is a day of their own calendar. This counts the
     * years that calendar has turned since they were born, which is the number they would give
     * themselves.
     */
    fun yearsBetween(context: Context, birth: LocalDate, today: LocalDate): Int {
        val locale = locale(context)
        val from = calendarAt(locale, birth)
        val to = calendarAt(locale, today)
        var years = to.get(Calendar.YEAR) - from.get(Calendar.YEAR)
        val beforeTheDay = to.get(Calendar.MONTH) < from.get(Calendar.MONTH) ||
            (to.get(Calendar.MONTH) == from.get(Calendar.MONTH) && to.get(Calendar.DAY_OF_MONTH) < from.get(Calendar.DAY_OF_MONTH))
        if (beforeTheDay) years--
        return years
    }

    /**
     * The next day this yearly date comes round, in the calendar it is read in.
     *
     * A birthday is a day of its own calendar: 19 Aban is 19 Aban every year, and the
     * Gregorian day it lands on moves by one now and then. So the anniversary is worked out in
     * the chosen calendar and handed back as the ordinary day it falls on, which is what a
     * calendar app and a reminder both need.
     */
    fun nextTimeRound(context: Context, day: LocalDate, today: LocalDate): LocalDate {
        val locale = locale(context)
        val born = calendarAt(locale, day)
        val now = calendarAt(locale, today)
        val wanted = calendarAt(locale, today)
        wanted.set(Calendar.MONTH, born.get(Calendar.MONTH))
        // The day the card gives, or the last day of that month where this year is shorter.
        wanted.set(Calendar.DAY_OF_MONTH, minOf(born.get(Calendar.DAY_OF_MONTH), wanted.getActualMaximum(Calendar.DAY_OF_MONTH)))
        if (wanted.timeInMillis < startOfDay(now).timeInMillis) {
            wanted.add(Calendar.YEAR, 1)
            wanted.set(Calendar.DAY_OF_MONTH, minOf(born.get(Calendar.DAY_OF_MONTH), wanted.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return Date(wanted.timeInMillis).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /**
     * The year a day falls in, as the chosen calendar numbers it.
     *
     * Lists leave the year out when a date is from this year, and "this year" is not the same
     * stretch of days in every calendar: the Persian one turns in March. Asking the calendar
     * keeps a date from March reading as last year's in one app and this year's in another.
     */
    fun year(context: Context, date: LocalDate): Int =
        calendarAt(locale(context), date).get(Calendar.YEAR)

    /** An ICU calendar of the chosen kind, which is what a picker counts months and days with. */
    fun working(context: Context): Calendar = Calendar.getInstance(locale(context))

    /** The language with the chosen calendar written into it, which is all ICU needs to be told. */
    internal fun localeOf(calendar: SecaCalendar): Locale {
        val language = uiLocale()
        val id = calendar.id ?: return language
        return Locale.Builder().setLocale(language).setUnicodeLocaleKeyword("ca", id).build()
    }

    private fun formatter(context: Context, skeleton: String): DateFormat =
        DateFormat.getInstanceForSkeleton(tidy(skeleton), locale(context))

    /**
     * A skeleton as ICU wants it.
     *
     * "yyyy" is a padded year to a formatter and a skeleton field to ICU, which answers it with
     * a four-digit year even where the calendar's years are not four digits. The single "y"
     * means "the year, written as this calendar writes it".
     */
    private fun tidy(skeleton: String): String = skeleton.replace("yyyy", "y")

    private fun calendarAt(locale: Locale, day: LocalDate): Calendar =
        Calendar.getInstance(locale).apply {
            timeInMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

    private fun startOfDay(calendar: Calendar): Calendar = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun read(context: Context): SecaCalendar = SecaCalendar.named(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
    )

    private const val PREFS = "seca_calendar"
    private const val KEY = "calendar"
}
