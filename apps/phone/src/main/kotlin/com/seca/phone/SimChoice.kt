package com.seca.phone

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.segmentShape

/** A SIM able to place calls. [slot] counts from 1, when Android tells it. */
internal data class Sim(val handle: PhoneAccountHandle, val label: String, val slot: Int?) {
    val name: String get() = if (slot != null) "SIM $slot · $label" else label
}

/** The SIMs able to place calls, in slot order. Empty without the phone-state permission, which comes with being the phone app. */
internal fun callingSims(context: Context): List<Sim> {
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    return runCatching {
        telecom.callCapablePhoneAccounts.map { handle ->
            val label = telecom.getPhoneAccount(handle)?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM"
            val slot = runCatching {
                val subscription = telephony?.getSubscriptionId(handle) ?: return@runCatching null
                subscriptions?.getActiveSubscriptionInfo(subscription)?.simSlotIndex?.plus(1)
            }.getOrNull()
            Sim(handle, label, slot)
        }.sortedBy { it.slot ?: Int.MAX_VALUE }
    }.getOrDefault(emptyList())
}

/** The SIM each contact is called with, once the owner chose it. Kept on this phone only. */
internal class SimPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("seca_sim_choice", Context.MODE_PRIVATE)

    operator fun get(key: String): PhoneAccountHandle? {
        val saved = prefs.getString(key, null) ?: return null
        val component = ComponentName.unflattenFromString(saved.substringBefore(SEPARATOR)) ?: return null
        return PhoneAccountHandle(component, saved.substringAfter(SEPARATOR))
    }

    operator fun set(key: String, handle: PhoneAccountHandle) {
        prefs.edit { putString(key, handle.componentName.flattenToString() + SEPARATOR + handle.id) }
    }

    fun forget(key: String) {
        prefs.edit { remove(key) }
    }

    private companion object {
        const val SEPARATOR = '|'
    }
}

/** A contact is remembered by its lookup key, which outlives a change of number; anyone else by their number. */
internal fun simKeyOf(lookupKey: String?, number: String, numbers: PhoneNumbers): String =
    if (lookupKey.isNullOrEmpty()) "number:${numbers.key(number)}" else "contact:$lookupKey"

/** The lookup key and name of the contact [number] belongs to; null for someone not in the contacts. */
internal fun contactFor(context: Context, number: String): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
        arrayOf(PhoneLookup.LOOKUP_KEY, PhoneLookup.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null }
}.getOrNull()

/** Before a call: the SIM to place it with, or the choice to offer. */
internal sealed interface SimRoute {
    /** [account] null lets Android pick, as with a single SIM. */
    data class Direct(val account: PhoneAccountHandle?) : SimRoute

    data class Ask(val number: String, val sims: List<Sim>, val key: String, val contactName: String?) : SimRoute
}

/**
 * With several SIMs, a contact is called with the SIM chosen for them, and
 * the owner is asked otherwise. An emergency number never waits for a choice.
 */
internal fun routeCall(context: Context, number: String): SimRoute {
    val sims = callingSims(context)
    if (sims.size < 2 || isEmergency(context, number)) return SimRoute.Direct(null)
    val contact = contactFor(context, number)
    val key = simKeyOf(contact?.first, number, PhoneNumbers(PhoneNumbers.detectRegion(context)))
    val preferred = SimPreferences(context)[key]?.takeIf { saved -> sims.any { it.handle == saved } }
    return if (preferred != null) SimRoute.Direct(preferred) else SimRoute.Ask(number, sims, key, contact?.second?.ifEmpty { null })
}

private fun isEmergency(context: Context, number: String): Boolean =
    runCatching { context.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(number) == true }.getOrDefault(true)

/**
 * Which SIM to use. With [rememberLabel], a box keeps the choice for the next
 * calls; it starts ticked, since a contact is usually called with the same SIM.
 */
@Composable
internal fun SimChooserDialog(
    sims: List<Sim>,
    title: String,
    rememberLabel: String?,
    onDismiss: () -> Unit,
    onPick: (sim: Sim, remember: Boolean) -> Unit,
) {
    var keep by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Phone, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sims.forEachIndexed { index, sim ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(segmentShape(index, sims.size, outer = 20.dp))
                            .background(colors.surfaceContainerHighest)
                            .clickable(onClickLabel = sim.name) { onPick(sim, keep) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                        ) {
                            Text(
                                text = sim.slot?.toString() ?: sim.label.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onPrimaryContainer,
                            )
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(sim.label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                            sim.slot?.let {
                                Text("SIM $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                rememberLabel?.let { label ->
                    RememberChoice(keep, label, onCheckedChange = { keep = it }, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** The box that keeps a SIM for a contact's next calls, its whole line a touch target. */
@Composable
internal fun RememberChoice(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** On a contact's page, with several SIMs: the SIM their calls use, to change or to forget. */
@Composable
internal fun SimPreferenceRow(number: String, lookupKey: String?, name: String, numbers: PhoneNumbers) {
    val context = LocalContext.current
    val sims = remember { callingSims(context) }
    if (sims.size < 2) return
    val preferences = remember { SimPreferences(context) }
    val key = remember(number, lookupKey) { simKeyOf(lookupKey, number, numbers) }
    var preferred by remember(key) { mutableStateOf(preferences[key]?.let { saved -> sims.firstOrNull { it.handle == saved } }) }
    var choosing by remember { mutableStateOf(false) }
    Column {
        SecaSectionLabel("Carte SIM")
        SecaGroupItem(index = 0, count = 1, onClick = { choosing = true }) {
            SecaSettingRow(
                icon = SecaIcons.Phone,
                title = preferred?.name ?: "Demander à chaque appel",
                subtitle = if (preferred != null) "Pour appeler $name" else "Toucher pour choisir la SIM qui appelle $name",
                trailing = preferred?.let {
                    {
                        TextButton(
                            onClick = {
                                preferences.forget(key)
                                preferred = null
                            },
                        ) { Text("Oublier") }
                    }
                },
            )
        }
    }
    if (choosing) {
        SimChooserDialog(
            sims = sims,
            title = "SIM pour $name",
            rememberLabel = null,
            onDismiss = { choosing = false },
            onPick = { sim, _ ->
                preferences[key] = sim.handle
                preferred = sim
                choosing = false
            },
        )
    }
}
