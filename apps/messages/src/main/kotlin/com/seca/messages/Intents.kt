package com.seca.messages

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.ContactsContract
import android.provider.Settings
import com.seca.core.design.SecaAppIdentity

private const val SECA_CONTACTS = "com.seca.contacts"
private const val SECA_PHONE = "com.seca.phone"
private const val SECA_PHONE_CALL = "com.seca.phone.action.CALL"
private const val PLACE_CALLS = "com.seca.permission.PLACE_CALLS"

/** Opens the sibling Seca app, or the system's own app when it is not installed. */
internal fun openSibling(context: Context, identity: SecaAppIdentity) {
    if (identity == SecaAppIdentity.Messages) return
    val target = if (identity == SecaAppIdentity.Contacts) SECA_CONTACTS else SECA_PHONE
    val seca = context.packageManager.getLaunchIntentForPackage(target)
    if (seca != null) {
        // The Seca apps share this task and switch like tabs: instantly, both ways.
        // Coming back to an app already open brings its screen forward instead of stacking another.
        seca.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
        startSafely(context, seca)
        return
    }
    val fallback = if (identity == SecaAppIdentity.Contacts) {
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
    } else {
        Intent(Intent.ACTION_DIAL)
    }
    startSafely(context, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Calls in one tap through Seca Téléphone when it is installed with the same key, else opens the dialer. */
internal fun call(context: Context, number: String) {
    val tel = Uri.fromParts("tel", number, null)
    val direct = Intent(SECA_PHONE_CALL, tel).setPackage(SECA_PHONE)
    val allowed = context.checkSelfPermission(PLACE_CALLS) == PackageManager.PERMISSION_GRANTED
    if (allowed && direct.resolveActivity(context.packageManager) != null) {
        startSafely(context, direct)
    } else {
        startSafely(context, Intent(Intent.ACTION_DIAL, tel))
    }
}

/** Opens a contact's card in Seca Contacts, or in the system's contacts app. */
internal fun openContact(context: Context, contactId: Long, lookupKey: String) {
    val uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
    startPreferring(context, Intent(Intent.ACTION_VIEW, uri), SECA_CONTACTS)
}

/** Opens a new contact with [number] already filled in. */
internal fun addContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
    startPreferring(context, intent, SECA_CONTACTS)
}

/** ClipDescription.EXTRA_IS_SENSITIVE, written out: Android 13 honours it, older versions ignore it. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/** Copies a verification code, marked sensitive so Android does not show it in the clipboard preview. */
internal fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("Code", text).apply {
        description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** Copies [text]; Android confirms it on screen by itself. */
internal fun copyText(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun openAppSettings(context: Context) = startSafely(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

/** Android's own notification settings for this app: sound, vibration, lock screen. */
internal fun openNotificationSettings(context: Context) = startSafely(
    context,
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
)

/** Where the owner lets Seca Messages send a scheduled message on the minute. */
internal fun openExactAlarmSettings(context: Context) = startSafely(
    context,
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)),
)

internal fun openDefaultAppsSettings(context: Context) =
    startSafely(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

/** Sends [intent] to the Seca app [preferred] when it is installed and handles it, else to any app. */
private fun startPreferring(context: Context, intent: Intent, preferred: String) {
    val targeted = Intent(intent).setPackage(preferred)
    val chosen = if (targeted.resolveActivity(context.packageManager) != null) targeted else intent
    startSafely(context, chosen)
}

/** No app to handle the intent is a normal situation on a de-Googled phone, not a crash. */
private fun startSafely(context: Context, intent: Intent, options: Bundle? = null) {
    runCatching { context.startActivity(intent, options) }
}
