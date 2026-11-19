package com.seca.messages.sms

/**
 * Whether what arrived is a message somebody wrote, or bytes that are not text at all.
 *
 * A picture message is announced by a push that is binary, and it is meant to be handed to the
 * app as a push rather than as a text. When that goes wrong somewhere between the network and
 * the phone, the default messaging app is handed the bytes as though they were words, and the
 * conversation fills with what two users have now called hieroglyphs (#72).
 *
 * Seca already refuses to show a multimedia part as a sentence when its bytes are not text.
 * This is the same answer one step earlier, on the way in, because the bytes arrive here
 * already decoded and the test that works there cannot work here: a binary payload run through
 * the GSM alphabet comes out as characters, just not characters anybody typed.
 *
 * So the rule is about what the characters are, and it is deliberately timid. Control
 * characters and replacement characters are what binary leaves behind and what writing never
 * contains, beyond the odd line break. A message has to be mostly those before it is refused,
 * which no sentence in any script ever is, and a message with one stray character in it is
 * still delivered.
 */
internal fun readsAsMessage(body: String): Boolean {
    if (body.isEmpty()) return true
    // A NUL is never typed and survives every decoding, so one is enough on its own.
    if (body.any { it == '\u0000' }) return false
    val unwritable = body.count { character ->
        character == REPLACEMENT || (Character.isISOControl(character) && character !in WRITTEN_CONTROLS)
    }
    return unwritable * CEILING_DIVISOR < body.length
}

/** What a decoder leaves where it could not read a byte. */
private const val REPLACEMENT = '�'

/** The three control characters that do appear in a message somebody typed. */
private val WRITTEN_CONTROLS = charArrayOf('\n', '\r', '\t')

/** A message is refused once a third of it is unwritable, and not before. */
private const val CEILING_DIVISOR = 3
