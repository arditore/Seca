package com.seca.core.contacts

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.Phonenumber
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Understands phone numbers the way the phone's owner writes them.
 *
 * [homeRegion] is the country of the SIM (see [detectRegion]). A number
 * written without a country code is read as a number of that country: with a
 * French SIM, "06 95 86 61 33" and "+33 6 95 86 61 33" are the same line, and
 * with an American SIM "(201) 555-0123" needs no +1. Numbers keep the shape
 * their owner gave them — spaced out, never rewritten.
 *
 * Reading a number is slow next to drawing a frame, so each result is kept:
 * the contacts and the call history repeat the same numbers. [prepare] does
 * the reading ahead, off the main thread, so lists scroll without stalling.
 */
class PhoneNumbers(val homeRegion: String) {

    private val util = PhoneNumberUtil.getInstance()
    private val homeCountryCode = util.getCountryCodeForRegion(homeRegion)

    private val displays = ConcurrentHashMap<String, String>()
    private val keys = ConcurrentHashMap<String, String>()
    private val countries = ConcurrentHashMap<String, Optional<NumberCountry>>()
    private val forms = ConcurrentHashMap<String, List<String>>()

    /** Reads [raw] now so that later lookups are instant. Call it off the main thread. */
    fun prepare(raw: String) {
        display(raw)
        key(raw)
        countryOf(raw)
        formsOf(raw)
    }

    /**
     * The number spaced out in the shape it was written: national stays
     * national, international stays international. A foreign number always
     * shows its country code. Anything not understood is left as it is.
     */
    fun display(raw: String): String = displays.getOrPut(raw) {
        val number = parse(raw)?.takeIf(util::isValidNumber) ?: return@getOrPut raw.trim()
        val writtenInternational = raw.trimStart().let { it.startsWith("+") || it.startsWith("00") }
        val international = writtenInternational || number.countryCode != homeCountryCode
        util.format(number, if (international) PhoneNumberFormat.INTERNATIONAL else PhoneNumberFormat.NATIONAL)
    }

    /** Equal for every way of writing the same line: E.164 when the number is understood, else its digits. */
    fun key(raw: String): String = keys.getOrPut(raw) {
        val number = parse(raw)?.takeIf(util::isPossibleNumber) ?: return@getOrPut raw.filter(Char::isDigit)
        util.format(number, PhoneNumberFormat.E164)
    }

    /** E.164, the form the contacts provider matches callers on, or null when the number is not understood. */
    fun toE164(raw: String): String? =
        parse(raw)?.takeIf(util::isValidNumber)?.let { util.format(it, PhoneNumberFormat.E164) }

    /** The country of a valid number, or null. */
    fun countryOf(raw: String): NumberCountry? = countries.getOrPut(raw) {
        val number = parse(raw)?.takeIf(util::isValidNumber)
        val region = number?.let(util::getRegionCodeForNumber)
        Optional.ofNullable(
            region?.let {
                val name = Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.getDefault())
                NumberCountry(region = it, displayName = name, isHome = it == homeRegion)
            },
        )
    }.orElse(null)

    /** One line for the editor: the number's country, or why it is not recognised. Null for an empty field. */
    fun describe(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        countryOf(raw)?.let { return "${it.flag} ${it.displayName}" }
        return if (digits.length <= SHORT_NUMBER_DIGITS) "Numéro court" else "Numéro incomplet ou inconnu"
    }

    /**
     * Whether the [typed] digits appear in [raw] written any usual way — as
     * saved, national or international — so "0612" finds "+33 6 12…" and
     * "+33612" finds "06 12…".
     */
    fun matchesDigits(raw: String, typed: String): Boolean {
        val digits = typed.filter(Char::isDigit)
        // "00" is how an international number is dialled from France; the rest is the country code.
        val wanted = if (typed.trimStart().startsWith("00")) digits.removePrefix("00") else digits
        if (wanted.isEmpty()) return false
        return formsOf(raw).any { it.contains(wanted) }
    }

    /** Formats a number while it is being typed on the keypad. */
    fun formatTyping(typed: String): String {
        val formatter = util.getAsYouTypeFormatter(homeRegion)
        var formatted = ""
        typed.forEach { formatted = formatter.inputDigit(it) }
        return formatted
    }

    private fun formsOf(raw: String): List<String> = forms.getOrPut(raw) {
        val saved = raw.filter(Char::isDigit)
        val number = parse(raw) ?: return@getOrPut listOf(saved)
        listOf(
            saved,
            util.format(number, PhoneNumberFormat.E164).filter(Char::isDigit),
            util.format(number, PhoneNumberFormat.NATIONAL).filter(Char::isDigit),
        )
    }

    private fun parse(raw: String): Phonenumber.PhoneNumber? {
        if (raw.none(Char::isDigit)) return null
        return try {
            util.parse(raw, homeRegion)
        } catch (e: NumberParseException) {
            null
        }
    }

    companion object {
        private const val SHORT_NUMBER_DIGITS = 6

        /**
         * The SIM's country, then the network's, then the language's. The SIM
         * comes first: abroad, "06…" still means a French number to a French
         * SIM, and a French speaker living elsewhere gets their own country.
         */
        fun detectRegion(context: Context): String {
            val telephony = context.getSystemService(TelephonyManager::class.java)
            return listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
                .firstOrNull { it.isNotBlank() }
                ?.uppercase(Locale.ROOT)
                ?: "ZZ"
        }
    }
}

/** The country a number belongs to, its name in the phone's language, and whether it is the SIM's own. */
data class NumberCountry(val region: String, val displayName: String, val isHome: Boolean) {
    /** The flag emoji, made of the region's two regional-indicator letters. */
    val flag: String
        get() = region.uppercase(Locale.ROOT).map { Character.toChars(REGIONAL_A + (it - 'A')).concatToString() }
            .joinToString("")

    private companion object {
        const val REGIONAL_A = 0x1F1E6
    }
}
