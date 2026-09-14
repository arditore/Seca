package com.seca.core.link

import android.content.Context
import com.seca.core.link.storage.Sealer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What this phone knows of another Seca phone, reached through a data SMS invitation. */
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
)

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
                    .put("invitedAt", peer.invitedAt),
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
