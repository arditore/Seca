package com.seca.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64

/** A contact as a vCard carries it. */
data class VCardContact(
    val givenName: String,
    val familyName: String,
    val displayName: String,
    val phones: List<ContactField>,
    val emails: List<ContactField>,
    val addresses: List<ContactField> = emptyList(),
    val organization: String = "",
    val jobTitle: String = "",
    val website: String = "",
    val birthday: String = "",
    val anniversary: String = "",
    val note: String = "",
    /** The contact's picture as a JPEG, or null; compared by identity, as pictures are. */
    val photo: ByteArray? = null,
    /** The Seca Contacts profile the contact is filed in, by name; empty for Principal. */
    val profile: String = "",
) {

    // A data class holding a ByteArray compares it by identity, which would make two
    // readings of the same card unequal. The bytes are compared properly here instead.
    override fun equals(other: Any?): Boolean = this === other ||
        (
            other is VCardContact &&
                givenName == other.givenName && familyName == other.familyName &&
                displayName == other.displayName && phones == other.phones &&
                emails == other.emails && addresses == other.addresses &&
                organization == other.organization && jobTitle == other.jobTitle &&
                website == other.website && birthday == other.birthday &&
                anniversary == other.anniversary && note == other.note &&
                profile == other.profile && photo.contentEquals(other.photo)
            )

    override fun hashCode(): Int {
        var result = givenName.hashCode()
        listOf(familyName, displayName, organization, jobTitle, website, birthday, anniversary, note, profile)
            .forEach { result = HASH_STEP * result + it.hashCode() }
        listOf(phones, emails, addresses).forEach { result = HASH_STEP * result + it.hashCode() }
        return HASH_STEP * result + photo.contentHashCode()
    }

    private companion object {
        const val HASH_STEP = 31
    }
}

/**
 * Reads and writes vCards, the file every contacts app exchanges: Seca's way
 * to back contacts up and move them to another phone, since it syncs nothing.
 *
 * Writes version 3.0. Reads 2.1, 3.0 and 4.0, including the quoted-printable
 * names older phones export for accented letters.
 *
 * A field the owner named themselves is written the way Apple's and Google's cards write it:
 * the property is put in a group, and a second line in the same group carries the name.
 *
 * ```
 * item1.TEL;TYPE=VOICE:+33123456789
 * item1.X-ABLabel:Holiday house
 * ```
 *
 * Reading is more forgiving than writing, since other apps settled this differently: a group's
 * label is used when there is one, and otherwise a type nobody recognises is taken to be the
 * name itself, which is what Android's own exporter produces.
 */
object VCard {

    private const val FOLD = 74

    fun write(contacts: List<VCardContact>): String = buildString {
        contacts.forEach { contact ->
            line("BEGIN:VCARD")
            line("VERSION:3.0")
            line("N:${escape(contact.familyName)};${escape(contact.givenName)};;;")
            line("FN:${escape(contact.displayName.ifBlank { nameOf(contact.givenName, contact.familyName) })}")
            // Groups are numbered across the whole card: a label belongs to one property only.
            var group = 0
            fun labelled(field: ContactField, body: () -> String) {
                val name = field.customLabel
                if (name == null) {
                    line(body())
                } else {
                    val prefix = "item${++group}."
                    line(prefix + body())
                    line("${prefix}X-ABLabel:${escape(name)}")
                }
            }
            contact.phones.forEach { field ->
                labelled(field) { "TEL;TYPE=${phoneTypeName(field.type)}:${escape(field.value)}" }
            }
            contact.emails.forEach { field ->
                labelled(field) { "EMAIL;TYPE=${emailTypeName(field.type)}:${escape(field.value)}" }
            }
            // An address is written whole, in the street field: Seca keeps it as one line.
            contact.addresses.forEach { field ->
                labelled(field) { "ADR;TYPE=${addressTypeName(field.type)}:;;${escape(field.value)};;;;" }
            }
            if (contact.organization.isNotBlank()) line("ORG:${escape(contact.organization)}")
            if (contact.jobTitle.isNotBlank()) line("TITLE:${escape(contact.jobTitle)}")
            if (contact.website.isNotBlank()) line("URL:${escape(contact.website)}")
            if (contact.birthday.isNotBlank()) line("BDAY:${escape(contact.birthday)}")
            // X-ANNIVERSARY is what Android writes and reads; version 3.0 has no property for it.
            if (contact.anniversary.isNotBlank()) line("X-ANNIVERSARY:${escape(contact.anniversary)}")
            if (contact.note.isNotBlank()) line("NOTE:${escape(contact.note)}")
            // The picture, base64 as every version of vCard carries it. Folded like any
            // other long line, which is what readers expect of it.
            contact.photo?.takeIf { it.isNotEmpty() }?.let {
                line("PHOTO;ENCODING=b;TYPE=JPEG:" + Base64.encode(it))
            }
            if (contact.profile.isNotBlank()) {
                // Other apps file the contact in a group from its categories. Seca reads back only its
                // own property, never the categories other apps export, such as "myContacts".
                line("CATEGORIES:${escape(contact.profile)}")
                line("X-SECA-PROFILE:${escape(contact.profile)}")
            }
            line("END:VCARD")
        }
    }

