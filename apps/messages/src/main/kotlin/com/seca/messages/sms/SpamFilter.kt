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
    private val OptOut = Regex("(?i)(d[ée]sinscri|d[ée]sabonn|ne plus recevoir|unsubscribe)")
    private const val SHORT_CODE_MIN = 4
    private const val SHORT_CODE_MAX = 6

    /** On by default, as the call filter against telemarketing is. */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, on) }
    }

    fun isCommercial(address: String, body: String): Boolean {
        if (StopNumber.containsMatchIn(body)) return true
        val digits = address.count(Char::isDigit)
        val brandOrShortCode = address.any(Char::isLetter) || digits in SHORT_CODE_MIN..SHORT_CODE_MAX
        return brandOrShortCode && OptOut.containsMatchIn(body)
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
