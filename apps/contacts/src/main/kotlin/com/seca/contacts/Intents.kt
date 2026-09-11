package com.seca.contacts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.seca.core.design.SecaAppIdentity
import java.io.File

private const val SECA_PHONE = "com.seca.phone"
private const val SECA_PHONE_CALL = "com.seca.phone.action.CALL"
private const val PLACE_CALLS = "com.seca.permission.PLACE_CALLS"

/**
 * Calls [number] in one tap through Seca Phone, when it is installed and
 * signed with the same key. Otherwise opens the dialer with the number filled
 * in, for the user to confirm.
 */
internal fun dial(context: Context, number: String) {
    val tel = Uri.fromParts("tel", number, null)
    val call = Intent(SECA_PHONE_CALL, tel).setPackage(SECA_PHONE)
    val allowed = context.checkSelfPermission(PLACE_CALLS) == PackageManager.PERMISSION_GRANTED
    if (allowed && call.resolveActivity(context.packageManager) != null) {
        startSafely(context, call)
    } else {
        startSafely(context, Intent(Intent.ACTION_DIAL, tel))
    }
}

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

/**
 * Shares a contact as a .vcf file through the system share sheet. The file is
 * readable only by the app the user picks, and only for that share.
 */
internal fun shareVCard(context: Context, name: String, vcard: String) {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val safeName = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "contact" }
    val file = File(folder, "$safeName.vcf").apply { writeText(vcard) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/x-vcard")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startSafely(context, Intent.createChooser(send, "Partager le contact"))
}

internal fun openAppSettings(context: Context) = startSafely(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

/** No app to handle the intent is a normal situation on a de-Googled phone, not a crash. */
private fun startSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}