    fun parse(text: String): List<VCardContact> {
        val contacts = mutableListOf<VCardContact>()
        var card: Card? = null
        for (line in unfold(text)) {
            val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
            val parts = line.substring(0, colon).split(';')
            // "item1.TEL" is a grouped property: the group is how a label finds its field.
            val group = parts.first().substringBefore('.', "").takeIf { it.isNotEmpty() }
            val name = parts.first().substringAfter('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }
            // Kept as written as well as upper-cased: a type nobody recognises may be a name the
            // owner typed, and "Holiday house" must not come back shouting.
            val written = parts.drop(1).flatMap { param ->
                if (param.uppercase().startsWith("TYPE=")) param.drop("TYPE=".length).split(',') else listOf(param)
            }.map { it.trim('"') }
            val types = written.map { it.uppercase() }.toSet()
            val raw = line.substring(colon + 1)
            val value = if ("ENCODING=QUOTED-PRINTABLE" in params || "QUOTED-PRINTABLE" in params) decodeQuotedPrintable(raw) else raw
            when (name) {
                "BEGIN" -> if (value.equals("VCARD", ignoreCase = true)) card = Card()
                "END" -> if (value.equals("VCARD", ignoreCase = true)) {
                    card?.build()?.let(contacts::add)
                    card = null
                }
                "N" -> card?.let {
                    val fields = splitUnescaped(value)
                    it.family = fields.getOrElse(0) { "" }
                    it.given = fields.getOrElse(1) { "" }
                }
                "FN" -> card?.display = unescape(value)
                "TEL" -> card?.addPhone(group, unescape(value).removePrefix("tel:").trim(), types, written)
                "EMAIL" -> card?.addEmail(group, unescape(value).trim(), types, written)
                // The parts of an address, minus the post-office box, joined back into one line.
                "ADR" -> splitUnescaped(value).drop(2).filter { it.isNotBlank() }.joinToString(", ")
                    .takeIf { it.isNotBlank() }
                    ?.let { card?.addAddress(group, it, types, written) }
                "ORG" -> card?.let { current ->
                    val parts = splitUnescaped(value)
                    current.organization = parts.firstOrNull().orEmpty()
                    if (current.jobTitle.isEmpty()) current.jobTitle = parts.getOrElse(1) { "" }
                }
                "TITLE" -> card?.jobTitle = unescape(value)
                "URL" -> card?.website = unescape(value)
                "BDAY" -> card?.birthday = unescape(value).trim()
                "ANNIVERSARY", "X-ANNIVERSARY" -> card?.anniversary = unescape(value).trim()
                "NOTE" -> card?.note = unescape(value)
                // Version 4.0 writes it as a data URI; 2.1 and 3.0 as plain base64.
                "PHOTO", "LOGO" -> card?.let { current ->
                    if (current.photo == null) current.photo = decodeBase64(value.substringAfterLast(","))
                }
                "X-ABLABEL" -> if (group != null) card?.name(group, unescape(value).trim())
                "X-SECA-PROFILE" -> card?.profile = unescape(value)
            }
        }
        return contacts
    }

    private class Card {
        var given = ""
        var family = ""
        var display = ""
        var organization = ""
        var jobTitle = ""
        var website = ""
        var birthday = ""
        var anniversary = ""
        var note = ""
        var profile = ""
        var photo: ByteArray? = null
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
        val addresses = mutableListOf<ContactField>()

        /** Which list each group's field went into, so its label can be put back on it later. */
        private val grouped = mutableMapOf<String, Pair<MutableList<ContactField>, Int>>()

        /**
         * Adds a field, remembering its group, and taking an unrecognised type as its name.
         *
         * Android exports a custom label as the type itself, with or without an X- in front,
         * so a card from the phone's own contacts app keeps its "ABC" even though no
         * X-ABLabel line follows it.
         */
        fun addPhone(group: String?, value: String, types: Set<String>, written: List<String>) =
            add(phones, group, value, phoneTypeOf(types), written)

        fun addEmail(group: String?, value: String, types: Set<String>, written: List<String>) =
            add(emails, group, value, emailTypeOf(types), written)

        fun addAddress(group: String?, value: String, types: Set<String>, written: List<String>) =
            add(addresses, group, value, addressTypeOf(types), written)

        private fun add(into: MutableList<ContactField>, group: String?, value: String, type: Int, written: List<String>) {
            val named = written.firstOrNull { it.length > 2 && it.startsWith("X-", ignoreCase = true) }?.drop(2)
            into += if (named == null) {
                ContactField(null, value, type)
            } else {
                ContactField(null, value, CUSTOM_TYPE, named)
            }
            if (group != null) grouped[group] = into to into.lastIndex
        }

        /** Gives the field written in [group] the name that followed it. */
        fun name(group: String, label: String) {
            if (label.isEmpty()) return
            val (into, index) = grouped[group] ?: return
            into[index] = into[index].copy(type = CUSTOM_TYPE, label = label)
        }

        fun build(): VCardContact? {
            val shown = display.ifBlank { nameOf(given, family) }
            if (shown.isBlank() && phones.isEmpty() && emails.isEmpty()) return null
            return VCardContact(
                givenName = given,
                familyName = family,
                displayName = shown,
                phones = phones.filter { it.value.isNotBlank() },
                emails = emails.filter { it.value.isNotBlank() },
                addresses = addresses,
                organization = organization,
                jobTitle = jobTitle,
                website = website,
                birthday = birthday,
                anniversary = anniversary,
                note = note,
                profile = profile,
                photo = photo,
            )
        }
    }

    /** Base64 as a vCard writes it, with the line breaks and spaces folding left in it. */
    private fun decodeBase64(value: String): ByteArray? = runCatching {
        Base64.decode(value.filterNot(Char::isWhitespace))
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun nameOf(given: String, family: String) =
        listOf(given, family).filter(String::isNotBlank).joinToString(" ")

    /** Lines over 75 characters are folded: the rest continues after a line break and one space. */
    private fun StringBuilder.line(text: String) {
        var rest = text
        var continuation = false
        while (rest.length > FOLD) {
            // Never cut an emoji or any other character made of two code units in half.
            val cut = if (rest[FOLD - 1].isHighSurrogate()) FOLD - 1 else FOLD
            append(if (continuation) " " else "").append(rest, 0, cut).append("\r\n")
            rest = rest.substring(cut)
            continuation = true
        }
        append(if (continuation) " " else "").append(rest).append("\r\n")
    }

    /** Joins folded lines back, and the soft line breaks of quoted-printable values. */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            val previous = lines.lastOrNull()
            when {
                previous != null && (line.startsWith(" ") || line.startsWith("\t")) ->
                    lines[lines.lastIndex] = previous + line.substring(1)
                previous != null && previous.endsWith("=") && previous.uppercase().contains("QUOTED-PRINTABLE") ->
                    lines[lines.lastIndex] = previous.dropLast(1) + line
                else -> lines += line
            }
        }
        return lines.filter { it.isNotBlank() }
    }

