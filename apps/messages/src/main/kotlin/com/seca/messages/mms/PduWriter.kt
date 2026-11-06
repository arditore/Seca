package com.seca.messages.mms

import java.io.ByteArrayOutputStream

/**
 * Writes the binary a multimedia message is sent as — the mirror of [PduReader].
 *
 * The encodings come from the WAP specification and are small but unforgiving: a length written
 * one byte too short and the carrier's server rejects the whole message with a number and no
 * explanation. Each one is a method here so that it is written the same way everywhere, and so
 * that the tests can read a message back with [PduReader] and get out what went in.
 */
internal class PduWriter {

    private val out = ByteArrayOutputStream()

    fun bytes(): ByteArray = out.toByteArray()

    val size: Int get() = out.size()

    fun byte(value: Int) {
        out.write(value and BYTE_MASK)
    }

    fun raw(value: ByteArray) {
        out.write(value, 0, value.size)
    }

    /** A number from 0 to 127, written in one byte with the top bit set. */
    fun shortInteger(value: Int) {
        byte(value or HIGH_BIT)
    }

    /** A length written over as many 7-bit groups as it needs, most significant first. */
    fun uintvar(value: Long) {
        val groups = ArrayDeque<Int>()
        var left = value
        do {
            groups.addFirst((left and UINTVAR_MASK).toInt())
            left = left shr UINTVAR_BITS
        } while (left > 0)
        groups.forEachIndexed { index, group ->
            byte(if (index == groups.size - 1) group else group or HIGH_BIT)
        }
    }

    /** A number written as its own length followed by its bytes, shortest form first. */
    fun longInteger(value: Long) {
        val bytes = value.toBigEndianBytes()
        byte(bytes.size)
        raw(bytes)
    }

    /** A number in whichever of the two forms fits: one byte under 128, length-prefixed above. */
    fun integer(value: Long) {
        if (value < SHORT_INTEGER_LIMIT) shortInteger(value.toInt()) else longInteger(value)
    }

    /** A length, written short when it is small and announced when it is not. */
    fun valueLength(length: Int) {
        if (length <= PduReader.SHORT_LENGTH_MAX) byte(length) else {
            byte(PduReader.LENGTH_QUOTE)
            uintvar(length.toLong())
        }
    }

    /**
     * Text, ended by a zero byte.
     *
     * A string starting with a character of 128 or more is quoted first, because the reader
     * would otherwise take that first byte for a number.
     */
    fun textString(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and HIGH_BIT) != 0) byte(PduReader.QUOTE)
        raw(bytes)
        byte(0)
    }

    /** Text with the character set it is written in, which is how a subject carries accents. */
    fun encodedString(text: String) {
        val body = PduWriter().apply {
            integer(UTF_8_CHARSET)
            textString(text)
        }.bytes()
        valueLength(body.size)
        raw(body)
    }

    /** An address: a length, the token saying it is there, and the address itself. */
    fun address(value: String) {
        // Written the way the standard spells a phone number, which is what a carrier expects.
        val written = if (value.any(Char::isDigit) && '@' !in value) "$value/TYPE=PLMN" else value
        val body = PduWriter().apply {
            byte(PduReader.ADDRESS_PRESENT)
            integer(UTF_8_CHARSET)
            textString(written)
        }.bytes()
        valueLength(body.size)
        raw(body)
    }

    /** One header: which field, then its value, written by [value]. */
    fun header(field: Int, value: PduWriter.() -> Unit) {
        byte(field)
        value()
    }

    private fun Long.toBigEndianBytes(): ByteArray {
        if (this == 0L) return byteArrayOf(0)
        val bytes = ArrayDeque<Byte>()
        var left = this
        while (left > 0) {
            bytes.addFirst((left and BYTE_MASK.toLong()).toByte())
            left = left shr Byte.SIZE_BITS
        }
        return bytes.toByteArray()
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val HIGH_BIT = 0x80
        const val UINTVAR_BITS = 7
        const val UINTVAR_MASK = 0x7FL
        const val SHORT_INTEGER_LIMIT = 128L
        const val UTF_8_CHARSET = 0x6AL
    }
}
