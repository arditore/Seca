package com.seca.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import com.seca.core.design.SecaAppIdentity

private const val SECA_CONTACTS = "com.seca.contacts"
private const val SECA_MESSAGES = "com.seca.messages"

/**
 * Places a call through Android's telephony stack, which also carries it
 * over Wi-Fi when the operator supports it. Returns false when calling is not
 * allowed yet, so the caller can ask. An emergency number is never blocked
 * here: Telecom hands it to the system's own emergency flow.
 */
internal fun placeCall(context: Context, number: String): Boolean {
    if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
    val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
    return runCatching { telecom.placeCall(Uri.fromParts("tel", number, null), Bundle()) }.isSuccess
}

/** Calls the voicemail, as a long press on 1 does on every dialer. */
internal fun callVoicemail(context: Context): Boolean {
    if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
    val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
    return runCatching { telecom.placeCall(Uri.fromParts("voicemail", "", null), Bundle()) }.isSuccess
}

/** Writes to [number] in Seca Messages, or the system's messaging app until it exists. */
internal fun sms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
    startPreferring(context, intent, SECA_MESSAGES)
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

/** Opens the sibling Seca app, or the system's messaging app until Seca Messages exists. */
internal fun openSibling(context: Context, identity: SecaAppIdentity) {
    val packages = context.packageManager
    val intent = when (identity) {
        SecaAppIdentity.Phone -> return
        SecaAppIdentity.Contacts -> packages.getLaunchIntentForPackage(SECA_CONTACTS)
            ?: Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        SecaAppIdentity.Messages -> packages.getLaunchIntentForPackage(SECA_MESSAGES)
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
    }
    startSafely(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun openAppSettings(context: Context) = startSafely(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

/** Sends [intent] to the Seca app [preferred] when it is installed and handles it, else to any app. */
private fun startPreferring(context: Context, intent: Intent, preferred: String) {
    val targeted = Intent(intent).setPackage(preferred)
    val chosen = if (targeted.resolveActivity(context.packageManager) != null) targeted else intent
    startSafely(context, chosen)
}

/** No app to handle the intent is a normal situation on a de-Googled phone, not a crash. */
private fun startSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}
