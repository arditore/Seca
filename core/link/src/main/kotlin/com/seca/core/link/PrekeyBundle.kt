package com.seca.core.link

import com.seca.core.link.identity.LinkIdentity
import com.seca.core.link.nostr.NostrEvent
import org.json.JSONObject
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
    private const val DEVICE_ID = 1

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
        return identity.nostr.sign(KIND, listOf(listOf("d", TAG)), content.toString())
    }
}
