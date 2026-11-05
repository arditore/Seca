package com.seca.core.suite

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaChoicePill
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaPassphraseDialog
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How many copies of each kind the owner can choose to keep. */
private val KeepChoices = listOf(3, 7, 14, 30)

/**
 * In each Seca app's settings: backups that write themselves into a folder of the owner's.
 *
 * Shown under the one made by hand, because it is the same backup with a clock attached, and
 * because someone who has never made one should meet the plain button first.
 */
@Composable
fun AutoBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AutoBackupSettings(context) }

    var folder by remember { mutableStateOf(settings.folder) }
    var rhythm by remember { mutableStateOf(settings.rhythm) }
    var keep by remember { mutableStateOf(settings.keep) }
    var portable by remember { mutableStateOf(settings.portable) }
    var lastRun by remember { mutableStateOf(settings.lastRun) }
    var chosen by remember { mutableStateOf<Uri?>(null) }
    var warning by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<AutoBackup.Result?>(null) }

    // The system's folder picker: the owner points at one folder, and the app sees that one only.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            // Held across restarts, or a backup set up tonight would fail at the next reboot.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            chosen = tree
        }
    }

    SecaSectionLabel(stringResource(R.string.auto_section))
    SecaGroupItem(index = 0, count = if (folder == null) 1 else ROWS_WHEN_ON, onClick = { pick.launch(null) }) {
        SecaSettingRow(
            icon = SecaIcons.Schedule,
            title = stringResource(R.string.auto_folder),
            subtitle = folder?.let(::folderName) ?: stringResource(R.string.auto_folder_none),
        )
    }

    if (folder != null) {
        SecaGroupItem(index = 1, count = ROWS_WHEN_ON) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.auto_rhythm),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    BackupRhythm.entries.forEach { choice ->
                        SecaChoicePill(
                            label = stringResource(if (choice == BackupRhythm.Daily) R.string.auto_daily else R.string.auto_weekly),
                            selected = rhythm == choice,
                            onClick = {
                                rhythm = choice
                                settings.rhythm = choice
                                AutoBackupSchedule.arm(context)
                            },
                        )
                    }
                }
            }
        }
        SecaGroupItem(index = 2, count = ROWS_WHEN_ON) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.auto_keep),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    KeepChoices.forEach { choice ->
                        SecaChoicePill(
                            label = stringResource(R.string.auto_keep_count, choice),
                            selected = keep == choice,
                            onClick = {
                                keep = choice
                                settings.keep = choice
                            },
                        )
                    }
                }
            }
        }
        SecaGroupItem(index = 3, count = ROWS_WHEN_ON) {
            SecaSettingRow(
                icon = SecaIcons.Share,
                title = stringResource(R.string.auto_portable),
                subtitle = stringResource(R.string.auto_portable_hint),
                modifier = Modifier.toggleable(
                    value = portable,
                    role = Role.Switch,
                    onValueChange = { on ->
                        // Turning it on is said out loud once; turning it off needs no ceremony.
                        if (on) {
                            warning = true
                        } else {
                            portable = false
                            settings.portable = false
                        }
                    },
                ),
                trailing = { Switch(checked = portable, onCheckedChange = null) },
            )
        }
        SecaGroupItem(
            index = 4,
            count = ROWS_WHEN_ON,
            onClick = {
                working = true
                scope.launch {
                    outcome = AutoBackupSchedule.runOnce(context)
                    lastRun = settings.lastRun
                    working = false
                }
            },
        ) {
            SecaSettingRow(
                icon = SecaIcons.Upload,
                title = stringResource(R.string.auto_run_now),
                subtitle = if (lastRun == 0L) {
                    stringResource(R.string.auto_never_run)
                } else {
                    stringResource(R.string.auto_last_run, WHEN.format(Instant.ofEpochMilli(lastRun)))
                },
            )
        }
        SecaGroupItem(
            index = 5,
            count = ROWS_WHEN_ON,
            onClick = {
                settings.forget()
                AutoBackupSchedule.arm(context)
                folder = null
                portable = false
                lastRun = 0L
            },
        ) {
            SecaSettingRow(
                icon = SecaIcons.Delete,
                title = stringResource(R.string.auto_turn_off),
                subtitle = stringResource(R.string.auto_turn_off_hint),
            )
        }
    }
    SecaHint(stringResource(R.string.auto_passphrase_hint))

    // A folder is only of use with a phrase to seal the backups with, so the two are asked together.
    chosen?.let { tree ->
        SecaPassphraseDialog(
            title = stringResource(R.string.auto_passphrase_title),
            confirmLabel = stringResource(R.string.suite_encrypt),
            creating = true,
            onDismiss = { chosen = null },
            onConfirm = { passphrase ->
                chosen = null
                settings.setPassphrase(passphrase)
                settings.folder = tree
                folder = tree
                AutoBackupSchedule.arm(context)
            },
        )
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            icon = { Icon(SecaIcons.Shield, contentDescription = null) },
            title = { Text(stringResource(R.string.auto_portable)) },
            text = { Text(stringResource(R.string.auto_portable_warning), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        warning = false
                        portable = true
                        settings.portable = true
                    },
                ) { Text(stringResource(R.string.auto_understand)) }
            },
            dismissButton = { TextButton(onClick = { warning = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    if (working) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.suite_backing_up)) },
            text = { Text(stringResource(R.string.suite_working_hint), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {},
        )
    }

    outcome?.let { result ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            icon = { Icon(if (result is AutoBackup.Result.Written) SecaIcons.Check else SecaIcons.Shield, contentDescription = null) },
            title = { Text(stringResource(if (result is AutoBackup.Result.Written) R.string.suite_done else R.string.suite_failed)) },
            text = {
                Column {
                    when (result) {
                        is AutoBackup.Result.Written -> result.files.forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        is AutoBackup.Result.Failed -> Text(result.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

/** The last part of the folder's own name, which is what the owner will recognise. */
private fun folderName(folder: Uri): String =
    folder.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() } ?: folder.toString()

private const val ROWS_WHEN_ON = 6

private val WHEN: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
