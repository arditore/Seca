package com.seca.core.design.component

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.seca.core.design.LocalBlackTheme
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import androidx.compose.ui.res.stringResource
import com.seca.core.design.R
import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
    // This bar is what shows behind the phone's own navigation bar, so under the black theme it
    // has to be black as well: Material would otherwise leave a grey strip across the bottom.
    // Only the apps that are actually here. Somebody who installed one of the three was being
    // offered two tabs that led to a shop, which is an advertisement rather than navigation.
    val context = LocalContext.current
    val shown = remember(current) {
        SecaAppIdentity.entries.filter { it == current || installed(context, it) }
    }
    // One app on its own needs no bar to tell it apart from itself.
    if (shown.size < 2) return
    NavigationBar(
        modifier = modifier,
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

/** Whether a sibling app is on the phone. The manifests name all three, so this may be asked. */
private fun installed(context: Context, identity: SecaAppIdentity): Boolean =
    runCatching { context.packageManager.getLaunchIntentForPackage(identity.packageName) != null }
        .getOrDefault(false)
