package com.seca.core.suite

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

/**
 * Whether the launcher shows "Contacts" or "Seca Contacts" (#33).
 *
 * An app's label is fixed in its manifest and cannot be changed while it runs, so the launcher
 * entry lives on two aliases of the same activity, one labelled each way, and exactly one of
 * them is enabled. `MainActivity` itself is never disabled: notifications and the sibling apps
 * start it by name, and a disabled component cannot be started at all.
 *
 * An app can only enable or disable its own components, so the choice is kept here and each
 * app applies it to itself — the one that is changed does so at once, and tells the others,
 * which do the same.
 */
object PlainNames {

    fun enabled(context: Context): Boolean =
        notes(context).getBoolean(KEY, false)

    /** Remembers the choice and applies it to this app's own launcher entry. */
    fun apply(context: Context, plain: Boolean) {
        notes(context).edit { putBoolean(KEY, plain) }
        val packages = context.packageManager
        // Enabled first, disabled second. The other way round there is an instant with no
        // launcher entry at all, and a launcher that reads the list in that instant loses the
        // icon until something else makes it look again.
        setEnabled(packages, context, if (plain) PLAIN else SECA, enabled = true)
        setEnabled(packages, context, if (plain) SECA else PLAIN, enabled = false)
    }

    private fun setEnabled(packages: PackageManager, context: Context, alias: String, enabled: Boolean) {
        val component = ComponentName(context.packageName, "${context.packageName}$alias")
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching { packages.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
    }

    private fun notes(context: Context) =
        context.getSharedPreferences("suite-names", Context.MODE_PRIVATE)

    private const val KEY = "plain"
    private const val SECA = ".LauncherSeca"
    private const val PLAIN = ".LauncherPlain"
}
