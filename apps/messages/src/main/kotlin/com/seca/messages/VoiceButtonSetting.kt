package com.seca.messages

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSwitchRow

/**
 * Whether the composer offers the microphone where the send button goes (#91).
 *
 * On by default, because a voice message is the quickest way to answer and most people reach
 * for it. Off for whoever does not: the button then never appears, Send keeps that corner to
 * itself, and nothing can be recorded by pressing in the wrong place. Reported by someone who
 * is "not used to placing this voice button at the same position as the send button".
 */
object VoiceButtonSetting {

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, on) }
    }

    private const val PREFS = "seca_voice_button"
    private const val KEY_ENABLED = "enabled"
}

/** The switch for it, in the messages settings. */
@Composable
internal fun VoiceButtonSection() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(VoiceButtonSetting.enabled(context)) }
    SecaSectionLabel(stringResource(R.string.voice_messages))
    SecaGroupItem(index = 0, count = 1) {
        SecaSwitchRow(
            icon = if (on) SecaIcons.Mic else SecaIcons.MicOff,
            title = stringResource(R.string.voice_button),
            subtitle = stringResource(R.string.voice_button_hint),
            checked = on,
            onCheckedChange = { wanted ->
                on = wanted
                VoiceButtonSetting.setEnabled(context, wanted)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SecaHint(stringResource(R.string.voice_button_note))
}
