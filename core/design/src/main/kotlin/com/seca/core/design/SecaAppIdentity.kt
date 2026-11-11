package com.seca.core.design

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Which Seca app is being themed.
 *
 * All three share tokens, shapes, typography and motion; only the accent
 * differs, so the family reads as one system while each app stays
 * recognisable at a glance.
 */
enum class SecaAppIdentity(val packageName: String) {
    Contacts("com.seca.contacts"),
    Phone("com.seca.phone"),
    Messages("com.seca.messages"),
}

/** The mark that stands for an app, the same one in every list it appears in. */
val SecaAppIdentity.icon: ImageVector
    get() = when (this) {
        SecaAppIdentity.Contacts -> SecaIcons.Contacts
        SecaAppIdentity.Phone -> SecaIcons.Phone
        SecaAppIdentity.Messages -> SecaIcons.Messages
    }

/** The name shown for an app, in the phone's language; identifiers stay English. */
val SecaAppIdentity.label: Int
    get() = when (this) {
        SecaAppIdentity.Contacts -> R.string.design_app_contacts
        SecaAppIdentity.Phone -> R.string.design_app_phone
        SecaAppIdentity.Messages -> R.string.design_app_messages
    }

/** Which of the three this is. Null in anything else, such as the catalog. */
fun identityOf(context: Context): SecaAppIdentity? =
    SecaAppIdentity.entries.firstOrNull { it.packageName == context.packageName }

/**
 * Whether a sibling app is on the phone.
 *
 * The manifests name all three, so this may be asked. Somebody who installed one of the three
 * was being offered two entries that led to a shop, which is an advertisement rather than
 * navigation.
 */
fun SecaAppIdentity.isInstalled(context: Context): Boolean =
    runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }
        .getOrDefault(false)
