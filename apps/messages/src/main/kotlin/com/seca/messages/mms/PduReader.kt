package com.seca.messages.mms

import java.io.ByteArrayOutputStream

/**
 * Reads the binary encoding multimedia messages are written in.
 *
 * An MMS does not arrive as a message. It arrives as a WAP push carrying a handful of headers,
 * and the message itself has to be fetched afterwards; both are encoded the way WAP encoded
 * everything in 1999, which Android has no public parser for. So this reads it: integers packed
 * seven bits at a time, strings that may or may not carry a character set, and header fields
 * named by a single byte.
 *
 * Everything here is total: a reader that runs off the end of a malformed message returns null
 * rather than throwing, because a malformed message is something a stranger can send to a phone.
 */
internal class PduReader(private val bytes: ByteArray, private var at: Int = 0) {

    val position: Int get() = at

    val exhausted: Boolean get() = at >= bytes.size

    fun peek(): Int? = bytes.getOrNull(at)?.toInt()?.and(0xFF)

    fun byte(): Int? = peek()?.also { at++ }

    fun skip(count: Int) {
        at = (at + count).coerceAtMost(bytes.size)
    }

    /**
     * An unsigned integer written seven bits to a byte, the top bit saying that another follows.
     * How WAP writes a length that is usually small and occasionally is not.
     */
    fun uintvar(): Long? {
        var value = 0L
        repeat(MAX_UINTVAR_BYTES) {
            val b = byte() ?: return null
            value = (value shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) return value
        }
        return null
    }

    /** One byte with its top bit set, carrying a small value in the other seven. */
    fun shortInteger(): Int? = peek()?.takeIf { it and 0x80 != 0 }?.let { byte(); it and 0x7F }

    /** A length byte followed by that many bytes of a big-endian number. */
    fun longInteger(): Long? {
        val length = byte() ?: return null
        if (length > MAX_LONG_INTEGER_BYTES) return null
        var value = 0L
        repeat(length) {
            val b = byte() ?: return null
            value = (value shl 8) or b.toLong()
        }
        return value
    }

    /** Either form of integer, whichever the next byte turns out to be. */
    fun integer(): Long? = shortInteger()?.toLong() ?: longInteger()

    /**
     * How long the next value is: a byte up to 30 says so itself, and 31 means the length is
     * written as a uintvar instead.
     */
    fun valueLength(): Long? {
        val first = byte() ?: return null
        return when {
            first <= SHORT_LENGTH_MAX -> first.toLong()
            first == LENGTH_QUOTE -> uintvar()
            else -> null
        }
    }

    /** A string ending at the first zero byte, with the quote byte dropped if one leads it. */
    fun textString(): String? {
        if (peek() == QUOTE) at++
        val out = ByteArrayOutputStream()
        while (true) {
            val b = byte() ?: return null
            if (b == 0) return out.toString(Charsets.UTF_8.name())
            out.write(b)
        }
    }

    /**
     * Text that may carry the character set it was written in. Plain text is read as it comes;
     * the longer form says its length and its charset first, which is how a subject in Hindi or
     * Arabic survives the trip.
     */
    fun encodedString(): String? {
        val first = peek() ?: return null
        if (first > SHORT_LENGTH_MAX && first != LENGTH_QUOTE) return textString()
        val length = valueLength()?.toInt() ?: return null
        val end = (at + length).coerceAtMost(bytes.size)
        // The charset is written as an integer; the text follows it, to the end of the value.
        val charsetStart = at
        val charset = integer()
        if (charset == null) at = charsetStart
        val text = ByteArrayOutputStream()
        while (at < end) {
            val b = byte() ?: break
            if (b == 0) break
            text.write(b)
        }
        at = end
        return runCatching { text.toString(charsetOf(charset).name()) }.getOrNull()
    }

    /**
     * An address, which is written as a length, a token saying the address is present, and then
     * the address itself — or the token saying it was withheld, for a number that hides itself.
     */
    fun address(): String? {
        val length = valueLength()?.toInt() ?: return null
        val end = (at + length).coerceAtMost(bytes.size)
        val token = byte()
        val value = if (token == ADDRESS_PRESENT) encodedString() else null
        at = end
        // Addresses arrive as "+33612345678/TYPE=PLMN"; the part after the slash is not a number.
        return value?.substringBefore('/')
    }

    /** The bytes from here to [end], for a message part's own content. */
    fun bytes(end: Int): ByteArray {
        val stop = end.coerceIn(at, bytes.size)
        return bytes.copyOfRange(at, stop).also { at = stop }
    }

    private fun charsetOf(code: Long?): java.nio.charset.Charset = when (code) {
        null, UTF_8, MIB_UTF_8 -> Charsets.UTF_8
        ASCII, MIB_ASCII -> Charsets.US_ASCII
        LATIN_1, MIB_LATIN_1 -> Charsets.ISO_8859_1
        UTF_16 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    companion object {
        const val QUOTE = 0x7F
        const val LENGTH_QUOTE = 31
        const val SHORT_LENGTH_MAX = 30
        /** The token saying an address follows; its neighbour, 129, means "insert mine here". */
        const val ADDRESS_PRESENT = 0x80

        private const val MAX_UINTVAR_BYTES = 5
        private const val MAX_LONG_INTEGER_BYTES = 30

        // The few character sets that actually turn up, by their IANA number and their short form.
        private const val ASCII = 0x03L
        private const val LATIN_1 = 0x04L
        private const val UTF_8 = 0x6AL
        private const val UTF_16 = 0x03F7L
        private const val MIB_ASCII = 3L
        private const val MIB_LATIN_1 = 4L
        private const val MIB_UTF_8 = 106L
    }
}
