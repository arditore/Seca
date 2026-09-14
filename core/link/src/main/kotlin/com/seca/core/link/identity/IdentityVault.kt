package com.seca.core.link.identity

import android.content.Context
import com.seca.core.link.storage.Sealer
import java.io.File

/** Keeps this phone's identity sealed in the app's private storage, created the first time it is needed. */
class IdentityVault(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun exists(): Boolean = file.exists()

    @Synchronized
    fun loadOrCreate(): LinkIdentity = load() ?: LinkIdentity.generate().also(::save)

    private fun load(): LinkIdentity? {
        val plain = Sealer.read(file) ?: return null
        return try {
            LinkIdentity.deserialize(plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun save(identity: LinkIdentity) {
        val plain = identity.serialize()
        try {
            Sealer.write(file, plain)
        } finally {
            plain.fill(0)
        }
    }

    private companion object {
        const val FILE_NAME = "seca-link-identity"
    }
}
