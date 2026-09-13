package com.seca.core.link

import android.content.Context
import androidx.core.content.edit

/** The owner's Seca Link choices, kept on the phone. */
class LinkSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off until the owner turns it on: nothing is created or sent before. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** When the pre-keys last reached a relay, in milliseconds; 0 when never. */
    var publishedAt: Long
        get() = prefs.getLong(KEY_PUBLISHED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_PUBLISHED_AT, value) }

    /** Pre-keys are published again once a week, so relays that dropped them get them back. */
    fun publishDue(now: Long = System.currentTimeMillis()): Boolean = now - publishedAt > REPUBLISH_MILLIS

    /** The relays that receive for this phone, in the owner's order. */
    fun relays(): List<String> =
        prefs.getString(KEY_RELAYS, null)?.split('\n')?.filter { it.isNotBlank() } ?: DefaultRelays

    fun setRelays(relays: List<String>) = prefs.edit { putString(KEY_RELAYS, relays.distinct().joinToString("\n")) }

    fun resetRelays() = prefs.edit { remove(KEY_RELAYS) }

    companion object {
        /** Public, free relays run by volunteers, which keep app data and encrypted envelopes. */
        val DefaultRelays = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
        )

        private const val PREFS = "seca_link"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RELAYS = "relays"
        private const val KEY_PUBLISHED_AT = "published_at"
        private const val REPUBLISH_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val SECURE_SCHEME = "wss://"

        /**
         * A relay address as the owner types it, made whole: "nos.lol" becomes
         * "wss://nos.lol". Null when it cannot be a relay. Only encrypted
         * connections are accepted.
         */
        fun normalize(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val rest = when {
                trimmed.startsWith(SECURE_SCHEME, ignoreCase = true) -> trimmed.substring(SECURE_SCHEME.length)
                "://" in trimmed -> return null
                else -> trimmed
            }
            val authority = rest.substringBefore('/')
            val host = authority.substringBefore(':')
            val validHost = '.' in host && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
            return if (validHost) SECURE_SCHEME + authority.lowercase() + rest.removePrefix(authority) else null
        }
    }
}
