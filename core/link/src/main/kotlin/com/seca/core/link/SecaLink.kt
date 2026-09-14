package com.seca.core.link

import android.content.Context
import com.seca.core.link.handshake.Handshake
import com.seca.core.link.identity.IdentityVault
import com.seca.core.link.identity.LinkIdentity
import com.seca.core.link.relay.PublishResult
import com.seca.core.link.relay.RelayClient
import com.seca.core.link.session.LinkProtocolStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import kotlin.io.encoding.Base64

/**
 * Seca Link on this phone: its identity and relays, the invitations it sends
 * and answers by data SMS, and the sessions they open.
 *
 * Numbers are given in international format, so a contact is the same however
 * their number was written.
 */
class SecaLink(context: Context) {

    private val appContext = context.applicationContext
    private val vault = IdentityVault(appContext)
    private val relayClient = RelayClient()
    val settings = LinkSettings(appContext)
    val network = NetworkAccess(appContext)
    val peers = LinkPeers(appContext)

    @Volatile
    private var identity: LinkIdentity? = null

    @Volatile
    private var store: LinkProtocolStore? = null

    /** The identity, created the first time: generating the keys and sealing them takes a moment. */
    suspend fun identity(): LinkIdentity = identity ?: withContext(Dispatchers.IO) {
        vault.loadOrCreate().also { identity = it }
    }

    private suspend fun store(): LinkProtocolStore = store ?: LinkProtocolStore(appContext, identity(), peers).also { store = it }

    /** Publishes the pre-key bundle to every relay at once, reporting each answer as it arrives. */
    fun publishPrekeys(): Flow<Pair<String, PublishResult>> = channelFlow {
        val event = PrekeyBundle.eventOf(identity())
        settings.relays().forEach { url ->
            launch(Dispatchers.IO) {
                val result = relayClient.publish(url, event)
                if (result == PublishResult.Accepted) settings.publishedAt = System.currentTimeMillis()
                send(url to result)
            }
        }
    }

    /**
     * The invitation to send [number] by data SMS, or null: only while Seca
     * Link is on, before a session exists, and at most once a month.
     */
    suspend fun invitationFor(number: String): ByteArray? {
        if (!settings.enabled) return null
        val peer = peers[number]
        val now = System.currentTimeMillis()
        if (peer?.ready == true || (peer != null && now - peer.invitedAt < INVITE_INTERVAL_MILLIS)) return null
        val invitation = handshakeOf(Handshake.Type.Invite).encode()
        peers.update(number) { it.copy(invitedAt = now) }
        return invitation
    }

    /** What came of a data SMS on Seca Link's port. */
    sealed interface Received {
        /** Not an invitation, or Seca Link is off: what it said is kept, nothing is sent. */
        data object Ignored : Received

        /** The session is open. [reply] is the answer to send back when this phone was invited. */
        class Connected(val reply: ByteArray?, val keyChanged: Boolean) : Received

        /** The keys could not be fetched, or did not match: nothing was trusted. */
        data class Failed(val reason: String) : Received
    }

    suspend fun receive(number: String, payload: ByteArray): Received {
        val handshake = Handshake.decode(payload) ?: return Received.Ignored
        val before = peers[number]
        // Kept even while Seca Link is off, so that turning it on can still connect.
        peers.update(number) {
            it.copy(nostrPublicKey = handshake.nostrPublicKey, relays = handshake.relays, identityHash = handshake.identityHash)
        }
        if (!settings.enabled) return Received.Ignored
        val answer = handshake.type == Handshake.Type.Invite
        // Already connected to this very key: only the answer is still owed.
        if (before?.ready == true && before.identityHash == handshake.identityHash) {
            return Received.Connected(reply = if (answer) handshakeOf(Handshake.Type.Accept).encode() else null, keyChanged = false)
        }
        if (!network.available()) return Received.Failed("Pas d'accès au réseau")
        return connect(number, answer)
    }

