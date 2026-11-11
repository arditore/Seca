package com.seca.core.suite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SuiteOrder
import com.seca.core.design.component.IconBadge
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.icon
import com.seca.core.design.identityOf
import com.seca.core.design.isInstalled
import com.seca.core.design.label

/**
 * In each Seca app's settings: which order the three stand in along the bottom.
 *
 * Asked for by somebody who reaches for the telephone and keeps finding the contacts. Only
 * shown where there is more than one Seca app on the phone — an order of one is not an order,
 * and in that case the bar is not drawn at all either.
 */
@Composable
fun SuiteOrderSection() {
    val context = LocalContext.current
    val order = SuiteOrder.current()
    val here = remember(order) {
        order.filter { it == identityOf(context) || it.isInstalled(context) }
    }
    if (here.size < 2) return

    SecaSectionLabel(stringResource(R.string.suite_order_section))
    Column {
        Text(
            text = stringResource(R.string.suite_order_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        here.forEachIndexed { index, app ->
            SecaGroupItem(index = index, count = here.size) {
                val name = stringResource(app.label)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    IconBadge(app.icon)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    // No arrow where there is nowhere to go, and the gap kept, so the other
                    // arrow stays under the one above it rather than sliding across.
                    if (index > 0) {
                        IconButton(onClick = { SuiteOrderSync.set(context, moved(here, index, -1)) }) {
                            Icon(SecaIcons.ArrowUp, contentDescription = stringResource(R.string.suite_order_up, name))
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    if (index < here.size - 1) {
                        IconButton(onClick = { SuiteOrderSync.set(context, moved(here, index, 1)) }) {
                            Icon(SecaIcons.ArrowDown, contentDescription = stringResource(R.string.suite_order_down, name))
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

/** The list with the app at [from] moved by [by] places. */
private fun moved(order: List<SecaAppIdentity>, from: Int, by: Int): List<SecaAppIdentity> {
    val to = (from + by).coerceIn(0, order.size - 1)
    if (to == from) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Keeps the three apps' copies of the order in step.
 *
 * Whichever app is changed writes its own copy and then tells the others, which is the only way
 * across: an app cannot write another app's settings. The message goes to each sibling by name
 * and is guarded both ways by the Seca signing key, so nothing else can send it and nothing
 * else can hear it.
 */
object SuiteOrderSync {

    internal const val ACTION = "com.seca.suite.action.ORDER"
    private const val EXTRA_ORDER = "order"
    private const val SEPARATOR = ","

    fun set(context: Context, order: List<SecaAppIdentity>) {
        SuiteOrder.store(context, order)
        val said = order.joinToString(SEPARATOR) { it.name }
        SecaAppIdentity.entries
            .filterNot { it.packageName == context.packageName }
            .forEach { sibling ->
                runCatching {
                    context.sendBroadcast(
                        Intent(ACTION).setPackage(sibling.packageName).putExtra(EXTRA_ORDER, said),
                        SuiteBackup.PERMISSION,
                    )
                }
            }
    }

    /** Hears a sibling's change. Guarded in the manifest: only the Seca apps reach it. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val order = intent.getStringExtra(EXTRA_ORDER)
                ?.split(SEPARATOR)
                ?.mapNotNull { name -> SecaAppIdentity.entries.firstOrNull { it.name == name } }
                ?.takeIf { it.isNotEmpty() }
                ?: return
            SuiteOrder.store(context, order)
        }
    }
}
