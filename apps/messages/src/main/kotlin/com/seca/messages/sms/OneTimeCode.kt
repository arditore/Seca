package com.seca.messages.sms

/**
 * Finds the verification code in a message, such as "Votre code : 482 913".
 * Only a message that talks about a code, a password or a confirmation is
 * searched, so an order number or an amount is not mistaken for one.
 */
internal object OneTimeCode {

    private val Keywords = Regex(
        "(?i)(code|otp|v[ée]rif|confirm|mot de passe|password|passcode|pin|connexion|login|2fa|s[ée]curit)",
    )

    /**
     * Four to eight digits, or two groups of three, standing on their own: not part of a longer
     * number, a phone number or a decimal ("12.50"), though a full stop may end the sentence.
     */
    private val Digits = Regex("(?<![\\d+])(?<!\\d[.,])(\\d{3}[ -]\\d{3}|\\d{4,8})(?!\\d)(?![.,]\\d)")

    fun find(body: String): String? {
        if (!Keywords.containsMatchIn(body)) return null
        return Digits.find(body)?.value?.filter(Char::isDigit)
    }
}
