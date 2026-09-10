package com.seca.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.seca.core.design.SecaAppIdentity

/** Opens the dialer with [number] filled in; the user confirms the call there. */
internal fun dial(context: Context, number: String) =
    startSafely(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

internal fun sms(context: Context, number: String) =
    startSafely(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

internal fun email(context: Context, address: String) =
    startSafely(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))

/** Opens the sibling Seca app, or the system's dialer or messaging app until it exists. */
internal fun openSibling(context: Context, identity: SecaAppIdentity) {
    val packages = context.packageManager
    val intent = when (identity) {
        SecaAppIdentity.Contacts -> return
        SecaAppIdentity.Phone -> packages.getLaunchIntentForPackage("com.seca.phone")
            ?: Intent(Intent.ACTION_DIAL)
        SecaAppIdentity.Messages -> packages.getLaunchIntentForPackage("com.seca.messages")
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
    }
    startSafely(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun openAppSettings(context: Context) = startSafely(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

/** No app to handle the intent is a normal situation on a de-Googled phone, not a crash. */
private fun startSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}
