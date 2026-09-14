package com.seca.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager

/** A SIM or eSIM in the phone, with its number when the operator wrote it on the card. */
data class SimLine(val slot: Int, val label: String, val number: String?, val isEsim: Boolean)

/** The permissions reading the lines needs; asked only when the user chooses to. */
internal val SimPermissions = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)

/**
 * The phone's active lines. Empty without the permissions or without a SIM.
 * Many operators leave the number off the card, so a line may come back
 * without one: the owner then types it in "Ma fiche".
 */
@Suppress("DEPRECATION") // SubscriptionInfo.number is the only way to read it before Android 13.
internal fun readSimLines(context: Context): List<SimLine> {
    val granted = PackageManager.PERMISSION_GRANTED
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != granted ||
        context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != granted
    ) {
        return emptyList()
    }
    val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
    return manager.activeSubscriptionInfoList.orEmpty()
        .sortedBy { it.simSlotIndex }
        .map { info ->
            SimLine(
                slot = info.simSlotIndex,
                label = (info.displayName ?: info.carrierName)?.toString()?.takeIf { it.isNotBlank() } ?: "SIM",
                number = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) manager.getPhoneNumber(info.subscriptionId) else info.number
                }.getOrNull()?.takeIf { it.isNotBlank() },
                isEsim = info.isEmbedded,
            )
        }
}
