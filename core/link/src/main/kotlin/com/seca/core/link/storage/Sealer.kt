package com.seca.core.link.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals what Seca Link keeps on the phone with an AES key that Android
 * Keystore holds in the security chip (the Titan M2 on a Pixel) and never
 * hands out. Its files live in the app's no-backup storage: keys and sessions
 * copied to another phone would be stolen ones.
 */
internal object Sealer {

    // Kept from the first version, which sealed only the identity with it.
    private const val ALIAS = "seca-link-identity"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    @Volatile
    private var key: SecretKey? = null

    /** Reads and opens [file]; null when it does not exist yet. */
    fun read(file: File): ByteArray? = if (file.exists()) open(file.readBytes()) else null

    /** Seals [plain] into [file], written aside then renamed so a crash never leaves half of it. */
    fun write(file: File, plain: ByteArray) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(seal(plain))
        check(temporary.renameTo(file)) { "Impossible d'enregistrer ${file.name}" }
    }

    private fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_SIZE))
        return cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE)
    }

    @Synchronized
    private fun key(): SecretKey {
        key?.let { return it }
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val found = store.getKey(ALIAS, null) as? SecretKey
        // The security chip when the phone has one, the Keystore's trusted environment otherwise.
        return (found ?: generateKey(strongBox = true) ?: requireNotNull(generateKey(strongBox = false)))
            .also { key = it }
    }

    private fun generateKey(strongBox: Boolean): SecretKey? {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(spec)
            generator.generateKey()
        } catch (unavailable: StrongBoxUnavailableException) {
            null
        }
    }
}
