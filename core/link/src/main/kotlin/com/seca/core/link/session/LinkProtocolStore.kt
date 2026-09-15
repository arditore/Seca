package com.seca.core.link.session

import android.content.Context
import com.seca.core.link.LinkPeers
import com.seca.core.link.identity.LinkIdentity
import com.seca.core.link.storage.Sealer
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import java.io.File
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Everything libsignal keeps for Seca Link: this phone's keys, the identity
 * key trusted for each number, and one sealed session file per contact.
 *
 * Trust comes on first use. A key that changes is accepted and announced,
 * except for a contact the owner had verified: then nothing is sent to them
 * until the owner has looked again.
 */
internal class LinkProtocolStore(
    context: Context,
    private val identity: LinkIdentity,
    private val peers: LinkPeers,
) : IdentityKeyStore, SessionStore, PreKeyStore, SignedPreKeyStore, KyberPreKeyStore {

    private val sessions = File(context.noBackupFilesDir, SESSIONS_DIR)
    private val baseKeys = File(context.noBackupFilesDir, BASE_KEYS_FILE)

    override fun getIdentityKeyPair(): IdentityKeyPair = identity.signal

    override fun getLocalRegistrationId(): Int = identity.registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val encoded = Base64.encode(identityKey.serialize())
        var replaced = false
        peers.update(address.name) { peer ->
            when (peer.identityKey) {
                null -> peer.copy(identityKey = encoded)
                encoded -> peer
                else -> {
                    replaced = true
                    peer.copy(identityKey = encoded, verified = false, keyChangedAt = System.currentTimeMillis())
                }
            }
        }
        return if (replaced) IdentityKeyStore.IdentityChange.REPLACED_EXISTING else IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val peer = peers[address.name] ?: return true
        val trusted = peer.identityKey ?: return true
        return trusted == Base64.encode(identityKey.serialize()) ||
            direction == IdentityKeyStore.Direction.RECEIVING ||
            !peer.verified
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        peers[address.name]?.identityKey?.let { IdentityKey(Base64.decode(it)) }

    // A fresh record when there is none: libsignal tells an empty one from an open session.
    override fun loadSession(address: SignalProtocolAddress): SessionRecord =
        Sealer.read(fileOf(address))?.let(::SessionRecord) ?: SessionRecord()

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.map { address ->
            Sealer.read(fileOf(address))?.let(::SessionRecord) ?: throw NoSessionException("No session with ${address.name}")
        }

    // One phone per number.
    override fun getSubDeviceSessions(name: String): List<Int> = emptyList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        Sealer.write(fileOf(address), record.serialize())
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = fileOf(address).exists()

    override fun deleteSession(address: SignalProtocolAddress) {
        fileOf(address).delete()
    }

    override fun deleteAllSessions(name: String) {
        fileOf(SignalProtocolAddress(name, DEVICE_ID)).delete()
    }

    // No one-time pre-keys: a relay could not hand each out only once. PQXDH works without them.
    override fun loadPreKey(preKeyId: Int): PreKeyRecord = throw InvalidKeyIdException("No one-time pre-key")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = Unit

    override fun containsPreKey(preKeyId: Int): Boolean = false

    override fun removePreKey(preKeyId: Int) = Unit

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        identity.signedPreKey.takeIf { it.id == signedPreKeyId }
            ?: throw InvalidKeyIdException("Unknown signed pre-key: $signedPreKeyId")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = listOf(identity.signedPreKey)

    // The pre-keys are made with the identity and never replaced from outside.
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) = Unit

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeyId == identity.signedPreKey.id

    override fun removeSignedPreKey(signedPreKeyId: Int) = Unit

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        identity.kyberPreKey.takeIf { it.id == kyberPreKeyId }
            ?: throw InvalidKeyIdException("Unknown Kyber pre-key: $kyberPreKeyId")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = listOf(identity.kyberPreKey)

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) = Unit

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = kyberPreKeyId == identity.kyberPreKey.id

    /** The Kyber pre-key serves every contact: the same base key twice can only be a replayed message. */
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        val entry = digest("$kyberPreKeyId.$signedPreKeyId.".toByteArray() + baseKey.serialize())
        synchronized(baseKeysLock) {
            val seen = Sealer.read(baseKeys)?.toString(Charsets.UTF_8)?.lines()?.filter { it.isNotBlank() }.orEmpty()
            if (entry in seen) throw ReusedBaseKeyException("Base key already used")
            Sealer.write(baseKeys, (seen + entry).takeLast(MAX_BASE_KEYS).joinToString("\n").toByteArray(Charsets.UTF_8))
        }
    }

    private fun fileOf(address: SignalProtocolAddress): File =
        File(sessions, digest("${address.name}.${address.deviceId}".toByteArray(Charsets.UTF_8)))

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    companion object {
        const val DEVICE_ID = 1
        private const val SESSIONS_DIR = "seca-link-sessions"
        private const val BASE_KEYS_FILE = "seca-link-base-keys"
        private const val MAX_BASE_KEYS = 5000
        private val baseKeysLock = Any()
    }
}
