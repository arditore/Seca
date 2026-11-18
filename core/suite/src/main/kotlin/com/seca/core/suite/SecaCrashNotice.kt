package com.seca.core.suite

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons

/**
 * Offers the last crash, once, the next time the app opens.
 *
 * Shown rather than sent. The whole report is on screen and scrollable before any button is
 * pressed, because a stack trace can carry a number or a name inside an exception message and
 * nobody should be asked to hand over a thing they have not read.
 *
 * Saying no forgets it. An app that asks twice about the same crash is an app people learn to
 * dismiss without reading.
 */
@Composable
fun SecaCrashNotice() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { report = SecaCrashReport.pending(context) }
    val trace = report ?: return
    val forget = {
        SecaCrashReport.clear(context)
        report = null
    }
    AlertDialog(
        onDismissRequest = forget,
        icon = { Icon(SecaIcons.Shield, contentDescription = null) },
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Text(
                text = stringResource(R.string.crash_body) + "\n\n" + trace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            val subject = stringResource(R.string.crash_subject)
            TextButton(onClick = {
                share(context, subject, trace)
                forget()
            }) { Text(stringResource(R.string.crash_send)) }
        },
        dismissButton = {
            TextButton(onClick = forget) { Text(stringResource(R.string.crash_discard)) }
        },
    )
}

/** Hands it to whatever the owner picks, which is the only way it ever leaves the phone. */
private fun share(context: Context, subject: String, trace: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, trace)
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
