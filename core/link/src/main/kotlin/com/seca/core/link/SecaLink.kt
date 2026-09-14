package com.seca.core.link

import android.content.Context
import com.seca.core.link.handshake.Handshake
import com.seca.core.link.identity.IdentityVault
import com.seca.core.link.identity.LinkIdentity
import com.seca.core.link.message.Envelope
import com.seca.core.link.message.LinkPayload
import com.seca.core.link.nostr.NostrEvent
import com.seca.core.link.relay.PublishResult
import com.seca.core.link.relay.RelayClient
import com.seca.core.link.session.LinkProtocolStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import kotlin.io.encoding.Base64

/**
 * Seca Link on this phone: its identity and relays, the invitations it sends
 * and answers by data SMS, the sessions they open, and the encrypted messages
 * that travel through them.
 *
 * Numbers are given in international format, so a contact is the same however
 * their number was written.
 */
class SecaLink(context: Context) {

    private val appContext = context.applicationContext
    private val vault = IdentityVault(appContext)
    val settings = LinkSettings(appContext)

    /** Through Tor when the owner asked for it, read at each use so the choice applies at once. */
    private val relayClient: RelayClient get() = RelayClient(throughTor = settings.useTor)
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
     * The invitation to send [number], or null: only while Seca Link is on and
     * before a session exists. Sent on its own at most once a week; [force], when
     * the owner asks, sends it again at once.
     */
    suspend fun invitationFor(number: String, force: Boolean = false): Handshake? {
        if (!settings.enabled) return null
        val peer = peers[number]
        val now = System.currentTimeMillis()
        if (peer?.ready == true) return null
        if (!force && peer != null && now - peer.invitedAt < INVITE_INTERVAL_MILLIS) return null
        val invitation = handshakeOf(Handshake.Type.Invite)
        peers.update(number) { it.copy(invitedAt = now) }
        return invitation
    }

    /** What came of a handshake. */
    sealed interface Received {
        /** Not an invitation, or Seca Link is off: what it said is kept, nothing is sent. */
        data object Ignored : Received

        /** The session is open. [reply] is the answer to send back when this phone was invited. */
        class Connected(val reply: Handshake?, val keyChanged: Boolean) : Received

        /** The keys could not be fetched, or did not match: nothing was trusted yet, and it is tried again later. */
        data class Failed(val reason: String) : Received
    }

    /** A data SMS on Seca Link's port. */
    suspend fun receive(number: String, payload: ByteArray): Received {
        val handshake = Handshake.decode(payload) ?: return Received.Ignored
        return receive(number, handshake, byText = false)
    }

    /** A handshake from [number], by data SMS or, with [byText], written in a text message. */
    suspend fun receive(number: String, handshake: Handshake, byText: Boolean): Received {
        val before = peers[number]
        // Kept even while Seca Link is off, so that turning it on can still connect.
        peers.update(number) {
            it.copy(
                nostrPublicKey = handshake.nostrPublicKey,
                relays = handshake.relays,
                identityHash = handshake.identityHash,
                textHandshake = it.textHandshake || byText,
            )
        }
        if (!settings.enabled) return Received.Ignored
        val answer = handshake.type == Handshake.Type.Invite
        // Already connected to this very key: only the answer is still owed, in case the first one was lost.
        if (before?.ready == true && before.identityHash == handshake.identityHash) {
            return Received.Connected(reply = if (answer) handshakeOf(Handshake.Type.Accept) else null, keyChanged = false)
        }
        if (!network.available()) {
            peers.update(number) { it.copy(attemptedAt = System.currentTimeMillis()) }
            return Received.Failed("Pas d'accès au réseau")
        }
        return connect(number, answer)
    }

    /**
     * Contacts whose handshake came but whose session could not open, while
     * Seca Link was off or the network away: tried again, at most once an hour
     * each. Returns those now connected, with the answer to send them.
     */
    suspend fun connectWaiting(): List<Pair<LinkPeer, Handshake>> {
        if (!settings.enabled || !network.available()) return emptyList()
        val now = System.currentTimeMillis()
        return peers.all().values
            .filter { !it.ready && it.nostrPublicKey != null && now - it.attemptedAt > RETRY_INTERVAL_MILLIS }
            .mapNotNull { peer ->
                peers.update(peer.number) { it.copy(attemptedAt = now) }
                val reply = (connect(peer.number, answer = true) as? Received.Connected)?.reply ?: return@mapNotNull null
                peers[peer.number]?.let { it to reply }
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
            sessions.withLock { SessionBuilder(store, store, store, store, addressOf(number), localAddress()).process(bundle) }
        } catch (invalid: InvalidKeyException) {
            return@withContext Received.Failed("Clés invalides : ${invalid.message}")
        } catch (untrusted: UntrustedIdentityException) {
            return@withContext Received.Failed("Clé non approuvée : ${untrusted.message}")
        }
        peers.update(number) { it.copy(ready = true) }
        Received.Connected(reply = if (answer) handshakeOf(Handshake.Type.Accept) else null, keyChanged = keyChanged)
    }

