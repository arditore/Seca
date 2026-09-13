package com.seca.core.link.identity

import android.content.Context
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
 * Keeps the identity in the app's private storage, sealed with an AES key that
 * Android Keystore holds in the phone's security chip (the Titan M2 on a
 * Pixel) and never hands out. The file is left out of every backup: an identity
 * copied to another phone would be a stolen one.
 */
class IdentityVault(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun exists(): Boolean = file.exists()

    @Synchronized
    fun loadOrCreate(): LinkIdentity = load() ?: LinkIdentity.generate().also(::save)

    private fun load(): LinkIdentity? {
        if (!file.exists()) return null
        val plain = open(file.readBytes())
        return try {
            LinkIdentity.deserialize(plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun save(identity: LinkIdentity) {
        val plain = identity.serialize()
        val sealed = try {
            seal(plain)
        } finally {
            plain.fill(0)
        }
        // Written aside then renamed, so a crash never leaves half an identity.
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(sealed)
        check(temporary.renameTo(file)) { "Impossible d'enregistrer l'identité Seca Link" }
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

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        // The security chip when the phone has one, the Keystore's trusted environment otherwise.
        return generateKey(strongBox = true) ?: requireNotNull(generateKey(strongBox = false))
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

    private companion object {
        const val FILE_NAME = "seca-link-identity"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "seca-link-identity"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
