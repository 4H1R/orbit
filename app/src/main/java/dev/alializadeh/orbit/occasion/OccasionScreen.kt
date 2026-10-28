package dev.alializadeh.orbit.occasion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.interaction.InteractionRepository
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import dev.alializadeh.orbit.upcoming.todayFlow
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

/** An Occasion with its People. */
@Serializable
data class OccasionKey(val occasionId: Long) : NavKey

sealed interface OccasionUiState {
    data object Loading : OccasionUiState

    /** Deleted (or never there): the screen leaves. */
    data object Gone : OccasionUiState

    data class Loaded(
        val occasion: Occasion,
        /** Who it reaches, in Upcoming's order. */
        val people: List<UpcomingPerson>,
        /** Its next occurrence (today on the day). */
        val date: LocalDate,
        val today: LocalDate,
        /** On the day: who has an Interaction logged that day. */
        val reached: Set<Long> = emptySet(),
        val photos: Map<Long, File> = emptyMap(),
    ) : OccasionUiState {
        val isToday: Boolean get() = date == today
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = OccasionViewModel.Factory::class)
class OccasionViewModel @AssistedInject constructor(
    @Assisted private val occasionId: Long,
    occasionRepository: OccasionRepository,
    upcomingRepository: UpcomingRepository,
    interactionRepository: InteractionRepository,
    photoStore: PhotoStore,
    clock: Clock,
) : ViewModel() {

    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val today = todayFlow(clock, dateChecks)

    val uiState: StateFlow<OccasionUiState> = combine(
        occasionRepository.observeOccasions().map { all -> all.firstOrNull { it.id == occasionId } },
        upcomingRepository.observePeople(),
        today,
        today.distinctUntilChanged().flatMapLatest(interactionRepository::observePeopleOn),
    ) { occasion, people, today, reachedToday ->
        if (occasion == null) return@combine OccasionUiState.Gone
        val date = occasion.occasion.date.nextOccurrence(today).date
        val reached = occasion.audience(people)
        OccasionUiState.Loaded(
            occasion = occasion.occasion,
            people = reached,
            date = date,
            today = today,
            reached = if (date == today) reachedToday else emptySet(),
            photos = reached.mapNotNull { person -> person.photoFileName?.let { person.id to photoStore.file(it) } }.toMap(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OccasionUiState.Loading)

    /** The screen resumed: it may be another day. */
    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }

    @AssistedFactory
    interface Factory {
        fun create(occasionId: Long): OccasionViewModel
    }
}

@Composable
fun OccasionRoute(
    key: OccasionKey,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPersonClick: (personId: Long) -> Unit,
    onSendWishes: (personId: Long) -> Unit,
    viewModel: OccasionViewModel = hiltViewModel<OccasionViewModel, OccasionViewModel.Factory>(
        creationCallback = { factory -> factory.create(key.occasionId) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.checkDate()
        onPauseOrDispose {}
    }
    LaunchedEffect(uiState is OccasionUiState.Gone) {
        if (uiState is OccasionUiState.Gone) onBack()
    }
    OccasionScreen(uiState, onBack, onEdit, onPersonClick, onSendWishes)
}

/**
 * An Occasion: when it falls (in the Primary calendar, then its own), on the day how many People
 * have been reached, then its People, each with Send wishes and, on the day, a tick once reached.
 */
@Composable
fun OccasionScreen(
    uiState: OccasionUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPersonClick: (personId: Long) -> Unit,
    onSendWishes: (personId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = uiState as? OccasionUiState.Loaded
    Scaffold(
        modifier = modifier,
        snackbarHost = { OrbitSnackbarHost() },
        topBar = {
            OrbitTopAppBar(
                title = loaded?.let { occasionName(it.occasion) },
                onBack = onBack,
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.common_edit))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { OrbitLoadingIndicator() }
            return@Scaffold
        }
        val formats = LocalOrbitFormats.current
        val separator = stringResource(R.string.separator)
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
                item(key = "when") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        val whenText = RelativeDays.ahead(loaded.date, loaded.today)?.text()
                        val dateParts = formats.dates.occurrenceParts(
                            loaded.date,
                            loaded.occasion.date.calendar,
                            formats.primaryCalendar,
                            loaded.today,
                            withWeekday = true,
                        )
                        Text(listOfNotNull(whenText, *dateParts.toTypedArray()).joinToString(separator), style = MaterialTheme.typography.titleMedium)
                        if (loaded.isToday && loaded.people.isNotEmpty()) {
                            val reached = loaded.people.count { it.id in loaded.reached }
                            Text(
                                pluralStringResource(
                                    R.plurals.occasion_reached,
                                    reached,
                                    formats.numbers.format(reached),
                                    formats.numbers.format(loaded.people.size),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                if (loaded.people.isEmpty()) {
                    item(key = "nobody") {
                        Text(
                            stringResource(R.string.occasion_reaches_nobody),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                } else {
                    item(key = "people_title") { OrbitSectionTitle(stringResource(R.string.occasion_who_people), horizontalPadding = 4.dp) }
                    itemsIndexed(loaded.people, key = { _, person -> person.id }) { index, person ->
                        PersonRow(
                            person = person,
                            photo = loaded.photos[person.id],
                            reached = person.id in loaded.reached,
                            index = index,
                            count = loaded.people.size,
                            onClick = { onPersonClick(person.id) },
                            onSendWishes = { onSendWishes(person.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: UpcomingPerson,
    photo: File?,
    reached: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onSendWishes: () -> Unit,
) {
    val name = person.names.displayName(LocalOrbitFormats.current.language)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = photo) },
        supportingContent = person.ringName?.let { { Text(bidiIsolate(it), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reached) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.occasion_reached_a11y),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IconButton(onClick = onSendWishes) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.wishes_send))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
