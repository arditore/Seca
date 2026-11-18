package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Seca's dialog. One of them, used by all of them.
 *
 * There were thirty-one hand-rolled `AlertDialog`s across three apps and no shared component,
 * which is the whole reason they looked like somebody else's software. Material's default
 * centres the icon and left-aligns the headline under it, so every one of them had an icon
 * hanging over a title that did not line up with it; the title ran to two lines because it is
 * set at a headline size in a narrow box; and the button that deletes something looked exactly
 * like the button that does not.
 *
 * What this does differently, and why:
 *
 * - **The icon sits in a circle**, the one the settings rows use, so a dialog looks like it
 *   came from this app rather than from the framework.
 * - **Icon, title and message are centred together.** Either everything is centred or nothing
 *   is; the default was half of each.
 * - **The title is set at title size, not headline**, because a headline in a box this narrow
 *   wraps onto two lines for anything longer than three words.
 * - **The message is optional**, because most of them were the title said again at greater
 *   length. A dialog that says the same thing twice is one people stop reading.
 * - **The confirming button is filled**, and takes the error colours when it destroys
 *   something. Two identical text buttons is how people delete what they meant to keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecaDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Left out unless it says something the title does not. */
    message: String? = null,
    dismissLabel: String? = null,
    /** True when confirming cannot be undone: the button turns to the error colours. */
    destructive: Boolean = false,
    /** Whatever the dialog is really about, between the words and the buttons. */
    content: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                if (icon != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (destructive) colors.errorContainer else colors.secondaryContainer),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (destructive) colors.onErrorContainer else colors.onSecondaryContainer,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = if (icon != null) 16.dp else 0.dp),
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (content != null) Box(Modifier.padding(top = 16.dp)) { content() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(dismissLabel) }
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (destructive) colors.errorContainer else colors.primary,
                            contentColor = if (destructive) colors.onErrorContainer else colors.onPrimary,
                        ),
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

/**
 * The dialog shown while something is happening and nothing can be done about it.
 *
 * These were `AlertDialog`s with `confirmButton = {}` — an empty button slot, which is how a
 * dialog with nothing to press was spelled before there was a word for it. Saying it properly
 * also makes it impossible to dismiss by accident, which is what a half-written backup needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecaBusyDialog(title: String, message: String? = null) {
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp),
            ) {
                CircularProgressIndicator(Modifier.size(40.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
