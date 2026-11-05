package com.seca.core.suite

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/** How often a backup is written, once there is one to write. */
enum class BackupRhythm(val days: Int) { Daily(1), Weekly(7) }

/**
 * What the owner chose about backups that write themselves.
 *
 * The folder is theirs: it comes from the system's own folder picker, and the right to write
 * there is held across restarts, so the app sees that folder and nothing else of the phone.
 *
 * The passphrase is the awkward part and is treated as such. A backup that encrypts itself every
 * night cannot stop to ask for one, so the phrase is kept — sealed with a key that Android
 * Keystore holds in the security chip and never hands out, in the app's own storage, which no
 * backup of the phone copies. It is the same arrangement Seca Link's keys live under. Someone
 * who takes the phone and unlocks it can make a backup; someone who takes the storage cannot
 * read the phrase out of it.
 */
class AutoBackupSettings(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Where both folders are made. Null until the owner picks one, which is also how this is off. */
    var folder: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.toUri()
        set(value) = prefs.edit {
            if (value == null) remove(KEY_FOLDER) else putString(KEY_FOLDER, value.toString())
        }

    var rhythm: BackupRhythm
        get() = runCatching { BackupRhythm.valueOf(prefs.getString(KEY_RHYTHM, null).orEmpty()) }
            .getOrDefault(BackupRhythm.Daily)
        set(value) = prefs.edit { putString(KEY_RHYTHM, value.name) }

    /** How many of each kind are kept; the oldest beyond this go. */
    var keep: Int
        get() = prefs.getInt(KEY_KEEP, DEFAULT_KEEP)
        set(value) = prefs.edit { putInt(KEY_KEEP, value) }

    /**
     * Whether the second, readable folder is written as well.
     *
     * Off unless asked for, and asked for in words rather than with a shrug: that folder is
     * readable by anything that finds it, which is the point of it and also its whole cost.
     */
    var portable: Boolean
        get() = prefs.getBoolean(KEY_PORTABLE, false)
        set(value) = prefs.edit { putBoolean(KEY_PORTABLE, value) }

    /** When the last one was written, and what went wrong if anything did. */
    var lastRun: Long
        get() = prefs.getLong(KEY_LAST_RUN, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_RUN, value) }

    var lastFailure: String?
        get() = prefs.getString(KEY_LAST_FAILURE, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LAST_FAILURE) else putString(KEY_LAST_FAILURE, value)
        }

    /** On when there is somewhere to write to. Forgetting the folder is how it is turned off. */
    val on: Boolean get() = folder != null

    fun passphrase(): CharArray? = runCatching {
        val sealed = prefs.getString(KEY_PHRASE, null) ?: return null
        val bytes = Base64.decode(sealed)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8).toCharArray()
    }.getOrNull()

    fun setPassphrase(passphrase: CharArray) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(String(passphrase).toByteArray(Charsets.UTF_8))
            prefs.edit { putString(KEY_PHRASE, Base64.encode(sealed)) }
        }
        passphrase.fill(' ')
    }

    /** Turns it all off and forgets the phrase: nothing of it is left behind. */
    fun forget() {
        prefs.edit { clear() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "seca_auto_backup"
        const val KEY_FOLDER = "folder"
        const val KEY_RHYTHM = "rhythm"
        const val KEY_KEEP = "keep"
        const val KEY_PORTABLE = "portable"
        const val KEY_PHRASE = "phrase"
        const val KEY_LAST_RUN = "last_run"
        const val KEY_LAST_FAILURE = "last_failure"
        const val DEFAULT_KEEP = 7

        const val ALIAS = "seca-auto-backup"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
