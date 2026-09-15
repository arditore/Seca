package com.seca.core.link

import android.content.Context
import com.seca.core.link.storage.Sealer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What this phone knows of another Seca phone, reached through an SMS invitation. */
data class LinkPeer(
    /** International format, the same however the number was written. */
    val number: String,
    val nostrPublicKey: String? = null,
    val relays: List<String> = emptyList(),
    val identityHash: String? = null,
    /** Base64 of the libsignal identity key trusted for this number. */
    val identityKey: String? = null,
    /** A session is open: messages to this number can be encrypted. */
    val ready: Boolean = false,
    /** The owner compared safety numbers with the contact. */
    val verified: Boolean = false,
    /** When the contact's identity key changed, until the owner acknowledges it; 0 otherwise. */
    val keyChangedAt: Long = 0,
    /** When this phone last sent an invitation; 0 when never. */
    val invitedAt: Long = 0,
    /** The contact's handshake came as a text message: answers go the same way, which reaches them. */
    val textHandshake: Boolean = false,
    /** When opening the session was last tried and failed, so it is tried again later, not at every turn. */
    val attemptedAt: Long = 0,
    /** How many times in a row opening the session failed: each failure waits longer than the one before. */
    val failedAttempts: Int = 0,
    /** When the session first opened, which the conversation marks with a notice; 0 before. */
    val connectedAt: Long = 0,
    /** When this phone last saw the contact's keys published, in milliseconds; 0 when never. */
    val bundleAt: Long = 0,
    /** When this phone last looked at whether the contact still publishes their keys. */
    val checkedAt: Long = 0,
    /** When the contact was found to have left Seca Link, which ends the encrypted conversation; 0 while they are there. */
    val leftAt: Long = 0,
) {
    /** Messages to this contact can go encrypted: a session is open, and the contact still has Seca Link. */
    val active: Boolean get() = ready && leftAt == 0L

    /** Whether enough has passed since the last failed attempt to try this contact again. */
    fun retryDue(now: Long = System.currentTimeMillis()): Boolean = now - attemptedAt >= retryDelayMillis(failedAttempts)
}

/**
 * Whether the keys a contact published look abandoned. Seca Link publishes them
 * again every five hours, so a day without a fresh copy means the contact turned
 * Seca Link off, or took Seca off their phone. Keys found nowhere at all count as
 * abandoned too. A phone that was simply away comes back encrypted as soon as its
 * keys are published again: saying "encrypted" where nothing is would be worse.
 */
internal fun keysLookAbandoned(publishedAt: Long, now: Long): Boolean =
    publishedAt <= 0L || publishedAt <= now - ABANDONED_MILLIS

private const val ABANDONED_MILLIS = 24L * 60 * 60 * 1000

/**
 * How long to wait before trying a contact again: ten minutes after the first
 * failure, twice as long after each one that follows, up to twelve hours. Keys
 * a relay dropped, or that the contact has not published yet, are usually back
 * within minutes; a contact who left Seca is not worth asking every ten minutes
 * for good.
 */
internal fun retryDelayMillis(failedAttempts: Int): Long =
    (RETRY_FIRST_MILLIS shl failedAttempts.coerceIn(0, RETRY_MOST_DOUBLINGS)).coerceAtMost(RETRY_LONGEST_MILLIS)

private const val RETRY_FIRST_MILLIS = 10L * 60 * 1000
private const val RETRY_LONGEST_MILLIS = 12L * 60 * 60 * 1000
private const val RETRY_MOST_DOUBLINGS = 7

/** The peers, sealed in one file and kept in memory, shared by the screens and the SMS receivers. */
class LinkPeers(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun all(): Map<String, LinkPeer> = synchronized(lock) { loaded() }

    operator fun get(number: String): LinkPeer? = all()[number]

    /** Changes the peer for [number], created when there is none, and tells whoever follows. */
    fun update(number: String, change: (LinkPeer) -> LinkPeer): LinkPeer {
        val updated = synchronized(lock) {
            val peers = loaded().toMutableMap()
            change(peers[number] ?: LinkPeer(number)).also {
                peers[number] = it
                write(peers)
                cache = peers
            }
        }
        changed.tryEmit(Unit)
        return updated
    }

    private fun loaded(): Map<String, LinkPeer> = cache ?: read().also { cache = it }

    private fun read(): Map<String, LinkPeer> {
        val plain = Sealer.read(file) ?: return emptyMap()
        val array = JSONArray(plain.toString(Charsets.UTF_8))
        return (0 until array.length()).associate { index ->
            val item = array.getJSONObject(index)
            val relays = item.optJSONArray("relays") ?: JSONArray()
            val peer = LinkPeer(
                number = item.getString("number"),
                nostrPublicKey = item.optString("nostr").ifEmpty { null },
                relays = (0 until relays.length()).map(relays::getString),
                identityHash = item.optString("identityHash").ifEmpty { null },
                identityKey = item.optString("identityKey").ifEmpty { null },
                ready = item.optBoolean("ready"),
                verified = item.optBoolean("verified"),
                keyChangedAt = item.optLong("keyChangedAt"),
                invitedAt = item.optLong("invitedAt"),
                textHandshake = item.optBoolean("textHandshake"),
                attemptedAt = item.optLong("attemptedAt"),
                failedAttempts = item.optInt("failedAttempts"),
                connectedAt = item.optLong("connectedAt"),
                bundleAt = item.optLong("bundleAt"),
                checkedAt = item.optLong("checkedAt"),
                leftAt = item.optLong("leftAt"),
            )
            peer.number to peer
        }
    }

    private fun write(peers: Map<String, LinkPeer>) {
        val array = JSONArray()
        peers.values.forEach { peer ->
            array.put(
                JSONObject()
                    .put("number", peer.number)
                    .put("nostr", peer.nostrPublicKey.orEmpty())
                    .put("relays", JSONArray(peer.relays))
                    .put("identityHash", peer.identityHash.orEmpty())
                    .put("identityKey", peer.identityKey.orEmpty())
                    .put("ready", peer.ready)
                    .put("verified", peer.verified)
                    .put("keyChangedAt", peer.keyChangedAt)
                    .put("invitedAt", peer.invitedAt)
                    .put("textHandshake", peer.textHandshake)
                    .put("attemptedAt", peer.attemptedAt)
                    .put("failedAttempts", peer.failedAttempts)
                    .put("connectedAt", peer.connectedAt)
                    .put("bundleAt", peer.bundleAt)
                    .put("checkedAt", peer.checkedAt)
                    .put("leftAt", peer.leftAt),
            )
        }
        Sealer.write(file, array.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val FILE_NAME = "seca-link-peers"
        private val lock = Any()

        @Volatile
        private var cache: Map<String, LinkPeer>? = null

        private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Emits whenever a peer changes, from any screen or receiver of this app. */
        val changes: SharedFlow<Unit> = changed.asSharedFlow()
    }
}
