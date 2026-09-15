package com.seca.phone

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.seca.core.design.SecaAppIdentity

private const val SECA_CONTACTS = "com.seca.contacts"
private const val SECA_MESSAGES = "com.seca.messages"

/**
 * Places a call through Android's telephony stack, which also carries it
 * over Wi-Fi when the operator supports it. Returns false when calling is not
 * allowed yet, so the caller can ask. An emergency number is never blocked
 * here: Telecom hands it to the system's own emergency flow. With [account],
 * the call goes through that SIM without Android asking.
 */
internal fun placeCall(context: Context, number: String, account: PhoneAccountHandle? = null): Boolean {
    if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
    val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
    val extras = Bundle().apply { account?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) } }
    return runCatching { telecom.placeCall(Uri.fromParts("tel", number, null), extras) }.isSuccess
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

/** Opens the sibling Seca app, or the system's own app until Seca Messages exists. */
internal fun openSibling(context: Context, identity: SecaAppIdentity) {
    if (identity == SecaAppIdentity.Phone) return
    val target = if (identity == SecaAppIdentity.Contacts) SECA_CONTACTS else SECA_MESSAGES
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
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
    }
    startSafely(context, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Adds [number] to Android's own blocked list; only the default phone app may. */
internal fun blockNumber(context: Context, number: String): Boolean =
    BlockedNumberContract.canCurrentUserBlockNumbers(context) &&
        runCatching {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
        }.getOrDefault(false)

/**
 * Runs a hidden service code such as *#*#4636#*#* once it is fully typed.
 * Android only accepts them from the default phone app; elsewhere nothing happens.
 */
internal fun sendSpecialCode(context: Context, typed: String): Boolean {
    val code = Regex("^\\*#\\*#(\\d+)#\\*#\\*$").find(typed)?.groupValues?.get(1) ?: return false
    return runCatching { context.getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(code) }.isSuccess
}

/** Copies [number]; Android confirms it on screen by itself. */
internal fun copyNumber(context: Context, number: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.number), number))
}

/**
 * The system's Wi-Fi calling settings, then the mobile network settings that
 * hold the same switch on some phones. Android gives the first screen no
 * public name, so its action is spelled out.
 */
internal fun wifiCallingSettings(): List<Intent> = listOf(
    Intent("android.settings.WIFI_CALLING_SETTINGS"),
    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
)

/** The operator's call settings: forwarding, waiting, caller ID. */
internal fun callSettings(): Intent = Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)

internal fun voicemailSettings(): Intent = Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)

/** The system's list of blocked numbers, shared by every app. */
internal fun blockedNumbers(context: Context): Intent? =
    context.getSystemService(TelecomManager::class.java)?.createManageBlockedNumbersIntent()

/** Opens the first of these system screens the phone has; false when it has none of them. */
internal fun openSystemScreen(context: Context, intents: List<Intent>): Boolean =
    intents.any { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }

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
private fun startSafely(context: Context, intent: Intent, options: Bundle? = null) {
    runCatching { context.startActivity(intent, options) }
}
