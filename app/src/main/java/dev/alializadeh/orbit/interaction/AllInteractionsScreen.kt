package dev.alializadeh.orbit.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

/** Every Interaction of one Person, newest first, grouped by month. */
@Serializable
data class AllInteractionsKey(val personId: Long) : NavKey

sealed interface AllInteractionsUiState {
    data object Loading : AllInteractionsUiState

    data class Loaded(
        val personId: Long,
        val names: PersonNames,
        val interactions: List<InteractionWithPeople>,
        val today: LocalDate,
    ) : AllInteractionsUiState

    /** The Person is gone. */
    data object Gone : AllInteractionsUiState
}

@HiltViewModel(assistedFactory = AllInteractionsViewModel.Factory::class)
class AllInteractionsViewModel @AssistedInject constructor(
    @Assisted private val personId: Long,
    personRepository: PersonRepository,
    interactionRepository: InteractionRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<AllInteractionsUiState> =
        combine(personRepository.observeProfile(personId), interactionRepository.observeForPerson(personId)) { profile, interactions ->
            if (profile == null) {
                AllInteractionsUiState.Gone
            } else {
                AllInteractionsUiState.Loaded(personId, profile.names, interactions, LocalDate.now(clock))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllInteractionsUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(personId: Long): AllInteractionsViewModel
    }
}

@Composable
fun AllInteractionsRoute(
    key: AllInteractionsKey,
    onBack: () -> Unit,
    viewModel: AllInteractionsViewModel =
        hiltViewModel<AllInteractionsViewModel, AllInteractionsViewModel.Factory>(
            creationCallback = { factory -> factory.create(key.personId) },
        ),
    keepInTouch: KeepInTouchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gone = uiState is AllInteractionsUiState.Gone
    LaunchedEffect(gone) { if (gone) onBack() }
    KeepInTouchSheets(keepInTouch)
    AllInteractionsScreen(
        uiState = uiState,
        onBack = onBack,
        onLogInteraction = { keepInTouch.logInteraction(key.personId) },
        onOpen = keepInTouch::editInteraction,
    )
}

/**
 * All interactions: a Person's whole log, newest first, under month headings in the Primary
 * calendar. Tapping one reopens it to edit or delete.
 */
@Composable
fun AllInteractionsScreen(
    uiState: AllInteractionsUiState,
    onBack: () -> Unit,
    onLogInteraction: () -> Unit,
    onOpen: (interactionId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val loaded = uiState as? AllInteractionsUiState.Loaded
    Scaffold(
        modifier = modifier,
        snackbarHost = { OrbitSnackbarHost() },
        topBar = {
            OrbitTopAppBar(
                title = loaded?.let { stringResource(R.string.interactions_all_title, bidiIsolate(it.names.displayName(formats.language))) },
                onBack = onBack,
                actions = {
                    IconButton(onClick = onLogInteraction) {
                        Icon(OrbitIcons.LogInteraction, contentDescription = stringResource(R.string.interaction_log))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { OrbitLoadingIndicator() }
            return@Scaffold
        }
        val months = loaded.interactions.groupBy { it.interaction.date.inCalendar(formats.primaryCalendar).let { d -> d.year to d.month } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                months.forEach { (yearMonth, items) ->
                    val (year, month) = yearMonth
                    item(key = "month_${year}_$month") {
                        OrbitSectionTitle(
                            formats.dates.monthName(formats.primaryCalendar, month, full = true) + " " + formats.numbers.format(year),
                            horizontalPadding = 4.dp,
                        )
                    }
                    itemsIndexed(items, key = { _, it -> it.interaction.id }) { index, item ->
                        InteractionListItem(
                            item = item,
                            personId = loaded.personId,
                            today = loaded.today,
                            index = index,
                            count = items.size,
                            onClick = { onOpen(item.interaction.id) },
                            modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                        )
                    }
                }
            }
        }
    }
}
