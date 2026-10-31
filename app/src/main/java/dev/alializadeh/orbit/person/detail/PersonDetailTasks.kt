package dev.alializadeh.orbit.person.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.person.FollowUp
import dev.alializadeh.orbit.person.Loan
import dev.alializadeh.orbit.person.loans.displayName
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

/**
 * "Today", "In 3 days", "3 days ago", or the date itself over a week ahead: when a Follow-up or
 * Loan's day is.
 */
@Composable
internal fun taskWhen(date: LocalDate, today: LocalDate): String {
    val formats = LocalOrbitFormats.current
    if (date.isBefore(today)) return RelativeDays.ago(date, today).text()
    return RelativeDays.ahead(date, today)?.text() ?: formats.dates.format(date, formats.primaryCalendar, today)
}

/** One row of a [TaskSection]. */
private class TaskRow(val id: Long, val text: String, val supporting: String?, val overdue: Boolean = false)

/** The open Follow-ups, "Done (n)" collapsed, and a "+ Follow-up" row. Tapping one opens its sheet. */
@Composable
internal fun FollowUpsSection(
    open: List<FollowUp>,
    done: List<FollowUp>,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (id: Long) -> Unit,
    onMarkDone: (id: Long) -> Unit,
) {
    val formats = LocalOrbitFormats.current
    TaskSection(
        title = stringResource(R.string.person_section_follow_ups),
        icon = OrbitIcons.LogInteraction,
        open = open.map { TaskRow(it.id, it.text, taskWhen(it.dueOn, today), overdue = it.dueOn.isBefore(today)) },
        closed = done.map { followUp ->
            TaskRow(
                followUp.id,
                followUp.text,
                followUp.doneOn?.let { stringResource(R.string.follow_up_done_on, formats.dates.format(it, formats.primaryCalendar, today)) },
            )
        },
        closedLabel = stringResource(R.string.follow_up_done_section, formats.numbers.format(done.size)),
        closeLabel = stringResource(R.string.follow_up_mark_done),
        addLabel = stringResource(R.string.follow_up_add),
        onAdd = onAdd,
        onOpen = onOpen,
        onClose = onMarkDone,
    )
}

/** The open Loans, "Returned (n)" collapsed, and a "+ Lent or borrowed" row. Tapping one opens its sheet. */
@Composable
internal fun LoansSection(
    open: List<Loan>,
    returned: List<Loan>,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (id: Long) -> Unit,
    onMarkReturned: (id: Long) -> Unit,
) {
    val formats = LocalOrbitFormats.current
    val separator = stringResource(R.string.separator)
    TaskSection(
        title = stringResource(R.string.person_section_loans),
        icon = OrbitIcons.Loan,
        open = open.map { loan ->
            val by = loan.returnBy?.let { stringResource(R.string.loan_return_by_date, formats.dates.format(it, formats.primaryCalendar, today)) }
            TaskRow(
                loan.id,
                loan.text,
                listOfNotNull(loan.direction.displayName(), by).joinToString(separator),
                overdue = loan.returnBy?.isBefore(today) == true,
            )
        },
        closed = returned.map { loan ->
            val on = loan.returnedOn?.let { stringResource(R.string.loan_returned_on, formats.dates.format(it, formats.primaryCalendar, today)) }
            TaskRow(loan.id, loan.text, listOfNotNull(loan.direction.displayName(), on).joinToString(separator))
        },
        closedLabel = stringResource(R.string.loan_returned_section, formats.numbers.format(returned.size)),
        closeLabel = stringResource(R.string.loan_mark_returned),
        addLabel = stringResource(R.string.loan_add),
        onAdd = onAdd,
        onOpen = onOpen,
        onClose = onMarkReturned,
    )
}

@Composable
private fun TaskSection(
    title: String,
    icon: ImageVector,
    open: List<TaskRow>,
    closed: List<TaskRow>,
    closedLabel: String,
    closeLabel: String,
    addLabel: String,
    onAdd: () -> Unit,
    onOpen: (id: Long) -> Unit,
    onClose: (id: Long) -> Unit,
) {
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val count = open.size + (if (closed.isEmpty()) 0 else 1) + (if (showClosed) closed.size else 0) + 1
    var index = 0
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column {
        OrbitSectionTitle(title, horizontalPadding = 4.dp)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            open.forEach { row ->
                SegmentedListItem(
                    onClick = { onOpen(row.id) },
                    shapes = ListItemDefaults.segmentedShapes(index++, count),
                    colors = colors,
                    leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    supportingContent = row.supporting?.let { text ->
                        { Text(text, color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                    trailingContent = {
                        IconButton(onClick = { onClose(row.id) }) {
                            Icon(Icons.Filled.Check, contentDescription = closeLabel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(row.text)
                }
            }
            if (closed.isNotEmpty()) {
                SegmentedListItem(
                    onClick = { showClosed = !showClosed },
                    shapes = ListItemDefaults.segmentedShapes(index++, count),
                    colors = colors,
                    leadingContent = { Icon(Icons.Filled.Check, contentDescription = null) },
                    trailingContent = {
                        Icon(if (showClosed) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(closedLabel)
                }
                if (showClosed) {
                    closed.forEach { row ->
                        SegmentedListItem(
                            onClick = { onOpen(row.id) },
                            shapes = ListItemDefaults.segmentedShapes(index++, count),
                            colors = colors,
                            leadingContent = { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingContent = row.supporting?.let { text -> { Text(text) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(row.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SegmentedListItem(
                onClick = onAdd,
                shapes = ListItemDefaults.segmentedShapes(index, count),
                colors = colors,
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(addLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
