package com.seca.core.link.message

import com.seca.core.link.nostr.NostrEvent
import com.seca.core.link.nostr.NostrKeys
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/**
 * The envelope a Seca Link message travels in, after NIP-59's gift wrap:
 * - outside, a Nostr event signed by a key made for this message alone, which
 *   names only the recipient;
 * - inside, sealed for the recipient, the sender's Nostr key, the libsignal
 *   message, and the sender's signature over both.
 *
 * A relay learns who receives, when, and roughly how much; never who sends,
 * nor what. The inside is sealed with AES-256-GCM rather than NIP-44: only Seca
 * opens these envelopes, and the message is already encrypted by libsignal.
 */
object Envelope {

    const val KIND = 1059

    /** Typing notices: relays pass these on without keeping them. */
    const val EPHEMERAL_KIND = 21059

    private const val VERSION: Byte = 1
    private const val KEY_SIZE = 32
    private const val SIGNATURE_SIZE = 64
    private const val HEADER_SIZE = 1 + KEY_SIZE + 1 + SIGNATURE_SIZE
    private const val BYTE_MASK = 0xFF
    private const val MILLIS_PER_SECOND = 1000
    private const val MAX_BACKDATE_SECONDS = 900

    private val random = SecureRandom()

    /** What an envelope held, once opened and its signature checked. */
    class Opened(val from: String, val signalType: Int, val ciphertext: ByteArray)

    fun wrap(sender: NostrKeys, recipient: String, signalType: Int, ciphertext: ByteArray, ephemeral: Boolean): NostrEvent {
        val inside = insideOf(sender, recipient, signalType, ciphertext)
        val oneTime = NostrKeys.generate()
        val sealed = EnvelopeCipher.seal(oneTime.sharedSecretWith(recipient), inside, associatedOf(oneTime.publicKey, recipient))
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        // A kept envelope carries a time a few minutes off, so the relay does not record the exact moment.
        val createdAt = if (ephemeral) now else now - random.nextInt(MAX_BACKDATE_SECONDS)
        return oneTime.sign(
            kind = if (ephemeral) EPHEMERAL_KIND else KIND,
            tags = listOf(listOf("p", recipient)),
            content = Base64.encode(sealed),
            createdAt = createdAt,
        )
    }

    /** Null when the envelope is not for [recipient], was altered, or its sender's signature does not hold. */
    fun open(recipient: NostrKeys, event: NostrEvent): Opened? = runCatching {
        if (event.kind != KIND && event.kind != EPHEMERAL_KIND) return null
        if (event.tags.none { it.size >= 2 && it[0] == "p" && it[1] == recipient.publicKey }) return null
        if (!NostrKeys.verify(event)) return null
        val inside = EnvelopeCipher.open(
            recipient.sharedSecretWith(event.pubkey),
            Base64.decode(event.content),
            associatedOf(event.pubkey, recipient.publicKey),
        ) ?: return null
        parse(inside, recipient.publicKey)
    }.getOrNull()

    private fun insideOf(sender: NostrKeys, recipient: String, signalType: Int, ciphertext: ByteArray): ByteArray {
        val signature = sender.signDigest(digestOf(recipient, sender.publicKey, signalType, ciphertext))
        return ByteArrayOutputStream().apply {
            write(VERSION.toInt())
            write(sender.publicKey.hexToByteArray())
            write(signalType)
            write(signature)
            write(ciphertext)
        }.toByteArray()
    }

    private fun parse(inside: ByteArray, recipient: String): Opened? {
        if (inside.size <= HEADER_SIZE || inside[0] != VERSION) return null
        val from = inside.copyOfRange(1, 1 + KEY_SIZE).toHexString()
        val signalType = inside[1 + KEY_SIZE].toInt() and BYTE_MASK
        val signature = inside.copyOfRange(2 + KEY_SIZE, HEADER_SIZE)
        val ciphertext = inside.copyOfRange(HEADER_SIZE, inside.size)
        if (!NostrKeys.verifyDigest(signature, digestOf(recipient, from, signalType, ciphertext), from)) return null
        return Opened(from, signalType, ciphertext)
    }

    /** What the sender signs: the envelope cannot be readdressed, nor its message swapped. */
    private fun digestOf(recipient: String, from: String, signalType: Int, ciphertext: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(recipient.hexToByteArray())
            update(from.hexToByteArray())
            update(signalType.toByte())
            digest(ciphertext)
        }

    private fun associatedOf(outerKey: String, recipient: String): ByteArray = outerKey.hexToByteArray() + recipient.hexToByteArray()
}
