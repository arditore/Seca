package com.seca.contacts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.seca.core.contacts.SimContacts
import com.seca.core.contacts.SimEntry
import com.seca.core.contacts.SimPhonebook
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import kotlinx.coroutines.launch

/**
 * What the SIM card itself holds (#97).
 *
 * A card is not an account and not a contacts database: each entry is a name, a number and
 * sometimes an email, at a numbered place in the card's own phonebook. That is what this page
 * shows, and it shows the number of the place too, because that is what another phone will
 * call the same entry.
 *
 * A card is read when the page is opened and again whenever a card is put in or taken out, so
 * swapping cards does not leave the page describing one that is no longer there.
 */
@Composable
internal fun SimContactsPage(viewModel: ContactsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(simReadable(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = simReadable(context)
        reload++
    }
    val cards by produceState(initialValue = emptyList<SimPhonebook>(), reload, allowed) {
        value = if (allowed) SimContacts.read(context) else emptyList()
    }
    // A card put in or taken out changes the list; the page follows rather than going stale.
    WatchingCards(enabled = allowed) { reload++ }

    val written = stringResource(R.string.sim_added)
    val refused = stringResource(R.string.sim_write_failed)
    val removed = stringResource(R.string.sim_removed)
    val pickNumber = rememberLauncherForActivityResult(PickPhoneNumber()) { picked ->
        val card = cards.firstOrNull() ?: return@rememberLauncherForActivityResult
        if (picked == null) return@rememberLauncherForActivityResult
        scope.launch {
            val line = numberAt(context, picked)
            val done = line != null &&
                SimContacts.add(context, card.subscriptionId, line.first, line.second)
            viewModel.say(if (done) written else refused)
            reload++
        }
    }

    if (!allowed) {
        SecaGroupItem(index = 0, count = 1, onClick = { ask.launch(SimPermissions) }) {
            SecaSettingRow(
                icon = SecaIcons.Sim,
                title = stringResource(R.string.sim_allow_reading),
                subtitle = stringResource(R.string.sim_allow_reading_hint),
            )
        }
        SecaHint(stringResource(R.string.sim_contacts_note))
        return
    }
    if (cards.isEmpty()) {
        SecaHint(stringResource(R.string.no_sim))
        return
    }

    cards.forEach { card ->
        SecaSectionLabel("${stringResource(R.string.sim_slot, card.slot)} · ${card.label}")
        when {
            !card.readable -> SecaHint(stringResource(R.string.sim_unreadable))
            card.entries.isEmpty() -> SecaHint(stringResource(R.string.sim_contacts_none))
            else -> card.entries.forEachIndexed { row, entry ->
                SecaGroupItem(index = row, count = card.entries.size) {
                    SimEntryRow(
                        entry = entry,
                        onCopy = {
                            scope.launch {
                                viewModel.saveFromSim(entry)
                                reload++
                            }
                        },
                        onRemove = {
                            scope.launch {
                                val gone = SimContacts.remove(context, entry)
                                viewModel.say(if (gone) removed else refused)
                                reload++
                            }
                        },
                    )
                }
            }
        }
    }
    if (SimContacts.canWrite(context) && cards.isNotEmpty()) {
        SecaGroupItem(index = 0, count = 1, onClick = { pickNumber.launch(Unit) }) {
            SecaSettingRow(
                icon = SecaIcons.PersonAdd,
                title = stringResource(R.string.sim_add_contact),
                subtitle = stringResource(R.string.sim_add_contact_hint),
            )
        }
    }
    SecaHint(stringResource(R.string.sim_contacts_note))
}

/** One entry of the card: what it says, where it sits, and the two things that can be done with it. */
@Composable
private fun SimEntryRow(entry: SimEntry, onCopy: () -> Unit, onRemove: () -> Unit) {
    SecaSettingRow(
        icon = SecaIcons.Sim,
        title = entry.name.ifBlank { entry.number },
        subtitle = listOfNotNull(
            stringResource(R.string.sim_entry_index, entry.index),
            entry.number.takeIf { entry.name.isNotBlank() },
            entry.email.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        trailing = {
            IconButton(onClick = onCopy) {
                Icon(SecaIcons.PersonAdd, contentDescription = stringResource(R.string.sim_copy_to_phone))
            }
            IconButton(onClick = onRemove) {
                Icon(SecaIcons.Delete, contentDescription = stringResource(R.string.sim_remove_from_card))
            }
        },
    )
}

/**
 * Watches for a card being put in or taken out, and for the page coming back to the front.
 *
 * The card is watched through the subscription list rather than a broadcast: the broadcast
 * that says a card changed is reserved to the system's own apps, while this listener is given
 * to anyone holding the permission the page already needs.
 *
 * What the card holds has no such signal at all: a phonebook changed by another app, or by the
 * phone's own contacts app, is announced to nobody. So the page reads it again every time it
 * returns to the front, which is the moment a stale list would otherwise be believed.
 */
@Composable
private fun WatchingCards(enabled: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val changed by rememberUpdatedState(onChanged)
    DisposableEffect(enabled, owner) {
        val manager = if (enabled) context.getSystemService(SubscriptionManager::class.java) else null
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() = changed()
        }
        runCatching { manager?.addOnSubscriptionsChangedListener(context.mainExecutor, listener) }
        val returning = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) changed()
        }
        owner.lifecycle.addObserver(returning)
        onDispose {
            runCatching { manager?.removeOnSubscriptionsChangedListener(listener) }
            owner.lifecycle.removeObserver(returning)
        }
    }
}

/** Whether the cards can be listed and read at all. */
private fun simReadable(context: Context): Boolean =
    SimPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/**
 * The phone's own picker, asking for a number rather than a contact.
 *
 * A contact can hold a dozen numbers and a card holds one, so the choice of which number goes
 * onto the card belongs to the owner and not to this app.
 */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

/** The name and the number of a picked row, or null when the phone hands back neither. */
private fun numberAt(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val number = cursor.getString(1).orEmpty()
        if (number.isBlank()) null else cursor.getString(0).orEmpty() to number
    }
}.getOrNull()