    private fun escape(value: String) =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> append('\n')
                    else -> append(next)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    /** Splits on the semicolons that are not escaped, unescaping each part. */
    private fun splitUnescaped(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '\\' && i + 1 < value.length -> {
                    current.append(c).append(value[i + 1])
                    i += 2
                    continue
                }
                c == ';' -> {
                    parts += unescape(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        parts += unescape(current.toString())
        return parts
    }

    private fun decodeQuotedPrintable(value: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hex = if (c == '=' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun phoneTypeOf(types: Set<String>): Int = when {
        "CELL" in types && "WORK" in types -> Phone.TYPE_WORK_MOBILE
        "CELL" in types -> Phone.TYPE_MOBILE
        "FAX" in types && "WORK" in types -> Phone.TYPE_FAX_WORK
        "FAX" in types -> Phone.TYPE_FAX_HOME
        "WORK" in types -> Phone.TYPE_WORK
        "HOME" in types -> Phone.TYPE_HOME
        "PAGER" in types -> Phone.TYPE_PAGER
        "MAIN" in types || "PREF" in types -> Phone.TYPE_MAIN
        else -> Phone.TYPE_OTHER
    }

    private fun phoneTypeName(type: Int): String = when (type) {
        Phone.TYPE_MOBILE -> "CELL"
        Phone.TYPE_HOME -> "HOME"
        Phone.TYPE_WORK -> "WORK"
        Phone.TYPE_WORK_MOBILE -> "WORK,CELL"
        Phone.TYPE_FAX_WORK -> "WORK,FAX"
        Phone.TYPE_FAX_HOME -> "HOME,FAX"
        Phone.TYPE_PAGER -> "PAGER"
        Phone.TYPE_MAIN -> "MAIN"
        else -> "VOICE"
    }

    private fun addressTypeOf(types: Set<String>): Int = when {
        "WORK" in types -> StructuredPostal.TYPE_WORK
        "HOME" in types -> StructuredPostal.TYPE_HOME
        else -> StructuredPostal.TYPE_OTHER
    }

    private fun addressTypeName(type: Int): String = when (type) {
        StructuredPostal.TYPE_WORK -> "WORK"
        StructuredPostal.TYPE_HOME -> "HOME"
        else -> "OTHER"
    }

    private fun emailTypeOf(types: Set<String>): Int = when {
        "WORK" in types -> Email.TYPE_WORK
        "HOME" in types -> Email.TYPE_HOME
        else -> Email.TYPE_OTHER
    }

    private fun emailTypeName(type: Int): String = when (type) {
        Email.TYPE_WORK -> "WORK"
        Email.TYPE_HOME -> "HOME"
        else -> "INTERNET"
    }
}
