package com.seca.messages.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager

/** A SIM a text can be sent from. [slot] counts from 1, as the phone prints it on the tray. */
data class SendingSim(val subscriptionId: Int, val label: String, val slot: Int)

/**
 * The SIMs this phone can send a text from, in slot order.
 *
 * Empty on a phone with one SIM, and empty when the list cannot be read — which is the answer
 * rather than an error: the app simply does not offer a choice it cannot make good on, and sends
 * on whichever SIM the phone's own settings name, as it always did.
 *
 * Reading the list needs `READ_PHONE_STATE`. Android grants it to whichever app the owner made
 * their messaging app, along with the right to read and send texts, so no separate question is
 * asked; an app that is not the messaging app cannot send a text at all.
 */
internal fun sendingSims(context: Context): List<SendingSim> {
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val subscriptions = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
    return runCatching {
        subscriptions.activeSubscriptionInfoList.orEmpty()
            // Only what is in a slot. A phone lists more than its SIMs here: a Pixel also
            // reports its satellite profile and any eSIM that is loaded but not switched on,
            // and neither can send a text. Both come back with no slot, which is the test.
            .filter { it.simSlotIndex >= 0 }
            .map { info ->
                SendingSim(
                    subscriptionId = info.subscriptionId,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM",
                    slot = info.simSlotIndex + 1,
                )
            }
            .sortedBy(SendingSim::slot)
    }.getOrDefault(emptyList())
}
