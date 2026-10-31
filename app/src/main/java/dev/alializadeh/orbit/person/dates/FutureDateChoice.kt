package dev.alializadeh.orbit.person.dates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarDate
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.ui.datepicker.WheelDatePicker
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import java.time.LocalDate

private enum class FutureChoice { None, Tomorrow, Week, Month, Pick }

/** The day a [FutureDateField] stands at, and whether it can be saved. */
internal class FutureDateChoice(
    /** Null is No date. */
    val date: LocalDate?,
    val isValid: Boolean,
    private val draw: @Composable () -> Unit,
) {
    @Composable
    fun Field() = draw()
}

/**
 * A day from today on: Tomorrow, In 1 week, In 1 month or a picked day, and No date too when
 * [allowNone]. Saveable, so it survives App lock. An [initial] day already passed stays valid until
 * another is chosen (editing an overdue Follow-up keeps its day). The line under the chips is
 * always laid out, so the sheet's height doesn't change after a wheel scroll.
 */
@Composable
internal fun rememberFutureDateChoice(initial: LocalDate?, today: LocalDate, allowNone: Boolean): FutureDateChoice {
    val formats = LocalOrbitFormats.current
    var choice by rememberSaveable {
        mutableStateOf(
            when {
                initial != null -> FutureChoice.Pick
                allowNone -> FutureChoice.None
                else -> FutureChoice.Week
            },
        )
    }
    var picker by rememberSaveable(stateSaver = WheelDatePickerStateSaver) {
        val start = (initial ?: today).inCalendar(formats.primaryCalendar)
        mutableStateOf(
            WheelDatePickerState.forDate(
                RecurringDate(formats.primaryCalendar, start.month, start.day, start.year),
                today,
                allowNoYear = false,
            ).copy(futureYears = FUTURE_YEARS),
        )
    }
    val date = when (choice) {
        FutureChoice.None -> null
        FutureChoice.Tomorrow -> today.plusDays(1)
        FutureChoice.Week -> today.plusWeeks(1)
        FutureChoice.Month -> today.plusMonths(1)
        FutureChoice.Pick -> picker.date.let { CalendarDate(it.calendar, it.year!!, it.month, it.day).toLocalDate() }
    }
    val isValid = date == null || !date.isBefore(today) || date == initial
    return FutureDateChoice(date, isValid) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FutureChoice.entries.filter { allowNone || it != FutureChoice.None }.forEach { option ->
                    FilterChip(
                        selected = choice == option,
                        onClick = { choice = option },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        FutureChoice.None -> R.string.date_choice_none
                                        FutureChoice.Tomorrow -> R.string.time_tomorrow
                                        FutureChoice.Week -> R.string.date_choice_week
                                        FutureChoice.Month -> R.string.date_choice_month
                                        FutureChoice.Pick -> R.string.date_choice_pick
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            if (choice == FutureChoice.Pick) WheelDatePicker(state = picker, onStateChange = { picker = it })
            Text(
                text = when {
                    !isValid -> stringResource(R.string.date_choice_past)
                    date == null -> ""
                    else -> formats.dates.formatWithWeekday(date, formats.primaryCalendar, today)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** How many years ahead the wheel offers. */
private const val FUTURE_YEARS = 5
