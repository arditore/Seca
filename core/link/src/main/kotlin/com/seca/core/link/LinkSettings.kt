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

    /** When the pre-keys last reached a relay that kept them, in milliseconds; 0 when never. */
    var publishedAt: Long
        get() = prefs.getLong(KEY_PUBLISHED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_PUBLISHED_AT, value) }

    /** The relays that took the pre-keys and handed them back when asked, in the owner's order. */
    fun keptRelays(): List<String> =
        prefs.getString(KEY_KEPT_RELAYS, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun setKeptRelays(relays: List<String>) = prefs.edit { putString(KEY_KEPT_RELAYS, relays.distinct().joinToString("\n")) }

    /** Tells contacts when their messages were read. On by default, as the owner chose. */
    var readReceipts: Boolean
        get() = prefs.getBoolean("read_receipts", true)
        set(value) = prefs.edit { putBoolean("read_receipts", value) }

    /** Shows contacts that a message is being written. On by default. */
    var typingIndicator: Boolean
        get() = prefs.getBoolean("typing_indicator", true)
        set(value) = prefs.edit { putBoolean("typing_indicator", value) }

    /** Reaches the relays through Tor, with Orbot, so they never see the phone's address. Off by default. */
    var useTor: Boolean
        get() = prefs.getBoolean("use_tor", false)
        set(value) = prefs.edit { putBoolean("use_tor", value) }

    /**
     * Pre-keys are published again every four hours: relays that dropped them get them back, and
     * the copy they keep stays young. A phone that stops publishing is a phone that stopped having
     * Seca Link, which is what tells its contacts to go back to SMS.
     */
    fun publishDue(now: Long = System.currentTimeMillis()): Boolean = now - publishedAt > REPUBLISH_MILLIS

    /** The relays that receive for this phone, in the owner's order. */
    fun relays(): List<String> =
        prefs.getString(KEY_RELAYS, null)?.split('\n')?.filter { it.isNotBlank() } ?: DefaultRelays

    /**
     * The relays an invitation names, those known to keep this phone's keys
     * first. An invitation carries three at most: a relay that takes the keys
     * and keeps nothing would otherwise send the contact looking where there is
     * nothing to find.
     */
    fun handshakeRelays(): List<String> = orderForHandshake(relays(), keptRelays())

    fun setRelays(relays: List<String>) = prefs.edit { putString(KEY_RELAYS, relays.distinct().joinToString("\n")) }

    fun resetRelays() = prefs.edit { remove(KEY_RELAYS) }

    companion object {
        /**
         * Public, free relays run by volunteers, which keep app data and encrypted envelopes.
         * Invitations name them by their place in this list: new ones only ever go at the end.
         */
        val DefaultRelays = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
        )

        /** [relays] with those that keep the keys first, each group in the owner's order. */
        fun orderForHandshake(relays: List<String>, kept: Collection<String>): List<String> =
            relays.filter { it in kept } + relays.filterNot { it in kept }

        private const val PREFS = "seca_link"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RELAYS = "relays"
        private const val KEY_KEPT_RELAYS = "kept_relays"
        private const val KEY_PUBLISHED_AT = "published_at"
        private const val REPUBLISH_MILLIS = 4L * 60 * 60 * 1000
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
