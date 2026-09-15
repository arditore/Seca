package com.seca.messages

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import androidx.compose.ui.res.stringResource

private const val MORNING_HOUR = 8
private const val EVENING_HOUR = 19

/** Choosing when a message leaves: a few usual moments, or any date and time. */
@Composable
internal fun ScheduleDialog(onDismiss: () -> Unit, onSchedule: (Long) -> Unit) {
    val context = LocalContext.current
    val now = remember { ZonedDateTime.now() }
    val choices = remember(now) { presetsFrom(now) }
    // Checked each time the dialog opens, so a permission just granted counts.
    val exact = remember { context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Schedule, contentDescription = null) },
        title = { Text(stringResource(R.string.schedule_send)) },
        text = {
            Column {
                choices.forEach { (label, at) ->
                    ScheduleChoice(stringResource(label), scheduleTimeOf(context, at)) { onSchedule(at) }
                }
                ScheduleChoice(stringResource(R.string.pick_date_time), null) { pickDateTime(context, now, onSchedule) }
                if (!exact) {
                    Text(
                        text = stringResource(R.string.exact_alarm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    )
                    TextButton(
                        onClick = {
                            onDismiss()
                            openExactAlarmSettings(context)
                        },
                    ) { Text(stringResource(R.string.allow_exact_time)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ScheduleChoice(title: String, detail: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** In an hour, this evening while it is still ahead, tomorrow morning. */
private fun presetsFrom(now: ZonedDateTime): List<Pair<Int, Long>> = buildList {
    add(R.string.in_an_hour to now.plusHours(1).truncatedTo(ChronoUnit.MINUTES))
    if (now.hour < EVENING_HOUR - 1) add(R.string.this_evening to now.withHour(EVENING_HOUR).truncatedTo(ChronoUnit.HOURS))
    add(R.string.tomorrow_morning to now.plusDays(1).withHour(MORNING_HOUR).truncatedTo(ChronoUnit.HOURS))
}.map { (label, time) -> label to time.toInstant().toEpochMilli() }

/** Android's own date then time pickers, in the phone's colours and 12- or 24-hour setting. */
private fun pickDateTime(context: Context, now: ZonedDateTime, onSchedule: (Long) -> Unit) {
    val dates = DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val at = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    if (at <= System.currentTimeMillis()) {
                        Toast.makeText(context, context.getString(R.string.pick_future_time), Toast.LENGTH_SHORT).show()
                    } else {
                        onSchedule(at)
                    }
                },
                now.hour,
                now.minute,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth,
    )
    dates.datePicker.minDate = now.truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()
    dates.show()
}
