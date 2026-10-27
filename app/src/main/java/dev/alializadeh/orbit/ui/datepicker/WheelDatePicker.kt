package dev.alializadeh.orbit.ui.datepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Orbit's one date entry: a Calendar toggle on top, then day · month · year wheels (the year can be
 * "No year"). The rules live in [WheelDatePickerState]; this only draws them. In RTL the wheels
 * read day · month · year from the right.
 */
@Composable
fun WheelDatePicker(
    state: WheelDatePickerState,
    onStateChange: (WheelDatePickerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val calendarLabel = stringResource(R.string.date_picker_calendar)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OrbitConnectedChoice(
            options = CalendarSystem.entries,
            selected = state.calendar,
            onSelect = { onStateChange(state.withCalendar(it)) },
            label = { calendarName(it) },
            modifier = Modifier.semantics { contentDescription = calendarLabel },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wheel(
                count = state.dayCount,
                selectedIndex = state.day - 1,
                onSelect = { onStateChange(state.withDay(it + 1)) },
                label = { formats.numbers.format(it + 1) },
                description = stringResource(R.string.date_picker_day),
                modifier = Modifier.weight(0.8f),
            )
            Wheel(
                count = CalendarSystem.MONTHS_PER_YEAR,
                selectedIndex = state.month - 1,
                onSelect = { onStateChange(state.withMonth(it + 1)) },
                label = { formats.dates.monthName(state.calendar, it + 1, full = true) },
                description = stringResource(R.string.date_picker_month),
                modifier = Modifier.weight(1.5f),
            )
            val years = state.yearOptions
            val noYear = stringResource(R.string.date_picker_no_year)
            Wheel(
                count = years.size,
                selectedIndex = years.indexOf(state.year),
                onSelect = { onStateChange(state.withYear(years[it])) },
                label = { years[it]?.let(formats.numbers::format) ?: noYear },
                description = stringResource(R.string.date_picker_year),
                modifier = Modifier.weight(1.1f),
            )
        }
        val viaYear = state.convertedViaYear
        if (viaYear != null) {
            Text(
                text = stringResource(R.string.date_picker_converted_using, formats.numbers.format(viaYear)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun calendarName(calendar: CalendarSystem): String = stringResource(
    when (calendar) {
        CalendarSystem.Gregorian -> R.string.calendar_gregorian
        CalendarSystem.SolarHijri -> R.string.calendar_solar_hijri
    },
)

private val ItemHeight = 48.dp
private const val VISIBLE_ITEMS = 5

/**
 * One wheel: [count] rows, the centre one selected. Flinging snaps to a row and selects it once
 * the scroll settles; tapping a row scrolls to it. A selection changed from outside (another
 * wheel shortening the month, a Calendar flip) scrolls the wheel to it.
 */
@Composable
private fun Wheel(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect {
                val centred = listState.centredIndex() ?: return@collect
                if (centred != currentSelected) currentOnSelect(centred)
            }
    }
    LaunchedEffect(selectedIndex, count) {
        if (!listState.isScrollInProgress && listState.centredIndex() != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .height(ItemHeight * VISIBLE_ITEMS)
            .semantics { contentDescription = description },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(ItemHeight),
        ) {}
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = ItemHeight * (VISIBLE_ITEMS / 2)),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = EdgeFade, blendMode = BlendMode.DstIn)
                },
        ) {
            items(count) { index ->
                val selected = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ItemHeight)
                        .semantics { this.selected = selected }
                        .clickable {
                            onSelect(index)
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                ) {
                    Text(
                        text = label(index),
                        style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Fades the wheel out towards its top and bottom edges. */
private val EdgeFade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.3f to Color.Black,
    0.7f to Color.Black,
    1f to Color.Transparent,
)

/** The row nearest the wheel's centre line, or null before the first layout. */
private fun LazyListState.centredIndex(): Int? {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
}