    /** Numbers that invited this phone while Seca Link was off, connected now; each with the answer to send. */
    suspend fun connectWaiting(): List<Pair<String, ByteArray>> {
        if (!settings.enabled || !network.available()) return emptyList()
        return peers.all().values
            .filter { !it.ready && it.nostrPublicKey != null }
            .mapNotNull { peer ->
                (connect(peer.number, answer = true) as? Received.Connected)?.reply?.let { peer.number to it }
            }
    }

    /** Fetches the keys [number] announced from the relays it named, checks them, and opens the session. */
    private suspend fun connect(number: String, answer: Boolean): Received = withContext(Dispatchers.IO) {
        val peer = peers[number] ?: return@withContext Received.Failed("Contact inconnu")
        val publicKey = peer.nostrPublicKey ?: return@withContext Received.Failed("Invitation incomplète")
        val hash = peer.identityHash ?: return@withContext Received.Failed("Invitation incomplète")
        val filter = PrekeyBundle.filterFor(publicKey)
        val found = coroutineScope {
            peer.relays.map { url -> async { relayClient.fetch(url, filter).orEmpty() } }.awaitAll().flatten()
        }
        val bundle = found.firstNotNullOfOrNull { PrekeyBundle.parse(it, publicKey, hash) }
            ?: return@withContext Received.Failed("Clés introuvables sur les relais")
        val keyChanged = peer.identityKey != null && peer.identityKey != Base64.encode(bundle.identityKey.serialize())
        // A new key is announced, and whatever was verified about the old one no longer holds.
        if (keyChanged) peers.update(number) { it.copy(verified = false) }
        val store = store()
        try {
            SessionBuilder(store, store, store, store, addressOf(number), localAddress()).process(bundle)
        } catch (invalid: InvalidKeyException) {
            return@withContext Received.Failed("Clés invalides : ${invalid.message}")
        } catch (untrusted: UntrustedIdentityException) {
            return@withContext Received.Failed("Clé non approuvée : ${untrusted.message}")
        }
        peers.update(number) { it.copy(ready = true) }
        Received.Connected(reply = if (answer) handshakeOf(Handshake.Type.Accept).encode() else null, keyChanged = keyChanged)
    }

    /** The 60 digits both phones show for [number], and the code one scans on the other; null before a session. */
    suspend fun safetyNumber(number: String): SafetyNumber? {
        val peer = peers[number] ?: return null
        val remoteKey = peer.identityKey ?: return null
        val remoteId = peer.nostrPublicKey ?: return null
        val own = identity()
        return withContext(Dispatchers.Default) {
            val fingerprint = NumericFingerprintGenerator(FINGERPRINT_ITERATIONS).createFor(
                FINGERPRINT_VERSION,
                own.nostr.publicKey.hexToByteArray(),
                own.signal.publicKey,
                remoteId.hexToByteArray(),
                IdentityKey(Base64.decode(remoteKey)),
            )
            SafetyNumber(fingerprint.displayableFingerprint.displayText, fingerprint.scannableFingerprint)
        }
    }

    fun setVerified(number: String, verified: Boolean) {
        peers.update(number) { it.copy(verified = verified, keyChangedAt = if (verified) 0 else it.keyChangedAt) }
    }

    fun acknowledgeKeyChange(number: String) {
        peers.update(number) { it.copy(keyChangedAt = 0) }
    }

    private suspend fun handshakeOf(type: Handshake.Type): Handshake {
        val own = identity()
        return Handshake(type, own.nostr.publicKey, Handshake.identityHashOf(own.signal.publicKey.serialize()), settings.relays())
    }

    private fun addressOf(number: String) = SignalProtocolAddress(number, LinkProtocolStore.DEVICE_ID)

    private suspend fun localAddress() = SignalProtocolAddress(identity().nostr.publicKey, LinkProtocolStore.DEVICE_ID)

    private companion object {
        const val INVITE_INTERVAL_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val FINGERPRINT_ITERATIONS = 5200
        const val FINGERPRINT_VERSION = 2
    }
}
