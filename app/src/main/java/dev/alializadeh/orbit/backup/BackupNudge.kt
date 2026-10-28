package dev.alializadeh.orbit.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import dev.alializadeh.orbit.upcoming.todayFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Backup nudge card's content: when the last Backup was (null: never), and today. */
data class BackupNudgeUi(val lastBackup: LocalDate?, val today: LocalDate)

/** Whether Upcoming shows the Backup nudge ([BackupRules.showsNudge]), and "Not now". */
@HiltViewModel
class BackupNudgeViewModel @Inject constructor(
    statusRepository: BackupStatusRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val nudge: StateFlow<BackupNudgeUi?> =
        combine(statusRepository.status, todayFlow(clock, dateChecks)) { status, today ->
            if (BackupRules.showsNudge(status, today, clock.zone)) {
                BackupNudgeUi(status.lastBackupAt?.atZone(clock.zone)?.toLocalDate(), today)
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Not now": hidden for a week. */
    fun notNow() {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            preferences.updateDeviceState { BackupRules.hideNudge(it, today) }
        }
    }

    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }
}

/** Upcoming's Backup nudge: "Last backup 40 days ago" or "You haven't made a backup yet", Back up now, Not now. */
@Composable
fun BackupNudgeCard(nudge: BackupNudgeUi, onBackUpNow: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Icon(OrbitIcons.Backup, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = nudge.lastBackup?.let {
                            stringResource(R.string.backup_nudge_last, RelativeDays.ago(it, nudge.today).text())
                        } ?: stringResource(R.string.backup_nudge_never),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.backup_nudge_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.common_not_now)) }
                Button(onClick = onBackUpNow) { Text(stringResource(R.string.backup_now)) }
            }
        }
    }
}
