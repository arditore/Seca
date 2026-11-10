package com.seca.messages.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import androidx.core.content.edit

/**
 * Recognises advertising texts, which Seca files away without a notification.
 *
 * French law makes every commercial text offer a free way out, "STOP au 36180"
 * or another 36xxx number: that mention is the surest sign. Brands and short
 * codes offering to unsubscribe count too. The owner's contacts are never
 * filtered, and neither are verification codes.
 */
internal object SpamFilter {

    private const val PREFS = "seca_spam"
    private const val KEY_ENABLED = "enabled"

    private val StopNumber = Regex("(?i)\\bstop(?:\\s+(?:sms|pub))?\\s*(?:au|:|->|=)?\\s*3\\d{4}\\b")
    /**
     * "STOP" and then a link, which is where the way out has moved.
     *
     * The law asks for a free way out, not for a particular shape of one, and brands have gone
     * from "STOP au 36180" to "STOP https://mrnd.fr/8YwIwWb". It is the same sentence, and the
     * old rule saw none of it because it was looking for five digits beginning with a three.
     *
     * Counted only from a brand or a short code. Somebody writing "stop" and pasting a link is
     * having a conversation, and their number is far too long to be either of those.
     */
    private val StopLink = Regex("(?i)\\bstop\\b[\\s:.>=-]*(?:https?://|www\\.)\\S+")

    private val OptOut = Regex("(?i)(d[ée]sinscri|d[ée]sabonn|ne plus recevoir|unsubscribe)")
    private const val SHORT_CODE_MIN = 4
    private const val SHORT_CODE_MAX = 6

    /**
     * India's sender names say what they are, and a regulator decides it rather than the sender.
     *
     * TRAI has every registered sender written `XX-BRAND-Z`: two letters for the operator and
     * the circle, the brand as it registered, and a last letter that classifies the message —
     * `P` promotional, `T` transactional, `S` service, `G` government. So `AD-MYNTRA-P` is
     * advertising by law and `JD-HDFCBK-T` is a bank alert, whatever either one says in its text.
     * That is a better signal than any wording, and it cuts both ways: a one-time code is never
     * filed away because it happens to carry the word "unsubscribe".
     *
     * Told to us by a user in India (issue #13), who knows the rule better than any documentation
     * of it that I could find.
     */
    private val TraiSender = Regex("(?i)^[A-Z]{2}-[A-Z0-9]{2,10}-([TSPG])$")
    private const val TRAI_PROMOTIONAL = "P"

    /** On by default, as the call filter against telemarketing is. */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, on) }
    }

    fun isCommercial(address: String, body: String): Boolean {
        // A sender that classifies itself under India's rules is taken at its word, both ways.
        TraiSender.find(address.trim())?.let { return it.groupValues[1].uppercase() == TRAI_PROMOTIONAL }
        if (StopNumber.containsMatchIn(body)) return true
        val digits = address.count(Char::isDigit)
        val brandOrShortCode = address.any(Char::isLetter) || digits in SHORT_CODE_MIN..SHORT_CODE_MAX
        if (!brandOrShortCode) return false
        return OptOut.containsMatchIn(body) || StopLink.containsMatchIn(body)
    }

    fun isContact(context: Context, address: String): Boolean = runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
            arrayOf(PhoneLookup._ID),
            null,
            null,
            null,
        )?.use { it.count > 0 }
    }.getOrNull() == true
}
