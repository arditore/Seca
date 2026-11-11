package com.seca.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.R
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette

/**
 * The row of Seca palettes, with its heading.
 *
 * One composable rather than three copies. Seca Contacts had the heading and the icon, while
 * Seca Phone and Seca Messages drew four bare circles floating under the switch above them —
 * the same setting, in the same place, looking like two different things. Copies drift; a
 * shared one cannot.
 *
 * The explanation changes with the switch above it, because what a palette does depends on
 * whether the wallpaper is already colouring the app.
 */
@Composable
fun SecaPaletteSection(
    /** The palette chosen, or null while the wallpaper's own colours are being used. */
    palette: SecaPalette?,
    dynamic: Boolean,
    onPick: (SecaPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(SecaIcons.Palette)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.seca_palette),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (dynamic) R.string.seca_palette_hint_dynamic else R.string.seca_palette_hint_on,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            SecaPalette.entries.forEach { entry ->
                SecaPaletteSwatch(
                    palette = entry,
                    selected = palette == entry,
                    onClick = { onPick(entry) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