    /** Whether a message to [number] goes through Seca Link: it is on, and a session is open. */
    fun canSend(number: String): Boolean = settings.enabled && peers[number]?.ready == true

    /** What became of a message handed to Seca Link. */
    sealed interface Sent {
        /** At least one of the contact's relays took the envelope. */
        data object Published : Sent

        /** No session with this number: the message should go by SMS. */
        data object NotConnected : Sent

        data class Failed(val reason: String) : Sent
    }

    /** Encrypts [payload] for [number] and hands it to every relay that contact named, at once. */
    suspend fun send(number: String, payload: LinkPayload): Sent = withContext(Dispatchers.IO) {
        val peer = peers[number]?.takeIf { settings.enabled && it.ready } ?: return@withContext Sent.NotConnected
        val recipient = peer.nostrPublicKey ?: return@withContext Sent.NotConnected
        if (!network.available()) return@withContext Sent.Failed("Pas d'accès au réseau")
        val own = identity()
        val store = store()
        val ciphertext = sessions.withLock {
            runCatching {
                SessionCipher(store, store, store, store, store, localAddress(), addressOf(number)).encrypt(LinkPayload.encode(payload))
            }.getOrNull()
        } ?: return@withContext Sent.Failed("Chiffrement impossible")
        val event = Envelope.wrap(own.nostr, recipient, ciphertext.type, ciphertext.serialize(), ephemeral = payload == LinkPayload.Typing)
        val relays = peer.relays.ifEmpty { LinkSettings.DefaultRelays }
        val results = coroutineScope { relays.map { url -> async { relayClient.publish(url, event) } }.awaitAll() }
        if (results.any { it == PublishResult.Accepted }) Sent.Published else Sent.Failed("Aucun relais n'a accepté le message")
    }

    /** A message or a notice from a contact, decrypted. */
    class Incoming(val number: String, val payload: LinkPayload)

    /**
     * Opens an envelope a relay handed over. Null when it is not for this
     * phone, not from a contact Seca Link knows, or does not decrypt, such as a
     * copy already opened from another relay.
     */
    suspend fun open(event: NostrEvent): Incoming? = withContext(Dispatchers.IO) {
        val own = identity()
        val opened = Envelope.open(own.nostr, event) ?: return@withContext null
        val peer = peers.all().values.firstOrNull { it.nostrPublicKey == opened.from } ?: return@withContext null
        val store = store()
        val plain = sessions.withLock {
            runCatching {
                val cipher = SessionCipher(store, store, store, store, store, localAddress(), addressOf(peer.number))
                when (opened.signalType) {
                    CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(opened.ciphertext))
                    CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(opened.ciphertext))
                    else -> null
                }
            }.getOrNull()
        } ?: return@withContext null
        val payload = LinkPayload.decode(plain) ?: return@withContext null
        // A first message from a phone this one had not reached yet: the session now works both ways.
        if (!peer.ready) peers.update(peer.number) { it.copy(ready = true) }
        Incoming(peer.number, payload)
    }

    /**
     * Follows [url] for the envelopes addressed to this phone, from [since]
     * (seconds) on. Relays that ask who is listening get a signed answer.
     */
    fun inbox(url: String, since: Long): Flow<NostrEvent> = flow {
        val own = identity()
        val filter = "{\"kinds\":[${Envelope.KIND},${Envelope.EPHEMERAL_KIND}],\"#p\":[\"${own.nostr.publicKey}\"],\"since\":$since}"
        emitAll(
            relayClient.subscribe(url, filter) { relay, challenge ->
                own.nostr.sign(AUTH_KIND, listOf(listOf("relay", relay), listOf("challenge", challenge)), "")
            },
        )
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
        const val INVITE_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val RETRY_INTERVAL_MILLIS = 60L * 60 * 1000
        const val FINGERPRINT_ITERATIONS = 5200
        const val FINGERPRINT_VERSION = 2
        const val AUTH_KIND = 22242

        /** Sessions change with every message: one change at a time, whichever screen or service makes it. */
        val sessions = Mutex()
    }
}
