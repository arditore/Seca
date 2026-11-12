package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.design.R
import com.seca.core.design.SecaAvatarShape
import com.seca.core.design.SecaIcons
import com.seca.core.design.shapeFor

/**
 * The shapes a face can be cut to, as a row of faces cut to them.
 *
 * Shown rather than named, because nobody knows what "Cookie" is until they see it. The
 * playful one is first and shows its own mark rather than a shape, since what it does is give
 * every person a different one — a single example of it would be a lie about what it is.
 *
 * [chosen] of null means the playful default. Used both for everybody, in the settings, and
 * for one person, on their card, where [onPick] writes to a different place.
 */
@Composable
fun SecaShapePicker(
    chosen: SecaAvatarShape?,
    onPick: (SecaAvatarShape?) -> Unit,
    modifier: Modifier = Modifier,
    /** What "no choice of their own" means here: everybody's shape, on one person's card. */
    defaultLabel: String = stringResource(R.string.design_shape_playful),
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Swatch(
            shape = null,
            label = defaultLabel,
            selected = chosen == null,
            onClick = { onPick(null) },
        )
        SecaAvatarShape.entries.filterNot { it == SecaAvatarShape.Playful }.forEach { shape ->
            Swatch(
                shape = shape,
                label = stringResource(shape.label),
                selected = chosen == shape,
                onClick = { onPick(shape) },
            )
        }
    }
}

@Composable
private fun Swatch(shape: SecaAvatarShape?, label: String, selected: Boolean, onClick: () -> Unit) {
    val cut = (shape ?: SecaAvatarShape.Playful).shapeFor(label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = 72.dp, height = 84.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(cut)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                )
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, cut)
                    },
                ),
        ) {
            if (shape == null) {
                // The playful one is not a shape, so it shows what it does instead of a sample.
                Icon(
                    imageVector = SecaIcons.Palette,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** What each shape is called, in the phone's language. */
private val SecaAvatarShape.label: Int
    get() = when (this) {
        SecaAvatarShape.Playful -> R.string.design_shape_playful
        SecaAvatarShape.Circle -> R.string.design_shape_circle
        SecaAvatarShape.Squircle -> R.string.design_shape_squircle
        SecaAvatarShape.Cookie -> R.string.design_shape_cookie
        SecaAvatarShape.Clover -> R.string.design_shape_clover
        SecaAvatarShape.Sunny -> R.string.design_shape_sunny
        SecaAvatarShape.Flower -> R.string.design_shape_flower
    }
