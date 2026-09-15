package com.seca.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.link.SecaLink
import com.seca.messages.link.LinkService
import androidx.compose.ui.res.stringResource

/**
 * Letting Seca Link listen in the background without a notification: Android
 * allows it once the owner lifts the app's battery optimisation, the only way
 * to receive in time on a phone without a push service.
 */
internal object BackgroundAccess {

    private const val PREFS = "seca_background"
    private const val KEY_ASKED_AT = "asked_at"
    private const val ASK_AGAIN_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun granted(context: Context): Boolean = LinkService.runsFreely(context)

    /** Once Seca Link is on and Android still optimises the app; asked again a week after the owner declined. */
    fun shouldAsk(context: Context): Boolean {
        if (!SecaLink(context).settings.enabled || granted(context)) return false
        val askedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ASKED_AT, 0L)
        return System.currentTimeMillis() - askedAt > ASK_AGAIN_MILLIS
    }

    fun postpone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_ASKED_AT, System.currentTimeMillis()) }
    }

    /** Android's own dialog, which asks the owner in one tap. */
    @SuppressLint("BatteryLife") // A messaging app without a push service: this is how its messages arrive in time.
    fun request(context: Context) {
        postpone(context)
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    /** The list where the owner can take the permission back. */
    fun openSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

/** Asks, as the app opens, when Seca Link is on but still needs its notification to keep listening. */
@Composable
internal fun BackgroundAccessPrompt() {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        asking = BackgroundAccess.shouldAsk(context)
        onPauseOrDispose { }
    }
    if (asking) BackgroundAccessDialog(onDone = { asking = false })
}

@Composable
internal fun BackgroundAccessDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {
            BackgroundAccess.postpone(context)
            onDone()
        },
        icon = { Icon(SecaIcons.Battery, contentDescription = null) },
        title = { Text(stringResource(R.string.receive_without_notification)) },
        text = { Text(stringResource(R.string.background_dialog_text)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone()
                    BackgroundAccess.request(context)
                },
            ) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    BackgroundAccess.postpone(context)
                    onDone()
                },
            ) { Text(stringResource(R.string.later)) }
        },
    )
}

/** In the settings: whether Seca Link listens in the background, and the way to allow it or take it back. */
@Composable
internal fun BackgroundAccessRow(index: Int, count: Int) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BackgroundAccess.granted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = BackgroundAccess.granted(context)
        onPauseOrDispose { }
    }
    SecaGroupItem(
        index = index,
        count = count,
        onClick = { if (granted) BackgroundAccess.openSettings(context) else BackgroundAccess.request(context) },
    ) {
        SecaSettingRow(
            icon = SecaIcons.Battery,
            title = stringResource(R.string.background_reception),
            subtitle = stringResource(if (granted) R.string.background_granted else R.string.background_tap),
        )
    }
}
