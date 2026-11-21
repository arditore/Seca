package com.seca.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seca.core.design.R
import com.seca.core.design.SecaIcons
import com.seca.core.model.Calendars
import com.seca.core.model.SecaCalendar
import java.time.LocalDate

/**
 * Which calendar the three apps write their dates in (#83).
 *
 * A calendar is not a language and does not follow from one: somebody who reads English
 * screens may still count the days in Solar Hijri, and taking the calendar from the phone's
 * language would hand them the wrong one. So it is asked for, once, and the answer dresses all
 * three apps like the palette does.
 *
 * Each choice shows today written in it, which is the only way to tell them apart without
 * already knowing them.
 */
@Composable
fun SecaCalendarSection(chosen: SecaCalendar, onChoose: (SecaCalendar) -> Unit) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    SecaSectionLabel(stringResource(R.string.design_calendar))
    SecaGroupItem(index = 0, count = 1, onClick = { choosing = true }) {
        SecaSettingRow(
            icon = SecaIcons.Event,
            title = stringResource(nameOf(chosen)),
            subtitle = today(chosen),
        )
    }
    SecaHint(stringResource(R.string.design_calendar_hint))
    if (choosing) {
        SecaDialog(
            onDismiss = { choosing = false },
            icon = SecaIcons.Event,
            title = stringResource(R.string.design_calendar),
            confirmLabel = stringResource(R.string.design_cancel),
            onConfirm = { choosing = false },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SecaCalendar.entries.forEach { calendar ->
                        CalendarRow(
                            title = stringResource(nameOf(calendar)),
                            example = today(calendar),
                            picked = calendar == chosen,
                            onClick = {
                                choosing = false
                                onChoose(calendar)
                                // Written at once, so the first date drawn afterwards is
                                // already in the new calendar rather than a frame behind.
                                Calendars.remember(context, calendar)
                            },
                        )
                    }
                }
            },
        )
    }
}

/** One calendar in the chooser, with today written the way it would write it. */
@Composable
private fun CalendarRow(title: String, example: String, picked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(example, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (picked) {
            Icon(
                imageVector = SecaIcons.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Today, written as that calendar writes it, whatever the app is currently set to. */
@Composable
private fun today(calendar: SecaCalendar): String {
    val day = LocalDate.now()
    return remember(calendar, day) { Calendars.textIn(calendar, day, "dMMMMy") }
}

private fun nameOf(calendar: SecaCalendar): Int = when (calendar) {
    SecaCalendar.Phone -> R.string.design_calendar_phone
    SecaCalendar.Gregorian -> R.string.design_calendar_gregorian
    SecaCalendar.Persian -> R.string.design_calendar_persian
    SecaCalendar.Islamic -> R.string.design_calendar_islamic
    SecaCalendar.Buddhist -> R.string.design_calendar_buddhist
    SecaCalendar.Hebrew -> R.string.design_calendar_hebrew
    SecaCalendar.Indian -> R.string.design_calendar_indian
    SecaCalendar.Japanese -> R.string.design_calendar_japanese
}
