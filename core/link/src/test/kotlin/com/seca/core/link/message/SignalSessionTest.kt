package com.seca.core.link.message

import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * The session Seca Link relies on, played out between two phones in memory:
 * opened from a bundle as Seca publishes it, with a signed pre-key and a Kyber
 * pre-key but no one-time pre-key, then used both ways.
 */
class SignalSessionTest {

    @Test
    fun `a session opened from Seca's bundle carries messages both ways`() {
        val aliceIdentity = IdentityKeyPair.generate()
        val bobIdentity = IdentityKeyPair.generate()
        val alice = InMemorySignalProtocolStore(aliceIdentity, KeyHelper.generateRegistrationId(false))
        val bob = InMemorySignalProtocolStore(bobIdentity, KeyHelper.generateRegistrationId(false))

        val signedKeys = ECKeyPair.generate()
        val signed = SignedPreKeyRecord(1, 0, signedKeys, bobIdentity.privateKey.calculateSignature(signedKeys.publicKey.serialize()))
        val kyberKeys = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyber = KyberPreKeyRecord(1, 0, kyberKeys, bobIdentity.privateKey.calculateSignature(kyberKeys.publicKey.serialize()))
        bob.storeSignedPreKey(1, signed)
        bob.storeKyberPreKey(1, kyber)

        val aliceAddress = SignalProtocolAddress("+33600000001", 1)
        val bobAddress = SignalProtocolAddress("+33600000002", 1)
        val bundle = PreKeyBundle(
            bob.localRegistrationId,
            1,
            PreKeyBundle.NULL_PRE_KEY_ID,
            null,
            1,
            signedKeys.publicKey,
            signed.signature,
            bobIdentity.publicKey,
            1,
            kyberKeys.publicKey,
            kyber.signature,
        )
        SessionBuilder(alice, bobAddress, aliceAddress).process(bundle)

        val hello = LinkPayload.Text("1", "Bonjour Bob", 1)
        val first = SessionCipher(alice, aliceAddress, bobAddress).encrypt(LinkPayload.encode(hello))
        assertEquals(CiphertextMessage.PREKEY_TYPE, first.type)
        val received = SessionCipher(bob, bobAddress, aliceAddress).decrypt(PreKeySignalMessage(first.serialize()))
        assertEquals(hello, LinkPayload.decode(received))

        val seen = LinkPayload.Read(listOf("1"))
        val reply = SessionCipher(bob, bobAddress, aliceAddress).encrypt(LinkPayload.encode(seen))
        assertEquals(CiphertextMessage.WHISPER_TYPE, reply.type)
        val back = SessionCipher(alice, aliceAddress, bobAddress).decrypt(SignalMessage(reply.serialize()))
        assertEquals(seen, LinkPayload.decode(back))
    }
}
