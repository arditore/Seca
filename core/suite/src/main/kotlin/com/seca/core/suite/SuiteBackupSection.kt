package com.seca.core.suite

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaPassphraseDialog
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.ui.res.stringResource

/**
 * In each Seca app's settings: one encrypted file for the whole suite, made or
 * restored from any of the three apps, to move to another phone or keep safe.
 */
@Composable
fun SuiteBackupSection(
    /** Runs before the file is chosen, for instance to ask the permissions a restoration needs; calls proceed when done. */
    beforeRestore: (proceed: () -> Unit) -> Unit = { proceed -> proceed() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Uri?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var working by remember { mutableStateOf<Int?>(null) }
    var outcome by remember { mutableStateOf<SuiteBackup.Outcome?>(null) }
    // The system file picker: the owner chooses where the file goes, and the app sees nothing else.
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { target = it }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source = it }

    SecaSectionLabel(stringResource(R.string.suite_section))
    SecaGroupItem(index = 0, count = 2, onClick = { create.launch("seca-suite-${LocalDate.now()}.seca") }) {
        SecaSettingRow(
            icon = SecaIcons.Lock,
            title = stringResource(R.string.suite_backup_all),
            subtitle = stringResource(R.string.suite_backup_all_hint),
        )
    }
    SecaGroupItem(index = 1, count = 2, onClick = { beforeRestore { open.launch(arrayOf("*/*")) } }) {
        SecaSettingRow(
            icon = SecaIcons.Download,
            title = stringResource(R.string.suite_restore_all),
            subtitle = stringResource(R.string.suite_restore_all_hint),
        )
    }
    SecaHint(stringResource(R.string.suite_hint))

    target?.let { uri ->
        SecaPassphraseDialog(
            title = stringResource(R.string.suite_encrypt_title),
            confirmLabel = stringResource(R.string.suite_encrypt),
            creating = true,
            onDismiss = { target = null },
            onConfirm = { passphrase ->
                target = null
                working = R.string.suite_backing_up
                scope.launch {
                    outcome = SuiteBackup(context).export(uri, passphrase)
                    working = null
                }
            },
        )
    }
    source?.let { uri ->
        SecaPassphraseDialog(
            title = stringResource(R.string.suite_open_title),
            confirmLabel = stringResource(R.string.suite_restore),
            creating = false,
            onDismiss = { source = null },
            onConfirm = { passphrase ->
                source = null
                working = R.string.suite_restoring
                scope.launch {
                    outcome = SuiteBackup(context).restore(uri, passphrase)
                    working = null
                }
            },
        )
    }
    working?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(label)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text(
                        text = stringResource(R.string.suite_working_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
            confirmButton = {},
        )
    }
    outcome?.let { result ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            icon = { Icon(if (result is SuiteBackup.Outcome.Done) SecaIcons.Check else SecaIcons.Shield, contentDescription = null) },
            title = { Text(stringResource(if (result is SuiteBackup.Outcome.Done) R.string.suite_done else R.string.suite_failed)) },
            text = {
                Column {
                    when (result) {
                        is SuiteBackup.Outcome.Done -> result.lines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        is SuiteBackup.Outcome.Failed -> Text(result.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
