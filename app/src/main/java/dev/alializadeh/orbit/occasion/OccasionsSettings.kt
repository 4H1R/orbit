package dev.alializadeh.orbit.occasion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.person.dates.DateBadge
import dev.alializadeh.orbit.settings.AddRow
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.settings.SettingsFooter
import dev.alializadeh.orbit.ui.components.OrbitEmptyState
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.components.OrbitSheetTitle
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.icons.OrbitIcons
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
        val today: LocalDate,
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
            today = today,
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
    val empty = (uiState as? OccasionsSettingsUiState.Loaded)?.items?.isEmpty() == true
    OrbitSettingsScaffold(
        title = stringResource(R.string.settings_occasions_title),
        onBack = onBack,
        modifier = modifier,
        emptyState = if (empty) {
            {
                OrbitEmptyState(
                    icon = OrbitIcons.Sparkle,
                    title = stringResource(R.string.settings_occasions_none),
                    body = stringResource(R.string.occasions_empty),
                    action = {
                        Button(onClick = { adding = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(stringResource(R.string.occasions_add), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    },
                )
            }
        } else {
            null
        },
    ) {
        if (uiState !is OccasionsSettingsUiState.Loaded) return@OrbitSettingsScaffold
        val formats = LocalOrbitFormats.current
        val separator = stringResource(R.string.separator)
        val count = uiState.items.size + 1
        OrbitListGroup(title = null) {
            uiState.items.forEachIndexed { index, item ->
                val occasion = item.occasion.occasion
                SegmentedListItem(
                    onClick = { onOpen(occasion.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, count),
                    leadingContent = {
                        DateBadge(
                            day = formats.numbers.format(occasion.date.day),
                            month = formats.dates.monthName(occasion.date.calendar, occasion.date.month),
                            size = 48.dp,
                        )
                    },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                RelativeDays.ahead(uiState.today.plusDays(item.daysAway.toLong()), uiState.today)?.text(),
                                reachText(occasion.forEveryone, item.reach),
                            ).joinToString(separator),
                        )
                    },
                ) {
                    Text(occasionName(occasion))
                }
            }
            AddRow(title = stringResource(R.string.occasions_add), index = count - 1, count = count, onClick = { adding = true })
        }
        SettingsFooter(stringResource(R.string.occasions_footer))
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
    val formats = LocalOrbitFormats.current
    val count = addable.size + 1
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        OrbitSheetTitle(stringResource(R.string.occasions_add), Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            addable.forEachIndexed { index, builtIn ->
                SegmentedListItem(
                    onClick = { onPick(builtIn) },
                    shapes = ListItemDefaults.segmentedShapes(index, count),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    leadingContent = {
                        DateBadge(
                            day = formats.numbers.format(builtIn.date.day),
                            month = formats.dates.monthName(builtIn.date.calendar, builtIn.date.month),
                            size = 44.dp,
                        )
                    },
                    supportingContent = { Text(formats.dates.format(builtIn.date)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(builtIn.nameRes))
                }
            }
            SegmentedListItem(
                onClick = { onPick(null) },
                shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                leadingContent = {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.occasion_custom), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The Occasion's name for a sentence: the user's own label is user text. */
@Composable
internal fun occasionNameInSentence(occasion: Occasion): String = occasion.builtIn?.let { stringResource(it.nameRes) } ?: bidiIsolate(occasion.label.orEmpty())
