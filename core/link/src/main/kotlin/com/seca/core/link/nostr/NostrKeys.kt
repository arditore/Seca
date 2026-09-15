package com.seca.core.link.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

/**
 * A Nostr key pair: a secp256k1 secret, and the x-only public key that names
 * its owner on the relays. Events carry BIP-340 Schnorr signatures.
 */
class NostrKeys(secret: ByteArray) {

    private val secret = secret.copyOf()

    init {
        require(this.secret.size == KEY_SIZE && Secp256k1.secKeyVerify(this.secret)) { "Invalid Nostr key" }
    }

    /** Hex, as Nostr writes it: the x coordinate of the public point. */
    val publicKey: String = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(this.secret))
        .copyOfRange(1, KEY_SIZE + 1)
        .toHexString()

    fun sign(
        kind: Int,
        tags: List<List<String>>,
        content: String,
        createdAt: Long = System.currentTimeMillis() / MILLIS_PER_SECOND,
    ): NostrEvent {
        val id = NostrEvent.idOf(publicKey, createdAt, kind, tags, content)
        val signature = signDigest(id)
        return NostrEvent(id.toHexString(), publicKey, createdAt, kind, tags, content, signature.toHexString())
    }

    /** A Schnorr signature over 32 bytes, such as a SHA-256 digest. */
    fun signDigest(digest: ByteArray): ByteArray {
        val auxiliary = ByteArray(KEY_SIZE).also(random::nextBytes)
        return Secp256k1.signSchnorr(digest, secret, auxiliary)
    }

    /**
     * The secret this key shares with [publicKey]'s owner: the x coordinate of
     * this secret times their point. Both sides reach the same value, whatever
     * the parity each point was taken with.
     */
    fun sharedSecretWith(publicKey: String): ByteArray {
        val point = Secp256k1.pubKeyTweakMul(byteArrayOf(EVEN_Y) + publicKey.hexToByteArray(), secret)
        return point.copyOfRange(1, KEY_SIZE + 1)
    }

    /** A copy of the secret, for the vault to seal. */
    internal fun secretBytes(): ByteArray = secret.copyOf()

    companion object {
        private const val KEY_SIZE = 32
        private const val MILLIS_PER_SECOND = 1000
        private const val EVEN_Y: Byte = 0x02
        private val random = SecureRandom()

        fun generate(): NostrKeys {
            val secret = ByteArray(KEY_SIZE)
            do {
                random.nextBytes(secret)
            } while (!Secp256k1.secKeyVerify(secret))
            return NostrKeys(secret).also { secret.fill(0) }
        }

        /** Whether [event] was signed by its public key, and its id matches what it says. */
        fun verify(event: NostrEvent): Boolean = runCatching {
            val id = NostrEvent.idOf(event.pubkey, event.createdAt, event.kind, event.tags, event.content)
            id.toHexString() == event.id &&
                Secp256k1.verifySchnorr(event.sig.hexToByteArray(), id, event.pubkey.hexToByteArray())
        }.getOrDefault(false)

        fun verifyDigest(signature: ByteArray, digest: ByteArray, publicKey: String): Boolean = runCatching {
            Secp256k1.verifySchnorr(signature, digest, publicKey.hexToByteArray())
        }.getOrDefault(false)
    }
}
