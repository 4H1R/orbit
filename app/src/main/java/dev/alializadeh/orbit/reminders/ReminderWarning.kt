package dev.alializadeh.orbit.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Upcoming's Reminder health warning: the critical problem, if any, read on every resume. */
@HiltViewModel
class ReminderWarningViewModel @Inject constructor(
    private val checker: ReminderHealthChecker,
    preferences: PreferencesRepository,
) : ViewModel() {

    private val health = MutableStateFlow<ReminderHealth?>(null)

    val warning: StateFlow<CriticalReminderProblem?> =
        combine(health, preferences.deviceState.map { it.notificationPermissionAsked }) { health, asked ->
            health?.critical(askedForNotifications = asked)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        viewModelScope.launch { health.value = checker.check() }
    }
}

/** The warning card (Upcoming's Warnings slot): what's wrong, and Fix, which opens Reminder health. */
@Composable
fun ReminderWarningCard(problem: CriticalReminderProblem, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(
                    text = stringResource(
                        when (problem) {
                            CriticalReminderProblem.NotificationsOff -> R.string.reminder_warning_notifications_off
                            CriticalReminderProblem.ExactAlarmsOff -> R.string.reminder_warning_exact_alarms_off
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Button(
                    onClick = onFix,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                ) {
                    Text(stringResource(R.string.reminder_warning_fix))
                }
            }
        }
    }
}

/** Hands the app shell the entry a notification tap asked for. */
@HiltViewModel
class NotificationEntryViewModel @Inject constructor(private val entries: NotificationEntries) : ViewModel() {
    val pending: StateFlow<NotificationEntry?> = entries.pending

    fun consume(entry: NotificationEntry) = entries.consume(entry)
}
