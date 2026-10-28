package dev.alializadeh.orbit.reminders

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.BackedUpPreferences
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.ui.components.ContentRow
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object RemindersSettingsKey : NavKey

data class RemindersSettingsUiState(
    val reminderTime: LocalTime = BackedUpPreferences.DEFAULT_REMINDER_TIME,
    val defaultLeadTimes: Set<LeadTime> = LeadTime.DEFAULT,
    /** Read on every resume; null until the first read. */
    val health: ReminderHealth? = null,
)

@HiltViewModel
class RemindersSettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val healthChecker: ReminderHealthChecker,
) : ViewModel() {

    private val health = MutableStateFlow<ReminderHealth?>(null)

    val uiState: StateFlow<RemindersSettingsUiState> =
        combine(preferences.preferences, health) { backedUp, health ->
            RemindersSettingsUiState(backedUp.reminderTime, backedUp.defaultLeadTimes, health)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersSettingsUiState())

    /** Both are backed-up preferences: the data version moves, and reminders reconcile. */
    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch { preferences.update { it.copy(reminderTime = time) } }
    }

    fun toggleDefaultLeadTime(leadTime: LeadTime) {
        viewModelScope.launch {
            preferences.update {
                val leadTimes = if (leadTime in it.defaultLeadTimes) it.defaultLeadTimes - leadTime else it.defaultLeadTimes + leadTime
                it.copy(defaultLeadTimes = leadTimes)
            }
        }
    }

    fun refreshHealth() {
        viewModelScope.launch { health.value = healthChecker.check() }
    }
}

@Composable
fun RemindersSettingsRoute(
    onBack: () -> Unit,
    onOpenHealth: () -> Unit,
    viewModel: RemindersSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealth()
        onPauseOrDispose {}
    }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    RemindersSettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onReminderTimeClick = { pickingTime = true },
        onLeadTimeToggle = viewModel::toggleDefaultLeadTime,
        onHealthClick = onOpenHealth,
        onChannelClick = { ReminderSettingsIntents.channel(context, it) },
    )
    if (pickingTime) {
        ReminderTimeSheet(
            initial = uiState.reminderTime,
            onDone = viewModel::setReminderTime,
            onDismiss = { pickingTime = false },
        )
    }
}

/**
 * Settings → Reminders: the Reminder time, the default lead times, Reminder health, and a link to
 * each notification channel's system settings (Orbit has no on/off switches of its own).
 */
@Composable
fun RemindersSettingsScreen(
    uiState: RemindersSettingsUiState,
    onBack: () -> Unit,
    onReminderTimeClick: () -> Unit,
    onLeadTimeToggle: (LeadTime) -> Unit,
    onHealthClick: () -> Unit,
    onChannelClick: (ReminderChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = LocalOrbitFormats.current.numbers
    OrbitSettingsScaffold(title = stringResource(R.string.settings_reminders_title), onBack = onBack, modifier = modifier) {
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.reminders_time),
                summary = numbers.time(uiState.reminderTime),
                index = 0,
                count = 1,
                onClick = onReminderTimeClick,
            )
        }
        Footer(stringResource(R.string.reminders_time_footer))
        OrbitListGroup(title = stringResource(R.string.reminders_lead_times)) {
            ContentRow(index = 0, count = 1) {
                LeadTimeChips(selected = uiState.defaultLeadTimes, onToggle = onLeadTimeToggle)
            }
        }
        Footer(stringResource(R.string.reminders_lead_times_footer))
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.reminders_health),
                summary = uiState.health?.let { healthSummary(it) },
                index = 0,
                count = 1,
                onClick = onHealthClick,
            )
        }
        Spacer(Modifier.height(8.dp))
        OrbitListGroup(title = stringResource(R.string.reminders_notifications)) {
            ReminderChannel.entries.forEachIndexed { index, channel ->
                val off = uiState.health?.let { !it.notificationsAllowed || channel in it.channelsOff } == true
                NavigationRow(
                    title = channelName(channel),
                    summary = stringResource(if (off) R.string.reminders_channel_off else R.string.reminders_channel_on),
                    index = index,
                    count = ReminderChannel.entries.size,
                    onClick = { onChannelClick(channel) },
                )
            }
        }
    }
}

/** "Everything is set" or "2 things to check". */
@Composable
fun healthSummary(health: ReminderHealth): String = if (health.problems == 0) {
    stringResource(R.string.reminders_health_all_good)
} else {
    pluralStringResource(R.plurals.reminders_health_to_check, health.problems, LocalOrbitFormats.current.numbers.format(health.problems))
}

@Composable
fun channelName(channel: ReminderChannel): String = stringResource(
    when (channel) {
        ReminderChannel.ImportantDates -> R.string.channel_important_dates_name
        ReminderChannel.KeepInTouch -> R.string.channel_keep_in_touch_name
        ReminderChannel.Occasions -> R.string.channel_occasions_name
    },
)

@Composable
internal fun Footer(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
