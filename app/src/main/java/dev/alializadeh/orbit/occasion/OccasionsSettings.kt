package dev.alializadeh.orbit.occasion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import dev.alializadeh.orbit.upcoming.todayFlow
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable
data object OccasionsSettingsKey : NavKey

/** One Occasion in the list: when it next falls and how many People it reaches. */
data class OccasionListItem(val occasion: OccasionWithAudience, val daysAway: Int, val reach: Int)

sealed interface OccasionsSettingsUiState {
    data object Loading : OccasionsSettingsUiState

    data class Loaded(
        /** Soonest first. */
        val items: List<OccasionListItem>,
        /** The built-in Occasions not added yet, in the list's order. */
        val addable: List<BuiltInOccasion>,
    ) : OccasionsSettingsUiState
}

@HiltViewModel
class OccasionsSettingsViewModel @Inject constructor(
    occasionRepository: OccasionRepository,
    upcomingRepository: UpcomingRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<OccasionsSettingsUiState> = combine(
        occasionRepository.observeOccasions(),
        upcomingRepository.observePeople(),
        todayFlow(clock, emptyFlow()),
    ) { occasions, people, today ->
        OccasionsSettingsUiState.Loaded(
            items = occasions
                .map { OccasionListItem(it, daysAway(it, today), it.audience(people).size) }
                .sortedWith(compareBy<OccasionListItem> { it.daysAway }.thenBy { it.occasion.id }),
            addable = BuiltInOccasion.entries - occasions.mapNotNull { it.occasion.builtIn }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OccasionsSettingsUiState.Loading)

    private fun daysAway(occasion: OccasionWithAudience, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, occasion.occasion.date.nextOccurrence(today).date).toInt()
}

@Composable
fun OccasionsSettingsRoute(
    onBack: () -> Unit,
    onOpen: (occasionId: Long) -> Unit,
    onAdd: (builtIn: BuiltInOccasion?) -> Unit,
    viewModel: OccasionsSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OccasionsSettingsScreen(uiState, onBack, onOpen, onAdd)
}

/**
 * Settings → Occasions: every Occasion, soonest first, with its day (in its own Calendar) and who
 * it reaches; Add offers the built-in Occasions not added yet and one of the user's own.
 */
@Composable
fun OccasionsSettingsScreen(
    uiState: OccasionsSettingsUiState,
    onBack: () -> Unit,
    onOpen: (occasionId: Long) -> Unit,
    onAdd: (builtIn: BuiltInOccasion?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    OrbitSettingsScaffold(title = stringResource(R.string.settings_occasions_title), onBack = onBack, modifier = modifier) {
        if (uiState !is OccasionsSettingsUiState.Loaded) return@OrbitSettingsScaffold
        val formats = LocalOrbitFormats.current
        val separator = stringResource(R.string.separator)
        if (uiState.items.isEmpty()) {
            Text(
                stringResource(R.string.occasions_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            OrbitListGroup(title = null) {
                uiState.items.forEachIndexed { index, item ->
                    val occasion = item.occasion.occasion
                    NavigationRow(
                        title = occasionName(occasion),
                        summary = listOf(formats.dates.format(occasion.date), reachText(occasion.forEveryone, item.reach))
                            .joinToString(separator),
                        index = index,
                        count = uiState.items.size,
                        onClick = { onOpen(occasion.id) },
                    )
                }
            }
        }
        FilledTonalButton(onClick = { adding = true }, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.occasions_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (adding && uiState is OccasionsSettingsUiState.Loaded) {
        OrbitBottomSheet(onDismiss = { adding = false }) { sheet ->
            AddOccasionSheetContent(uiState.addable, onPick = { builtIn -> sheet.close { onAdd(builtIn) } })
        }
    }
}

/** "Everyone", "12 people", "Reaches nobody yet". */
@Composable
internal fun reachText(forEveryone: Boolean, reach: Int): String = when {
    forEveryone -> stringResource(R.string.occasion_who_everyone)
    reach == 0 -> stringResource(R.string.occasion_reaches_nobody)
    else -> pluralStringResource(R.plurals.occasion_people, reach, LocalOrbitFormats.current.numbers.format(reach))
}

/** Add occasion: the built-in ones not added yet, each with its day, then the user's own. */
@Composable
internal fun AddOccasionSheetContent(addable: List<BuiltInOccasion>, onPick: (BuiltInOccasion?) -> Unit) {
    val dates = LocalOrbitFormats.current.dates
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.occasions_add),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp).semantics { heading() },
        )
        addable.forEach { builtIn ->
            ListItem(
                onClick = { onPick(builtIn) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                supportingContent = { Text(dates.format(builtIn.date)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Text(stringResource(builtIn.nameRes))
            }
        }
        ListItem(
            onClick = { onPick(null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.occasion_custom), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The Occasion's name for a sentence: the user's own label is user text. */
@Composable
internal fun occasionNameInSentence(occasion: Occasion): String = occasion.builtIn?.let { stringResource(it.nameRes) } ?: bidiIsolate(occasion.label.orEmpty())
