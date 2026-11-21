package com.seca.core.design.component

import android.icu.util.Calendar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seca.core.design.R
import com.seca.core.design.SecaIcons
import com.seca.core.model.Calendars
import com.seca.core.model.SecaCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Picking a day in a calendar Android's own picker does not know (#83).
 *
 * Android's date picker draws a Gregorian grid and nothing else, so somebody who lives by the
 * Persian calendar was being asked to convert a date in his head in order to type it — which
 * is the very thing the setting exists to spare him. This asks for the year, the month and the
 * day of the calendar he actually reads, and names the months as that calendar names them.
 *
 * The Gregorian date is written underneath, always, because that is the date the phone stores
 * and the one he will quote to anyone who does not share his calendar. It was asked for by
 * name in the report.
 *
 * The Gregorian calendar keeps Android's own picker: a familiar grid beats a stack of fields
 * for the people who already had one that worked.
 */
@Composable
fun SecaDayPicker(
    title: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    /** Nothing earlier than this can be confirmed, for a date that has to be ahead. */
    notBefore: LocalDate? = null,
) {
    val context = LocalContext.current
    val locale = Calendars.locale(context)
    val start = remember(initial, locale) { fieldsOf(locale, initial) }
    var year by remember { mutableIntStateOf(start.year) }
    var month by remember { mutableIntStateOf(start.month) }
    var day by remember { mutableIntStateOf(start.day) }

    val months = remember(locale, year) { monthsOf(locale, year) }
    val lastDay = remember(locale, year, month) { lastDayOf(locale, year, month) }
    if (day > lastDay) day = lastDay
    val picked = remember(locale, year, month, day) { dayOf(locale, year, month, day) }
    val tooEarly = notBefore != null && picked.isBefore(notBefore)

    SecaDialog(
        onDismiss = onDismiss,
        icon = SecaIcons.Event,
        title = title,
        confirmLabel = stringResource(R.string.design_keep),
        onConfirm = { if (!tooEarly) onPick(picked) },
        dismissLabel = stringResource(R.string.design_cancel),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year.toString(),
                        onValueChange = { typed ->
                            typed.filter(Char::isDigit).take(YEAR_DIGITS).toIntOrNull()?.let { year = it }
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.design_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                    PickerField(
                        label = stringResource(R.string.design_month),
                        shown = months.getOrElse(month) { "" },
                        options = months,
                        onPick = { month = it },
                        modifier = Modifier.weight(1.4f),
                    )
                    PickerField(
                        label = stringResource(R.string.design_day),
                        shown = day.toString(),
                        options = (1..lastDay).map(Int::toString),
                        onPick = { day = it + 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
                // What the phone will actually store, named for what it is.
                Text(
                    text = stringResource(
                        R.string.design_gregorian_too,
                        Calendars.textIn(SecaCalendar.Gregorian, picked, "dMMMMy"),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tooEarly) {
                    Text(
                        text = stringResource(R.string.design_day_past),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

/** A read-only field that opens its list, since Material has no wheel to turn. */
@Composable
private fun PickerField(
    label: String,
    shown: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        // Over the field rather than inside it: a read-only text field swallows the tap.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = label) { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = MENU_HEIGHT),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            open = false
                            onPick(index)
                        },
                    )
                }
            }
        }
    }
}

/** Year, month and day as the chosen calendar counts them. */
private class Fields(val year: Int, val month: Int, val day: Int)

private fun working(locale: Locale): Calendar = Calendar.getInstance(locale)

private fun fieldsOf(locale: Locale, date: LocalDate): Fields {
    val calendar = working(locale).apply {
        timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return Fields(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH),
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

/** The months of that year, in that calendar: some calendars have thirteen in some years. */
private fun monthsOf(locale: Locale, year: Int): List<String> {
    val calendar = working(locale).apply {
        set(Calendar.YEAR, year)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val last = calendar.getActualMaximum(Calendar.MONTH)
    return (0..last).map { month ->
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        android.icu.text.DateFormat.getInstanceForSkeleton("MMMM", locale).format(calendar.time)
    }
}

private fun lastDayOf(locale: Locale, year: Int, month: Int): Int = working(locale).apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, 1)
}.getActualMaximum(Calendar.DAY_OF_MONTH)

private fun dayOf(locale: Locale, year: Int, month: Int, day: Int): LocalDate {
    val calendar = working(locale).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 12)
    }
    return java.util.Date(calendar.timeInMillis).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}

/** No calendar in use numbers its years past five digits. */
private const val YEAR_DIGITS = 5

private val MENU_HEIGHT = 320.dp
