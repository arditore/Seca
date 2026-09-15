package com.seca.core.link.handshake

import com.seca.core.link.LinkSettings
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * The discreet invitation two Seca phones exchange by data SMS: where to find
 * this phone on the relays, and which identity key to expect there. A phone
 * without Seca ignores a data SMS on this port, so a contact without Seca never
 * sees anything.
 *
 * It fits in a single SMS: a header, the Nostr public key, eight bytes of the
 * identity key's hash, and up to three relays, either as their place in the
 * default list or spelled out.
 */
data class Handshake(
    val type: Type,
    /** Hex, as Nostr writes it. */
    val nostrPublicKey: String,
    /** Hex of the first bytes of the SHA-256 of the serialized libsignal identity key. */
    val identityHash: String,
    val relays: List<String>,
) {
    enum class Type(val code: Byte) { Invite(1), Accept(2) }

    /**
     * The same handshake written into an ordinary text message, for when a
     * data SMS does not get through, as some networks drop them. A phone with
     * Seca reads it and sets it aside; anyone else reads [intro], a short explanation.
     */
    fun text(intro: String): String = "$intro $TEXT_MARKER${Base64.getUrlEncoder().withoutPadding().encodeToString(encode())}"

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(MAGIC_S, MAGIC_L, VERSION, type.code))
        out.write(nostrPublicKey.hexToByteArray().also { require(it.size == KEY_SIZE) })
        out.write(identityHash.hexToByteArray().also { require(it.size == HASH_SIZE) })
        val entries = relays.mapNotNull(::entryOf)
        val fitting = mutableListOf<ByteArray>()
        var size = HEADER_SIZE + 1
        for (entry in entries) {
            if (fitting.size == MAX_RELAYS) break
            if (size + entry.size <= MAX_PAYLOAD) {
                fitting += entry
                size += entry.size
            }
        }
        out.write(fitting.size)
        fitting.forEach(out::write)
        return out.toByteArray()
    }

    private fun entryOf(relay: String): ByteArray? {
        val index = LinkSettings.DefaultRelays.indexOf(relay)
        if (index in 0 until DEFAULT_FLAG) return byteArrayOf((DEFAULT_FLAG or index).toByte())
        val host = relay.removePrefix(SCHEME)
        if (host.isEmpty() || host.length >= DEFAULT_FLAG || host.any { it.code >= ASCII_LIMIT }) return null
        return byteArrayOf(host.length.toByte()) + host.toByteArray(Charsets.US_ASCII)
    }

    companion object {
        /** The data SMS port Seca listens on. */
        const val PORT: Short = 19734

        /** A data SMS carries 133 bytes once its port header is in; a little is kept aside. */
        const val MAX_PAYLOAD = 130

        private const val MAGIC_S: Byte = 0x53
        private const val MAGIC_L: Byte = 0x4C
        private const val VERSION: Byte = 1
        private const val KEY_SIZE = 32
        private const val HASH_SIZE = 8
        private const val HEADER_SIZE = 4 + KEY_SIZE + HASH_SIZE
        private const val MAX_RELAYS = 3
        private const val DEFAULT_FLAG = 0x80
        private const val BYTE_MASK = 0xFF
        private const val INDEX_MASK = 0x7F
        private const val ASCII_LIMIT = 0x80
        private const val SCHEME = "wss://"

        private const val TEXT_MARKER = "seca-link:"
        private val TextHandshake = Regex("seca-link:([A-Za-z0-9_-]{40,240})")

        /** The handshake a text message carries, written by [text]; null for any other message. */
        fun fromText(body: String): Handshake? {
            val encoded = TextHandshake.find(body)?.groupValues?.get(1) ?: return null
            return runCatching { decode(Base64.getUrlDecoder().decode(encoded)) }.getOrNull()
        }

        /** What an invitation says of an identity key, enough to recognise it on the relays. */
        fun identityHashOf(serializedIdentityKey: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(serializedIdentityKey).copyOf(HASH_SIZE).toHexString()

        /** Null when [bytes] are not a Seca invitation this version understands. */
        fun decode(bytes: ByteArray): Handshake? {
            if (bytes.size < HEADER_SIZE + 1 || bytes[0] != MAGIC_S || bytes[1] != MAGIC_L || bytes[2] != VERSION) return null
            val type = Type.entries.firstOrNull { it.code == bytes[3] } ?: return null
            var at = HEADER_SIZE
            val count = bytes[at++].toInt() and BYTE_MASK
            if (count > MAX_RELAYS) return null
            val relays = buildList {
                repeat(count) {
                    if (at >= bytes.size) return null
                    val tag = bytes[at++].toInt() and BYTE_MASK
                    if (tag and DEFAULT_FLAG != 0) {
                        LinkSettings.DefaultRelays.getOrNull(tag and INDEX_MASK)?.let(::add)
                    } else {
                        if (tag == 0 || at + tag > bytes.size) return null
                        add(SCHEME + String(bytes, at, tag, Charsets.US_ASCII))
                        at += tag
                    }
                }
            }
            return Handshake(
                type = type,
                nostrPublicKey = bytes.copyOfRange(4, 4 + KEY_SIZE).toHexString(),
                identityHash = bytes.copyOfRange(4 + KEY_SIZE, HEADER_SIZE).toHexString(),
                relays = relays.mapNotNull(LinkSettings::normalize),
            )
        }
    }
}
