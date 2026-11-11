package com.seca.core.design.component

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.seca.core.design.LocalBlackTheme
import com.seca.core.design.SecaAppIdentity
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.seca.core.design.SuiteOrder
import com.seca.core.design.icon
import com.seca.core.design.isInstalled
import com.seca.core.design.label

/**
 * The bar that ties the three Seca apps together.
 *
 * Each app shows it with its own [current] identity selected; tapping another
 * entry calls [onSelect]. This component deliberately knows nothing about
 * `Intent`s — the app decides what selecting a sibling does, which keeps
 * `:core:design` free of Android navigation logic and lets the catalog reuse
 * it as a preview switcher.
 */
@Composable
fun SecaSuiteBar(
    current: SecaAppIdentity,
    onSelect: (SecaAppIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Whatever order the owner put them in, and only the apps that are actually on the phone:
    // somebody who installed one of the three was being offered two entries that led to a
    // shop, which is an advertisement rather than navigation.
    val order = SuiteOrder.current()
    val shown = remember(current, order) {
        order.filter { it == current || it.isInstalled(context) }
    }
    // One app on its own needs no bar to tell it apart from itself.
    if (shown.size < 2) return
    NavigationBar(
        modifier = modifier,
        // This bar is what shows behind the phone's own navigation bar, so under the black
        // theme it has to be black too: Material would otherwise leave a grey strip across
        // the bottom of the screen.
        containerColor = if (LocalBlackTheme.current) {
            MaterialTheme.colorScheme.surface
        } else {
            NavigationBarDefaults.containerColor
        },
    ) {
        shown.forEach { identity ->
            NavigationBarItem(
                selected = identity == current,
                onClick = { onSelect(identity) },
                icon = {
                    // No contentDescription: NavigationBarItem merges the icon
                    // and its text label into one semantic node, so describing
                    // the icon too would make a screen reader announce each
                    // entry twice.
                    Icon(identity.icon, contentDescription = null)
                },
                label = { Text(stringResource(identity.label)) },
                alwaysShowLabel = true,
            )
        }
    }
}
