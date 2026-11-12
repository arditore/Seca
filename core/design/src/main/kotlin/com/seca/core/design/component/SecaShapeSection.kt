package com.seca.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.R
import com.seca.core.design.SecaAvatarShape
import com.seca.core.design.SecaIcons

/**
 * The shape every face is cut to, with its heading, for all three apps' settings.
 *
 * Written once rather than three times, as the palette is: the same setting in the same place
 * looking like two different things is how the palette block drifted apart before.
 */
@Composable
fun SecaShapeSection(
    shape: SecaAvatarShape?,
    onPick: (SecaAvatarShape?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            IconBadge(SecaIcons.Contacts)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.design_shape_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (shape == null) R.string.design_shape_hint_playful else R.string.design_shape_hint_one,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SecaShapePicker(chosen = shape, onPick = onPick, modifier = Modifier.padding(top = 8.dp))
    }
}
