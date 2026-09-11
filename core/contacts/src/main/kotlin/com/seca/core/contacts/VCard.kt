package com.seca.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.io.ByteArrayOutputStream

/** A contact as a vCard carries it. */
data class VCardContact(
    val givenName: String,
    val familyName: String,
    val displayName: String,
    val phones: List<ContactField>,
    val emails: List<ContactField>,
)

/**
 * Reads and writes vCards, the file every contacts app exchanges: Seca's way
 * to back contacts up and move them to another phone, since it syncs nothing.
 *
 * Writes version 3.0. Reads 2.1, 3.0 and 4.0, including the quoted-printable
 * names older phones export for accented letters.
 */
object VCard {

    private const val FOLD = 74

    fun write(contacts: List<VCardContact>): String = buildString {
        contacts.forEach { contact ->
            line("BEGIN:VCARD")
            line("VERSION:3.0")
            line("N:${escape(contact.familyName)};${escape(contact.givenName)};;;")
            line("FN:${escape(contact.displayName.ifBlank { nameOf(contact.givenName, contact.familyName) })}")
            contact.phones.forEach { line("TEL;TYPE=${phoneTypeName(it.type)}:${escape(it.value)}") }
            contact.emails.forEach { line("EMAIL;TYPE=${emailTypeName(it.type)}:${escape(it.value)}") }
            line("END:VCARD")
        }
    }

    fun parse(text: String): List<VCardContact> {
        val contacts = mutableListOf<VCardContact>()
        var card: Card? = null
        for (line in unfold(text)) {
            val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
            val parts = line.substring(0, colon).split(';')
            // "item1.TEL" is a grouped property: the group does not matter here.
            val name = parts.first().substringAfter('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }
            val types = params.flatMap { param ->
                if (param.startsWith("TYPE=")) param.removePrefix("TYPE=").split(',') else listOf(param)
            }.map { it.trim('"') }.toSet()
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
                "TEL" -> card?.phones?.add(ContactField(null, unescape(value).removePrefix("tel:").trim(), phoneTypeOf(types)))
                "EMAIL" -> card?.emails?.add(ContactField(null, unescape(value).trim(), emailTypeOf(types)))
            }
        }
        return contacts
    }

    private class Card {
        var given = ""
        var family = ""
        var display = ""
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()

        fun build(): VCardContact? {
            val shown = display.ifBlank { nameOf(given, family) }
            if (shown.isBlank() && phones.isEmpty() && emails.isEmpty()) return null
            return VCardContact(given, family, shown, phones.filter { it.value.isNotBlank() }, emails.filter { it.value.isNotBlank() })
        }
    }

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
