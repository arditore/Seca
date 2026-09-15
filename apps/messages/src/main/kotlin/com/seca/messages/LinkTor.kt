package com.seca.messages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSettingRow
import androidx.compose.ui.res.stringResource

/** In the Seca Link settings: reaching the relays through Tor, with Orbot, so they never see this phone's address. */
@Composable
internal fun TorRow(link: LinkUi, viewModel: MessagesViewModel, index: Int, count: Int) {
    // Orbot may have been started or installed while the app was away.
    LifecycleResumeEffect(link.useTor) {
        if (link.useTor) viewModel.refreshTor()
        onPauseOrDispose { }
    }
    val needsOrbot = link.useTor && link.orbotRunning == false
    SecaGroupItem(index = index, count = count) {
        SecaSettingRow(
            icon = SecaIcons.VisibilityOff,
            title = stringResource(R.string.use_tor),
            subtitle = when {
                !link.useTor -> stringResource(R.string.tor_off_hint)
                !link.orbotInstalled && link.orbotRunning != null -> stringResource(R.string.orbot_missing)
                link.orbotRunning == false -> stringResource(R.string.orbot_not_responding)
                link.orbotRunning == true -> stringResource(R.string.tor_on)
                else -> stringResource(R.string.checking_orbot)
            },
            modifier = Modifier.toggleable(value = link.useTor, role = Role.Switch, onValueChange = viewModel::setUseTor),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (needsOrbot) {
                        TextButton(onClick = viewModel::openOrbot) { Text(stringResource(if (link.orbotInstalled) R.string.open else R.string.get)) }
                    }
                    Switch(checked = link.useTor, onCheckedChange = null)
                }
            },
        )
    }
}
