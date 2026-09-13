package com.seca.core.model.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts a Seca backup with a passphrase, so the file can be kept anywhere —
 * a USB key, a computer, a cloud folder — without anyone being able to read it.
 *
 * AES-256-GCM, with the key derived from the passphrase by PBKDF2-HMAC-SHA256
 * over [ITERATIONS] rounds and a random salt. GCM also catches a wrong
 * passphrase or a damaged file. Layout: "SECA", version, salt, nonce, then the
 * encrypted content and its tag.
 */
object BackupCipher {

    const val ITERATIONS = 600_000
    private val MAGIC = "SECA".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + VERSION
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyOf(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        // The header is authenticated too: changing the version byte breaks the file.
        cipher.updateAAD(header)
        return header + salt + nonce + cipher.doFinal(plain)
    }

    /** The content, or null when the passphrase is wrong, or the file is damaged or not a Seca backup. */
    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray? {
        val headerSize = MAGIC.size + 1
        if (data.size < headerSize + SALT_BYTES + NONCE_BYTES + TAG_BITS / 8) return null
        val header = data.copyOfRange(0, headerSize)
        if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || header[MAGIC.size] != VERSION) return null
        val salt = data.copyOfRange(headerSize, headerSize + SALT_BYTES)
        val nonce = data.copyOfRange(headerSize + SALT_BYTES, headerSize + SALT_BYTES + NONCE_BYTES)
        val body = data.copyOfRange(headerSize + SALT_BYTES + NONCE_BYTES, data.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyOf(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    private fun keyOf(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
