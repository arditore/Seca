package com.seca.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.HUE_MAX
import com.seca.core.design.R
import com.seca.core.design.SecaCustomPalette
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaPaletteSpec

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
 *
 * The last swatch is a palette of the owner's own (#28): the four are only a hue and an amount
 * of colour each, so one more of those is the whole feature, and every lightness the contrast
 * was checked at stays where it was.
 */
@Composable
fun SecaPaletteSection(
    palette: SecaPaletteSpec?,
    dynamic: Boolean,
    onPick: (SecaPaletteSpec?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mixing by remember { mutableStateOf(false) }
    val mixed = palette as? SecaCustomPalette
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
            // Picked once, it is kept and shown; until then the swatch offers a starting point
            // rather than a blank, so there is something to see before anything is chosen.
            SecaPaletteSwatch(
                palette = mixed ?: SecaCustomPalette(baseHue = STARTING_HUE, chroma = STARTING_CHROMA),
                selected = mixed != null,
                onClick = { mixing = true },
                label = stringResource(R.string.design_palette_mine),
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (mixing) {
        MixDialog(
            start = mixed ?: SecaCustomPalette(baseHue = STARTING_HUE, chroma = STARTING_CHROMA),
            onDismiss = { mixing = false },
            onKeep = {
                onPick(it)
                mixing = false
            },
        )
    }
}

/**
 * Mixing one: where on the wheel, and how much colour.
 *
 * Two numbers and not a colour picker, because those two are what a Seca palette is. A full
 * picker would offer colours the scheme cannot keep readable, and would then have to take them
 * away again.
 */
@Composable
private fun MixDialog(
    start: SecaCustomPalette,
    onDismiss: () -> Unit,
    onKeep: (SecaCustomPalette) -> Unit,
) {
    var hue by remember { mutableFloatStateOf(start.baseHue) }
    var chroma by remember { mutableFloatStateOf(start.chroma) }
    val mixed = SecaCustomPalette(baseHue = hue, chroma = chroma)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_palette_mine)) },
        text = {
            Column {
                // The three discs are the three apps, so the choice is judged on what it does
                // rather than on one colour out of context.
                SecaPaletteSwatch(
                    palette = mixed,
                    selected = false,
                    onClick = {},
                    size = 72.dp,
                    label = stringResource(R.string.design_palette_mine),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.design_palette_hue),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..HUE_MAX,
                )
                Text(
                    text = stringResource(R.string.design_palette_chroma),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = chroma,
                    onValueChange = { chroma = it },
                    // Never all the way to grey: at nothing at all the three apps stop being
                    // told apart, which is the one thing the palette is for.
                    valueRange = LEAST_CHROMA..1f,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onKeep(mixed) }) { Text(stringResource(R.string.design_keep)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.design_cancel)) } },
    )
}

/** Where the mixing starts before anybody has mixed anything: a plain blue, fully coloured. */
private const val STARTING_HUE = 214f
private const val STARTING_CHROMA = 0.9f

/** Below this the three apps stop being told apart by colour at all. */
private const val LEAST_CHROMA = 0.2f
