package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.R
import com.seca.core.design.SecaIcons

/**
 * One thing that can be done to the rows now chosen.
 *
 * An action nobody could carry out is left out of the list rather than put in it greyed out:
 * a dead button still reads as something the app ought to have been able to do.
 */
data class SecaChoiceAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** Past this many, the actions fold into a menu rather than squeeze the count off the bar. */
private const val INLINE_ACTIONS = 3

/**
 * The bar that stands in for the top bar while rows are chosen.
 *
 * Leaving, how many are chosen and choosing the lot are the same in every list in the suite,
 * so they are written once here; each list brings its own actions. Two hand-written copies of
 * this bar had already drifted into saying the same thing two ways.
 *
 * Six icon buttons and a number do not fit across a narrow phone: they fit on a Pixel and clip
 * the number at 360dp, which is most of the phones this has to work on. So only the first few
 * actions stay on the bar and the rest go under one more button.
 */
@Composable
fun SecaChoiceBar(
    count: Int,
    onClear: () -> Unit,
    actions: List<SecaChoiceAction>,
    modifier: Modifier = Modifier,
    onChooseAll: (() -> Unit)? = null,
    allChosen: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val inline = if (actions.size > INLINE_ACTIONS) actions.take(INLINE_ACTIONS) else actions
    val folded = actions.drop(inline.size)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onClear) {
            Icon(SecaIcons.Close, contentDescription = stringResource(R.string.design_cancel))
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (onChooseAll != null) {
            IconButton(onClick = if (allChosen) onClear else onChooseAll) {
                // The same mark in both states, never the cross: a second cross beside the one
                // that leaves the choosing is two meanings drawn identically. What says
                // everything is chosen is that every row is ticked.
                Icon(
                    imageVector = SecaIcons.SelectAll,
                    contentDescription = stringResource(
                        if (allChosen) R.string.design_choose_none else R.string.design_choose_all,
                    ),
                )
            }
        }
        inline.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(action.icon, contentDescription = action.label)
            }
        }
        if (folded.isNotEmpty()) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = SecaIcons.MoreVert,
                        contentDescription = stringResource(R.string.design_more_options),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    folded.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}
