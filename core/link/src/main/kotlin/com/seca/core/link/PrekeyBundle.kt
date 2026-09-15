package com.seca.core.link

import com.seca.core.link.handshake.Handshake
import com.seca.core.link.identity.LinkIdentity
import com.seca.core.link.nostr.NostrEvent
import com.seca.core.link.nostr.NostrKeys
import com.seca.core.link.relay.RelayClock
import org.json.JSONObject
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import kotlin.io.encoding.Base64

/**
 * What another Seca needs to open a session with this phone: the identity key,
 * a signed pre-key and a signed Kyber pre-key. Published as a NIP-78 app-data
 * event (kind 30078), which each relay replaces instead of piling up. Public by
 * nature: only public keys and their signatures.
 */
object PrekeyBundle {

    const val KIND = 30078
    const val TAG = "seca-link/prekeys"
    private const val VERSION = 1
    const val DEVICE_ID = 1

    /** NIP-09, the event kind that asks relays to drop what this phone published. */
    private const val DELETION_KIND = 5

    /** How long the keys stay on a relay without a fresh copy; the app publishes them again every few hours. */
    private const val LIFETIME_SECONDS = 24L * 60 * 60

    fun eventOf(identity: LinkIdentity): NostrEvent {
        val signed = identity.signedPreKey
        val kyber = identity.kyberPreKey
        val content = JSONObject()
            .put("v", VERSION)
            .put("registrationId", identity.registrationId)
            .put("deviceId", DEVICE_ID)
            .put("identityKey", Base64.encode(identity.signal.publicKey.serialize()))
            .put(
                "signedPreKey",
                JSONObject()
                    .put("id", signed.id)
                    .put("key", Base64.encode(signed.keyPair.publicKey.serialize()))
                    .put("signature", Base64.encode(signed.signature)),
            )
            .put(
                "kyberPreKey",
                JSONObject()
                    .put("id", kyber.id)
                    .put("key", Base64.encode(kyber.keyPair.publicKey.serialize()))
                    .put("signature", Base64.encode(kyber.signature)),
            )
        val now = RelayClock.now()
        // A phone that stops publishing has stopped having Seca Link: relays that honour NIP-40
        // drop the keys a day after the last copy, and contacts see the conversation end there.
        val tags = listOf(listOf("d", TAG), listOf("expiration", (now + LIFETIME_SECONDS).toString()))
        return identity.nostr.sign(KIND, tags, content.toString(), now)
    }

    /** Asks the relays to drop the keys [identity] published (NIP-09), when Seca Link is turned off. */
    fun deletionOf(identity: LinkIdentity): NostrEvent = identity.nostr.sign(
        DELETION_KIND,
        listOf(listOf("a", "$KIND:${identity.nostr.publicKey}:$TAG")),
        "",
        RelayClock.now(),
    )

    /** The NIP-01 filter that finds the bundle [publicKey] published. */
    fun filterFor(publicKey: String): String =
        "{\"kinds\":[$KIND],\"authors\":[\"$publicKey\"],\"#d\":[\"$TAG\"],\"limit\":1}"

    /** The same question asked once for several contacts, so one look at a relay covers them all. */
    fun filterForAll(publicKeys: Collection<String>): String =
        "{\"kinds\":[$KIND],\"authors\":[${publicKeys.joinToString(",") { "\"$it\"" }}],\"#d\":[\"$TAG\"],\"limit\":${publicKeys.size}}"

    /**
     * A bundle read from a relay, once checked: signed by [publicKey], and
     * carrying the identity key whose hash the invitation announced. A relay
     * cannot slip another key in. Null when anything does not match.
     */
    fun parse(event: NostrEvent, publicKey: String, identityHash: String): PreKeyBundle? = runCatching {
        val tagged = event.tags.any { it.size >= 2 && it[0] == "d" && it[1] == TAG }
        if (event.pubkey != publicKey || event.kind != KIND || !tagged || !NostrKeys.verify(event)) return null
        val json = JSONObject(event.content)
        if (json.getInt("v") != VERSION) return null
        val identityKey = Base64.decode(json.getString("identityKey"))
        if (Handshake.identityHashOf(identityKey) != identityHash) return null
        val signed = json.getJSONObject("signedPreKey")
        val kyber = json.getJSONObject("kyberPreKey")
        PreKeyBundle(
            json.getInt("registrationId"),
            json.getInt("deviceId"),
            PreKeyBundle.NULL_PRE_KEY_ID,
            null,
            signed.getInt("id"),
            ECPublicKey(Base64.decode(signed.getString("key"))),
            Base64.decode(signed.getString("signature")),
            IdentityKey(identityKey),
            kyber.getInt("id"),
            KEMPublicKey(Base64.decode(kyber.getString("key"))),
            Base64.decode(kyber.getString("signature")),
        )
    }.getOrNull()
}
