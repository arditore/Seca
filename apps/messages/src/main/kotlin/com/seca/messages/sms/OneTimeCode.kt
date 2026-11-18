package com.seca.messages.sms

/**
 * Finds the verification code in a message, such as "Votre code : 482 913".
 *
 * Only a message that talks about a code or a password is searched, and only digits standing
 * near those words count, so an order number or an amount is not mistaken for one.
 *
 * **Both halves of that are load-bearing, and the second was missing.** The rule used to be a
 * keyword anywhere and digits anywhere, which is far too generous in a real inbox: an
 * appointment reminder saying "confirmez votre venue" and carrying a postcode or a year
 * matched on both counts. Where the owner has asked for codes to be erased after a while,
 * that is a real message quietly deleted — and nothing would have said so.
 *
 * "confirm" and "connexion" are gone from the list for the same reason. A message that really
 * carries a code says "code" somewhere ("votre code de confirmation"), so they cost nothing
 * and they were what made an appointment reminder look like a login.
 *
 * Erring towards finding nothing is the safe direction: a code not recognised stays in the
 * inbox, which is where it would have been anyway.
 */
internal object OneTimeCode {

    private val Keywords = Regex(
        "(?i)(code|otp|v[ée]rif|mot de passe|password|passcode|2fa|s[ée]curit)",
    )

    /**
     * How far from the word the digits may stand.
     *
     * "Votre code de vérification est le 482913" is forty characters at the outside, and
     * the postcode at the bottom of an appointment reminder is far further than that.
     */
    private const val NEARBY = 40

    /**
     * Four to eight digits, or two groups of three, standing on their own: not part of a longer
     * number, a phone number or a decimal ("12.50"), though a full stop may end the sentence.
     */
    private val Digits = Regex("(?<![\\d+])(?<!\\d[.,])(\\d{3}[ -]\\d{3}|\\d{4,8})(?!\\d)(?![.,]\\d)")

    fun find(body: String): String? {
        val words = Keywords.findAll(body).map { it.range }.toList()
        if (words.isEmpty()) return null
        return Digits.findAll(body)
            .firstOrNull { digits -> words.any { near(it, digits.range) } }
            ?.value
            ?.filter(Char::isDigit)
    }

    /** Whether the digits stand close enough to the word to be what it is talking about. */
    private fun near(word: IntRange, digits: IntRange): Boolean = when {
        digits.first >= word.last -> digits.first - word.last <= NEARBY
        else -> word.first - digits.last <= NEARBY
    }
}
