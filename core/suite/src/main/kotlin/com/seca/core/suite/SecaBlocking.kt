package com.seca.core.suite

import android.content.Context
import android.content.Intent
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.isInstalled

/**
 * Asking Seca Phone to block somebody, from another Seca app (#50).
 *
 * Android's blocked-number list may only be written by the default phone app, so Seca Contacts
 * cannot do it itself. It hands the numbers over instead, the same way the three apps already
 * pass the order of the bar and the shape of the photos between them, guarded by the signing
 * key in both directions.
 *
 * Undoing it is Android's own blocked-numbers screen, which Seca Phone's settings open, so this
 * is not a door that only opens one way.
 */
object SecaBlocking {

    const val ACTION = "com.seca.phone.action.BLOCK"
    const val EXTRA_NUMBERS = "numbers"
    const val EXTRA_BLOCK = "block"

    /** Whether there is an app here that could do it. */
    fun available(context: Context): Boolean = SecaAppIdentity.Phone.isInstalled(context)

    /** Asks Seca Phone to block, or let through again, every one of [numbers]. */
    fun request(context: Context, numbers: List<String>, block: Boolean) {
        if (numbers.isEmpty()) return
        runCatching {
            context.sendBroadcast(
                Intent(ACTION)
                    .setPackage(SecaAppIdentity.Phone.packageName)
                    .putExtra(EXTRA_NUMBERS, numbers.toTypedArray())
                    .putExtra(EXTRA_BLOCK, block),
                SuiteBackup.PERMISSION,
            )
        }
    }
}
