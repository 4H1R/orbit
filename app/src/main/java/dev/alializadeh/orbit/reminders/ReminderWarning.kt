package dev.alializadeh.orbit.reminders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.components.OrbitWarningCard
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
    OrbitWarningCard(
        text = stringResource(
            when (problem) {
                CriticalReminderProblem.NotificationsOff -> R.string.reminder_warning_notifications_off
                CriticalReminderProblem.ExactAlarmsOff -> R.string.reminder_warning_exact_alarms_off
            },
        ),
        actionLabel = stringResource(R.string.reminder_warning_fix),
        onAction = onFix,
        modifier = modifier,
    )
}

/** Hands the app shell the entry a notification tap asked for. */
@HiltViewModel
class NotificationEntryViewModel @Inject constructor(private val entries: NotificationEntries) : ViewModel() {
    val pending: StateFlow<NotificationEntry?> = entries.pending

    fun consume(entry: NotificationEntry) = entries.consume(entry)
}
