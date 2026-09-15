package com.seca.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.seca.core.design.R

private const val MIN_LENGTH = 8

/**
 * Asks for the passphrase of an encrypted backup. When creating one, it is
 * typed twice and must be at least [MIN_LENGTH] characters, since nobody can
 * recover it later.
 */
@Composable
fun SecaPassphraseDialog(
    title: String,
    confirmLabel: String,
    creating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = creating && first.isNotEmpty() && first.length < MIN_LENGTH
    val mismatch = creating && second.isNotEmpty() && first != second
    val valid = if (creating) first.length >= MIN_LENGTH && first == second else first.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(if (creating) R.string.design_passphrase_creating_hint else R.string.design_passphrase_opening_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PassphraseField(first, stringResource(R.string.design_passphrase), isError = tooShort) { first = it }
                if (tooShort) {
                    Text(
                        text = pluralStringResource(R.plurals.design_passphrase_too_short, MIN_LENGTH, MIN_LENGTH),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (creating) {
                    PassphraseField(second, stringResource(R.string.design_passphrase_confirm), isError = mismatch) { second = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(first.toCharArray()) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.design_cancel)) } },
    )
}

@Composable
private fun PassphraseField(value: String, label: String, isError: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = MaterialTheme.shapes.medium,
    )
}
