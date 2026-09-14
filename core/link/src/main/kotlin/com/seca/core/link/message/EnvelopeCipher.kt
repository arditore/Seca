package com.seca.core.link.message

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals an envelope's inside with a secret both phones derive from their keys:
 * HKDF-SHA256 (RFC 5869) over that secret, then AES-256-GCM, both from the
 * platform. The message within is already encrypted by libsignal; this hides
 * who sent it.
 */
internal object EnvelopeCipher {

    private val Info = "seca-link/envelope/v1".toByteArray(Charsets.US_ASCII)
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val KEY_SIZE = 32
    private const val HASH_SIZE = 32
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val HMAC = "HmacSHA256"

    private val random = SecureRandom()

    fun seal(shared: ByteArray, plain: ByteArray, associated: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyOf(shared), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associated)
        return nonce + cipher.doFinal(plain)
    }

    /** Null when the secret, the associated data or a single byte differs. */
    fun open(shared: ByteArray, sealed: ByteArray, associated: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyOf(shared), "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_SIZE))
        cipher.updateAAD(associated)
        cipher.doFinal(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE)
    }.getOrNull()

    /** HKDF: extract with an all-zero salt, then expand once with the envelope's label. */
    private fun keyOf(shared: ByteArray): ByteArray {
        val pseudoRandomKey = hmac(ByteArray(HASH_SIZE), shared)
        return hmac(pseudoRandomKey, Info + byteArrayOf(1)).copyOf(KEY_SIZE)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        return mac.doFinal(data)
    }
}
