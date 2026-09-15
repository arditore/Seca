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
import kotlinx.coroutines.joinAll
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import androidx.annotation.StringRes

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

    /**
     * Publishes the pre-key bundle to every relay at once, reporting each answer
     * as it arrives, and asks each relay for it back: one that says yes and keeps
     * nothing must not be named in an invitation, or contacts would look for the
     * keys where there is nothing to find.
     */
    fun publishPrekeys(): Flow<Pair<String, PublishResult>> = channelFlow {
        val own = identity()
        val event = PrekeyBundle.eventOf(own)
        val filter = PrekeyBundle.filterFor(own.nostr.publicKey)
        val kept = ConcurrentHashMap.newKeySet<String>()
        settings.relays().map { url ->
            launch(Dispatchers.IO) {
                var result = relayClient.publish(url, event)
                if (result == PublishResult.Accepted) {
                    val stored = relayClient.fetch(url, filter).orEmpty()
                        .any { it.pubkey == own.nostr.publicKey && it.content == event.content }
                    if (stored) kept += url else result = PublishResult.Refused(text(R.string.link_not_kept))
                }
                send(url to result)
            }
        }.joinAll()
        if (kept.isNotEmpty()) {
            settings.setKeptRelays(settings.relays().filter { it in kept })
            settings.publishedAt = System.currentTimeMillis()
        }
    }

    /**
     * The invitation to send [number], or null: only while Seca Link is on and
     * before a session exists. Sent on its own at most once a day, so a data SMS
     * a network dropped is offered again; [force], when the owner asks, sends it
     * again at once.
     */
    suspend fun invitationFor(number: String, force: Boolean = false): Handshake? {
        if (!settings.enabled) return null
        val peer = peers[number]
        val now = System.currentTimeMillis()
        if (peer?.active == true) return null
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
                // A fresh handshake is a fresh chance: the wait between attempts starts over.
                failedAttempts = 0,
                attemptedAt = 0,
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
            return Received.Failed(text(R.string.link_no_network))
        }
        return connect(number, answer)
    }

    /**
     * Contacts whose handshake came but whose session could not open, while
     * Seca Link was off, the network away or their keys not yet on a relay:
     * tried again ten minutes later, then less and less often. Returns those now
     * connected, with the answer to send them.
     */
    suspend fun connectWaiting(): List<Pair<LinkPeer, Handshake>> {
        if (!settings.enabled || !network.available()) return emptyList()
        val now = System.currentTimeMillis()
        return peers.all().values
            .filter { !it.ready && it.nostrPublicKey != null && it.retryDue(now) }
            .mapNotNull { peer ->
                peers.update(peer.number) { it.copy(attemptedAt = now) }
                val reply = (connect(peer.number, answer = true) as? Received.Connected)?.reply ?: return@mapNotNull null
                peers[peer.number]?.let { it to reply }
            }
    }

    /** Fetches the keys [number] announced from the relays it named, checks them, and opens the session. */
    private suspend fun connect(number: String, answer: Boolean): Received = withContext(Dispatchers.IO) {
        val peer = peers[number] ?: return@withContext Received.Failed(text(R.string.link_unknown_contact))
        val publicKey = peer.nostrPublicKey ?: return@withContext Received.Failed(text(R.string.link_incomplete_invitation))
        val hash = peer.identityHash ?: return@withContext Received.Failed(text(R.string.link_incomplete_invitation))
        val filter = PrekeyBundle.filterFor(publicKey)
        // The relays the contact named, then this phone's own and the default ones: a relay that
        // dropped the keys, or an invitation naming only relays that never kept them, is no dead end.
        val sources = (peer.relays + settings.relays() + LinkSettings.DefaultRelays).distinct()
        val found = coroutineScope {
            sources.map { url -> async { relayClient.fetch(url, filter).orEmpty() } }.awaitAll().flatten()
        }
        val bundle = found.firstNotNullOfOrNull { PrekeyBundle.parse(it, publicKey, hash) }
            ?: return@withContext failed(number, text(R.string.link_keys_not_found))
        val keyChanged = peer.identityKey != null && peer.identityKey != Base64.encode(bundle.identityKey.serialize())
        // A new key is announced, and whatever was verified about the old one no longer holds.
        if (keyChanged) peers.update(number) { it.copy(verified = false) }
        val store = store()
        try {
            sessions.withLock { SessionBuilder(store, store, store, store, addressOf(number), localAddress()).process(bundle) }
        } catch (invalid: InvalidKeyException) {
            return@withContext failed(number, text(R.string.link_invalid_keys, invalid.message.orEmpty()))
        } catch (untrusted: UntrustedIdentityException) {
            return@withContext failed(number, text(R.string.link_untrusted_key, untrusted.message.orEmpty()))
        }
        peers.update(number) { it.opened() }
        Received.Connected(reply = if (answer) handshakeOf(Handshake.Type.Accept) else null, keyChanged = keyChanged)
    }

    /**
     * Whether the contacts this phone writes to encrypted are still there. Seca
     * Link publishes its keys again every five hours: keys left untouched for two
     * days, or gone from every relay, mean the contact turned Seca Link off or
     * took Seca off their phone. Returns the numbers that just stopped, so their
     * conversation can say so and go back to SMS.
     */
    suspend fun checkPeersStillThere(now: Long = System.currentTimeMillis()): List<String> {
        if (!settings.enabled || !network.available()) return emptyList()
        val due = peers.all().values.filter { it.ready && now - it.checkedAt > CHECK_INTERVAL_MILLIS }
        val published = publishedTimes(due.mapNotNull { it.nostrPublicKey }) ?: return emptyList()
        return due.mapNotNull { peer -> settle(peer, published[peer.nostrPublicKey] ?: 0L, now) }
    }

    /**
     * Looks now at whether [number] still has Seca Link, as a conversation opens,
     * so the owner never writes into a conversation that only looks encrypted.
     * True when this is the moment they stopped.
     */
    suspend fun checkPeerStillThere(number: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!settings.enabled || !network.available()) return false
        val peer = peers[number]?.takeIf { it.ready && now - it.checkedAt > LOOK_AGAIN_MILLIS } ?: return false
        val publicKey = peer.nostrPublicKey ?: return false
        val published = publishedTimes(listOf(publicKey)) ?: return false
        return settle(peer, published[publicKey] ?: 0L, now) != null
    }

    /** A contact said they no longer have Seca Link: the conversation goes back to SMS at once. */
    fun peerLeft(number: String) {
        peers.update(number) { if (it.leftAt > 0) it else it.copy(leftAt = System.currentTimeMillis()) }
    }

    /**
     * Tells every connected contact that this phone no longer has Seca Link and
     * takes its keys off the relays, so nobody keeps writing into a conversation
     * that can no longer be read. Said while Seca Link is still on.
     */
    suspend fun sayGoodbye() {
        if (!settings.enabled) return
        val connected = peers.all().values.filter { it.active }
        coroutineScope {
            connected.forEach { peer -> launch { runCatching { send(peer.number, LinkPayload.Farewell) } } }
        }
        unpublishPrekeys()
    }

    /** Asks the relays to drop this phone's published keys. */
    suspend fun unpublishPrekeys() {
        val event = PrekeyBundle.deletionOf(identity())
        withContext(Dispatchers.IO) {
            coroutineScope { settings.relays().forEach { url -> launch { runCatching { relayClient.publish(url, event) } } } }
        }
        settings.setKeptRelays(emptyList())
        settings.publishedAt = 0
    }

    /** What the relays say of [peer], written down. Returns their number when this is the moment they stopped. */
    private fun settle(peer: LinkPeer, publishedAt: Long, now: Long): String? {
        peers.update(peer.number) { it.copy(checkedAt = now, bundleAt = maxOf(it.bundleAt, publishedAt)) }
        if (!keysLookAbandoned(publishedAt, now)) {
            // A contact who had left publishes their keys again: the conversation is encrypted once more.
            if (peer.leftAt > 0) peers.update(peer.number) { it.opened() }
            return null
        }
        if (peer.leftAt > 0) return null
        peers.update(peer.number) { it.copy(leftAt = now) }
        return peer.number
    }

    /**
     * When each of [publicKeys] last published their keys, asked of every relay
     * in one question, so following a dozen contacts costs one look. Null when
     * not a single relay answered: a quiet relay never ends a conversation.
     */
    private suspend fun publishedTimes(publicKeys: List<String>): Map<String, Long>? = withContext(Dispatchers.IO) {
        if (publicKeys.isEmpty()) return@withContext emptyMap()
        val filter = PrekeyBundle.filterForAll(publicKeys)
        val sources = (settings.relays() + LinkSettings.DefaultRelays + peers.all().values.flatMap { it.relays }).distinct()
        val answers = coroutineScope { sources.map { url -> async { relayClient.fetch(url, filter) } }.awaitAll() }
        if (answers.all { it == null }) return@withContext null
        buildMap {
            answers.filterNotNull().flatten().forEach { event ->
                val at = event.createdAt * MILLIS_PER_SECOND
                if (at > (this[event.pubkey] ?: 0L)) put(event.pubkey, at)
            }
        }
    }

    /** A failed attempt counts, so the next one waits longer than the last. */
    private fun failed(number: String, reason: String): Received.Failed {
        peers.update(number) { it.copy(failedAttempts = it.failedAttempts + 1) }
        return Received.Failed(reason)
    }

    /**
     * The contact as a session just opened with them: a contact who had left is
     * back, and the moment is kept, for the conversation says it.
     */
    private fun LinkPeer.opened(): LinkPeer = copy(
        ready = true,
        failedAttempts = 0,
        leftAt = 0,
        connectedAt = if (active) connectedAt else System.currentTimeMillis(),
    )

    /** Whether a message to [number] goes through Seca Link: it is on, a session is open, and the contact still has it. */
    fun canSend(number: String): Boolean = settings.enabled && peers[number]?.active == true

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
        if (!network.available()) return@withContext Sent.Failed(text(R.string.link_no_network))
        val own = identity()
        val store = store()
        val ciphertext = sessions.withLock {
            runCatching {
                SessionCipher(store, store, store, store, store, localAddress(), addressOf(number)).encrypt(LinkPayload.encode(payload))
            }.getOrNull()
        } ?: return@withContext Sent.Failed(text(R.string.link_encryption_failed))
        val event = Envelope.wrap(own.nostr, recipient, ciphertext.type, ciphertext.serialize(), ephemeral = payload == LinkPayload.Typing)
        val relays = peer.relays.ifEmpty { LinkSettings.DefaultRelays }
        val results = coroutineScope { relays.map { url -> async { relayClient.publish(url, event) } }.awaitAll() }
        if (results.any { it == PublishResult.Accepted }) Sent.Published else Sent.Failed(text(R.string.link_no_relay_accepted))
    }

    /** A message or a notice from a contact, decrypted. [keyChanged] when it came from a key this phone had not seen. */
    class Incoming(val number: String, val payload: LinkPayload, val keyChanged: Boolean = false)

    /**
     * Opens an envelope a relay handed over. Null when it is not for this
     * phone, not from a contact Seca Link knows, or does not decrypt, such as a
     * copy already opened from another relay.
     */
    suspend fun open(event: NostrEvent): Incoming? = withContext(Dispatchers.IO) {
        val own = identity()
        val opened = Envelope.open(own.nostr, event) ?: return@withContext null
        val peer = peers.all().values.firstOrNull { it.nostrPublicKey == opened.from } ?: return@withContext null
        val knownKeyChangedAt = peer.keyChangedAt
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
        // A first message from a phone this one had not reached yet, or from a contact who came back.
        if (!peer.active) peers.update(peer.number) { it.opened() }
        // Their message came from a key this phone had never seen: a new phone, or Seca installed again.
        val keyChanged = (peers[peer.number]?.keyChangedAt ?: 0L) > knownKeyChangedAt
        Incoming(peer.number, payload, keyChanged)
    }

    /** This phone's handshake, as the code a contact scans in person; null while Seca Link is off. */
    suspend fun myHandshake(): Handshake? = if (settings.enabled) handshakeOf(Handshake.Type.Invite) else null

    /** Tries at once, as the owner asked, to open the session with [number], whose handshake came earlier. */
    suspend fun retry(number: String): Received {
        if (!settings.enabled) return Received.Ignored
        val peer = peers[number] ?: return Received.Failed(text(R.string.link_unknown_contact))
        if (peer.nostrPublicKey == null) return Received.Failed(text(R.string.link_no_key_received))
        if (!network.available()) return Received.Failed(text(R.string.link_no_network))
        peers.update(number) { it.copy(attemptedAt = System.currentTimeMillis()) }
        return connect(number, answer = true)
    }

    /**
     * Follows [url] for the envelopes addressed to this phone, from [since]
     * (seconds) on. Relays that ask who is listening get a signed answer.
     * [onOpen] runs once the connection is up.
     */
    fun inbox(url: String, since: Long, onOpen: () -> Unit = {}): Flow<NostrEvent> = flow {
        val own = identity()
        val filter = "{\"kinds\":[${Envelope.KIND},${Envelope.EPHEMERAL_KIND}],\"#p\":[\"${own.nostr.publicKey}\"],\"since\":$since}"
        emitAll(
            relayClient.subscribe(url, filter, onOpen) { relay, challenge ->
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
        return Handshake(type, own.nostr.publicKey, Handshake.identityHashOf(own.signal.publicKey.serialize()), settings.handshakeRelays())
    }

    /** A reason given to the owner, in the phone's language. */
    private fun text(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    private fun addressOf(number: String) = SignalProtocolAddress(number, LinkProtocolStore.DEVICE_ID)

    private suspend fun localAddress() = SignalProtocolAddress(identity().nostr.publicKey, LinkProtocolStore.DEVICE_ID)

    private companion object {
        const val INVITE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val CHECK_INTERVAL_MILLIS = 60L * 60 * 1000
        const val LOOK_AGAIN_MILLIS = 60L * 1000
        const val MILLIS_PER_SECOND = 1000L
        const val FINGERPRINT_ITERATIONS = 5200
        const val FINGERPRINT_VERSION = 2
        const val AUTH_KIND = 22242

        /** Sessions change with every message: one change at a time, whichever screen or service makes it. */
        val sessions = Mutex()
    }
}
