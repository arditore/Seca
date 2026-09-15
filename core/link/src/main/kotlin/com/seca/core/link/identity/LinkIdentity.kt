package com.seca.core.link.identity

import com.seca.core.link.nostr.NostrKeys
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * What makes this phone reachable on Seca Link, created once and kept on it: a
 * Nostr key to receive on the relays, and a libsignal identity with the
 * pre-keys another phone needs to open a session (PQXDH).
 */
class LinkIdentity internal constructor(
    val nostr: NostrKeys,
    val signal: IdentityKeyPair,
    val registrationId: Int,
    val signedPreKey: SignedPreKeyRecord,
    val kyberPreKey: KyberPreKeyRecord,
) {
    /** Thirty digits in six groups, from the libsignal identity key, to compare two phones by eye. */
    val fingerprint: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(signal.publicKey.serialize())
        (0 until FINGERPRINT_GROUPS).joinToString(" ") { group ->
            var value = 0L
            repeat(BYTES_PER_GROUP) { value = (value shl Byte.SIZE_BITS) or (digest[group * BYTES_PER_GROUP + it].toLong() and BYTE_MASK) }
            (value % GROUP_MODULUS).toString().padStart(GROUP_DIGITS, '0')
        }
    }

    internal fun serialize(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            val secret = nostr.secretBytes()
            out.writeBlock(secret)
            secret.fill(0)
            out.writeBlock(signal.serialize())
            out.writeInt(registrationId)
            out.writeBlock(signedPreKey.serialize())
            out.writeBlock(kyberPreKey.serialize())
        }
        return bytes.toByteArray()
    }

    companion object {
        const val SIGNED_PRE_KEY_ID = 1
        const val KYBER_PRE_KEY_ID = 1
        private const val FORMAT_VERSION = 1
        private const val FINGERPRINT_GROUPS = 6
        private const val BYTES_PER_GROUP = 5
        private const val GROUP_DIGITS = 5
        private const val GROUP_MODULUS = 100_000L
        private const val BYTE_MASK = 0xFFL
        private const val MAX_BLOCK = 64 * 1024

        /** A new identity; its pre-keys are signed by the identity key, as PQXDH requires. */
        fun generate(now: Long = System.currentTimeMillis()): LinkIdentity {
            val signal = IdentityKeyPair.generate()
            val signedKeys = ECKeyPair.generate()
            val kyberKeys = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            return LinkIdentity(
                nostr = NostrKeys.generate(),
                signal = signal,
                registrationId = KeyHelper.generateRegistrationId(false),
                signedPreKey = SignedPreKeyRecord(
                    SIGNED_PRE_KEY_ID,
                    now,
                    signedKeys,
                    signal.privateKey.calculateSignature(signedKeys.publicKey.serialize()),
                ),
                kyberPreKey = KyberPreKeyRecord(
                    KYBER_PRE_KEY_ID,
                    now,
                    kyberKeys,
                    signal.privateKey.calculateSignature(kyberKeys.publicKey.serialize()),
                ),
            )
        }

        internal fun deserialize(bytes: ByteArray): LinkIdentity = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == FORMAT_VERSION) { "Unknown identity format" }
            val secret = input.readBlock()
            val nostr = NostrKeys(secret)
            secret.fill(0)
            LinkIdentity(
                nostr = nostr,
                signal = IdentityKeyPair(input.readBlock()),
                registrationId = input.readInt(),
                signedPreKey = SignedPreKeyRecord(input.readBlock()),
                kyberPreKey = KyberPreKeyRecord(input.readBlock()),
            )
        }

        private fun DataOutputStream.writeBlock(block: ByteArray) {
            writeInt(block.size)
            write(block)
        }

        private fun DataInputStream.readBlock(): ByteArray {
            val size = readInt()
            check(size in 0..MAX_BLOCK) { "Damaged identity" }
            return ByteArray(size).also(::readFully)
        }
    }
}
