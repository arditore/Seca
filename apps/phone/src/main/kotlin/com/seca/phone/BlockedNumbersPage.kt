package com.seca.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaDialog
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSettingRow
import kotlinx.coroutines.launch

/**
 * The numbers this phone refuses, inside the app rather than behind a trip to Android's own
 * screen.
 *
 * The list itself is still Android's, shared with every app, which is the whole value of it: a
 * number blocked here is blocked for the phone. What changes is that reading it, adding to it
 * and undoing it no longer send somebody out of the app they were in.
 *
 * Only the default phone app may touch that list, so this page is offered only where Seca
 * Phone is it; the row that leads here is not drawn otherwise.
 */
@Composable
internal fun BlockedNumbersPage(ui: PhoneUi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped after every change, which is what re-reads the list: the provider has no flow and
    // the list is short enough to read again whole.
    var reload by remember { mutableIntStateOf(0) }
    val blocked by produceState(initialValue = emptyList<BlockedNumber>(), reload) {
        value = BlockedNumbers.all(context)
    }
    var adding by remember { mutableStateOf(false) }

    if (blocked.isEmpty()) {
        SecaHint(stringResource(R.string.blocked_numbers_none))
    } else {
        blocked.forEachIndexed { index, entry ->
            SecaGroupItem(index = index, count = blocked.size) {
                SecaSettingRow(
                    icon = SecaIcons.Block,
                    title = ui.numbers.display(entry.number),
                    // The name where this phone knows one, so a list of digits is a list of people.
                    subtitle = ui.index?.find(entry.number)?.contact?.displayName,
                    trailing = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    BlockedNumbers.remove(context, entry.id)
                                    reload++
                                }
                            },
                        ) {
                            Icon(SecaIcons.Close, contentDescription = stringResource(R.string.unblock))
                        }
                    },
                )
            }
        }
    }
    SecaGroupItem(index = 0, count = 1, onClick = { adding = true }) {
        SecaSettingRow(
            icon = SecaIcons.Add,
            title = stringResource(R.string.block_a_number),
            subtitle = stringResource(R.string.block_a_number_hint),
        )
    }
    SecaHint(stringResource(R.string.blocked_numbers_shared))

    if (adding) {
        var typed by remember { mutableStateOf("") }
        SecaDialog(
            onDismiss = { adding = false },
            icon = SecaIcons.Block,
            title = stringResource(R.string.block_a_number),
            confirmLabel = stringResource(R.string.block),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                val number = typed
                adding = false
                scope.launch {
                    BlockedNumbers.add(context, number)
                    reload++
                }
            },
            content = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.number)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}
