package com.seca.messages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaChoicePill
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.messages.sms.CodeCleanup
import com.seca.messages.sms.CodeLifetime
import com.seca.messages.sms.SpamFilter
import kotlinx.coroutines.launch

/** Verification codes that erase themselves, and advertising filed away. */
@Composable
internal fun CodeAndSpamSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lifetime by remember { mutableStateOf(CodeCleanup.lifetime(context)) }
    var filterSpam by remember { mutableStateOf(SpamFilter.enabled(context)) }

    SecaSectionLabel("Codes de vérification")
    SecaGroupItem(index = 0, count = 1) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            CodeLifetime.entries.forEach { choice ->
                SecaChoicePill(
                    label = choice.label,
                    selected = lifetime == choice,
                    onClick = {
                        lifetime = choice
                        CodeCleanup.setLifetime(context, choice)
                        // Codes already past the new delay go at once.
                        scope.launch { runCatching { CodeCleanup.sweep(context) } }
                    },
                )
            }
        }
    }
    SecaHint("Les SMS qui contiennent un code de vérification sont effacés du téléphone après ce délai.")

    SecaSectionLabel("SMS publicitaires")
    SecaGroupItem(index = 0, count = 1) {
        SecaSettingRow(
            icon = SecaIcons.Block,
            title = "Ranger les SMS publicitaires",
            subtitle = "Dans « Indésirables », sans notification",
            modifier = Modifier.toggleable(
                value = filterSpam,
                role = Role.Switch,
                onValueChange = { on ->
                    filterSpam = on
                    SpamFilter.setEnabled(context, on)
                },
            ),
            trailing = { Switch(checked = filterSpam, onCheckedChange = null) },
        )
    }
    SecaHint("Reconnus à leur mention « STOP au 36… ». Les SMS de vos contacts et les codes ne sont jamais rangés.")
}
