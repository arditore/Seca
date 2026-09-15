package com.seca.core.design.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import androidx.compose.ui.res.stringResource
import com.seca.core.design.R

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
    NavigationBar(modifier = modifier) {
        SecaAppIdentity.entries.forEach { identity ->
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

private val SecaAppIdentity.icon: ImageVector
    get() = when (this) {
        SecaAppIdentity.Contacts -> SecaIcons.Contacts
        SecaAppIdentity.Phone -> SecaIcons.Phone
        SecaAppIdentity.Messages -> SecaIcons.Messages
    }

/** The name shown under each entry, in the phone's language; identifiers stay English. */
internal val SecaAppIdentity.label: Int
    get() = when (this) {
        SecaAppIdentity.Contacts -> R.string.design_app_contacts
        SecaAppIdentity.Phone -> R.string.design_app_phone
        SecaAppIdentity.Messages -> R.string.design_app_messages
    }
