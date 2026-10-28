package dev.alializadeh.orbit.person.list

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.KeepInTouchSheets
import dev.alializadeh.orbit.interaction.KeepInTouchViewModel
import dev.alializadeh.orbit.interaction.QuickActionSheet
import dev.alializadeh.orbit.interaction.QuickActionTarget
import dev.alializadeh.orbit.interaction.lastTalkedText
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.detail.DueBadge
import dev.alializadeh.orbit.person.nameComparator
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.search.FilteredBar
import dev.alializadeh.orbit.search.PeopleFilterChips
import dev.alializadeh.orbit.search.RingFilterSheet
import dev.alializadeh.orbit.search.TagFilterSheet
import dev.alializadeh.orbit.search.WhyMatchedLine
import dev.alializadeh.orbit.search.displayedField
import dev.alializadeh.orbit.search.highlighted
import dev.alializadeh.orbit.search.presentation
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.FastScrollThumb
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

@Composable
fun PeopleRoute(
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
    keepInTouch: KeepInTouchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from the background: it may be another day.
    LifecycleResumeEffect(viewModel) {
        viewModel.checkDate()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val snackbars = LocalOrbitSnackbars.current
    val noApp = stringResource(R.string.contact_no_app)
    KeepInTouchSheets(keepInTouch)
    PeopleScreen(
        uiState = uiState,
        query = viewModel.query,
        actions = viewModel,
        onPersonClick = onPersonClick,
        onAddPerson = onAddPerson,
        onContactAction = { action ->
            try {
                context.startActivity(Intent(action.intentAction, action.uri.toUri()))
            } catch (_: ActivityNotFoundException) {
                snackbars?.show(noApp)
            }
        },
        onLogInteraction = keepInTouch::logInteraction,
        onSnooze = keepInTouch::openSnooze,
    )
}

/** Which filter sheet is open. */
private enum class FilterSheet { Rings, Tags }

/**
 * The People list (ticket 12/14): a docked search bar, the chip row (Sort ▾ · Ring ▾ · Tags ▾ ·
 * Due · Date soon · Archived), "Filtered · Clear" while filtering, and the rows: by the sort with
 * its headers, or ranked search results with their "why it matched" line. Archived switches to
 * the Archived view ("Archived · 7", search only). Long-press opens the quick-action sheet.
 */
@Composable
fun PeopleScreen(
    uiState: PeopleUiState,
    query: TextFieldState,
    actions: PeopleListActions,
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (personId: Long) -> Unit,
    onSnooze: (personId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = uiState as? PeopleUiState.Loaded
    val archivedView = loaded?.archivedView == true
    val nobodyAtAll = loaded != null && !loaded.hasPeople && loaded.archivedCount == 0
    val listState = rememberLazyListState()
    var quickActionsFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var filterSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    BackHandler(enabled = archivedView) { actions.closeArchived() }
    Scaffold(
        modifier = modifier,
        topBar = {
            if (loaded != null && loaded.archivedView) {
                val title = stringResource(R.string.people_archived_title) + stringResource(R.string.separator) +
                    LocalOrbitFormats.current.numbers.format(loaded.archivedCount)
                OrbitTopAppBar(title = title, onBack = actions::closeArchived)
            }
        },
        snackbarHost = { OrbitSnackbarHost() },
        floatingActionButton = {
            // The empty state has its own Add person button.
            if (loaded != null && loaded.hasPeople && !archivedView) NewPersonFab(onClick = onAddPerson, listState = listState)
        },
    ) { innerPadding ->
        when {
            loaded == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OrbitLoadingIndicator()
            }

            nobodyAtAll -> NoPeople(onAddPerson = onAddPerson, modifier = Modifier.padding(innerPadding))

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .imePadding(),
            ) {
                PeopleSearchBar(query, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp))
                if (!archivedView) {
                    PeopleFilterChips(
                        filters = loaded.filters,
                        rings = loaded.rings,
                        tags = loaded.tags,
                        onOpenRings = { filterSheet = FilterSheet.Rings },
                        onOpenTags = { filterSheet = FilterSheet.Tags },
                        onToggleDue = actions::toggleDue,
                        onToggleDateSoon = actions::toggleDateSoon,
                        leading = { SortChip(loaded.sort, actions::setSort) },
                        trailing = { ArchivedChip(onClick = actions::openArchived) },
                    )
                    if (loaded.filters.isActive) FilteredBar(onClear = actions::clearFilters, modifier = Modifier.padding(top = 8.dp))
                }
                PeopleContent(
                    state = loaded,
                    query = query,
                    listState = listState,
                    actions = actions,
                    onPersonClick = onPersonClick,
                    onPersonLongClick = { quickActionsFor = it },
                    onAddPerson = onAddPerson,
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (loaded != null) {
        when (filterSheet) {
            FilterSheet.Rings -> RingFilterSheet(loaded.rings, loaded.filters.ringIds, actions::setRingFilter, onDismiss = { filterSheet = null })
            FilterSheet.Tags -> TagFilterSheet(loaded.tags, loaded.filters.tagIds, actions::setTagFilter, onDismiss = { filterSheet = null })
            null -> Unit
        }
    }

    val quickActionRow = loaded?.people?.firstOrNull { it.summary.id == quickActionsFor }
    if (quickActionRow != null) {
        QuickActionSheet(
            target = QuickActionTarget(
                personId = quickActionRow.summary.id,
                names = quickActionRow.summary.names,
                photo = quickActionRow.photo,
                primaryContactMethod = quickActionRow.summary.primaryContactMethod,
                isDue = quickActionRow.due.isDue,
            ),
            onContactAction = onContactAction,
            onLogInteraction = onLogInteraction,
            onSnooze = onSnooze,
            onDismiss = { quickActionsFor = null },
        )
    }
}

/** The extended New person FAB; it shrinks to its icon once the list has scrolled. */
@Composable
fun NewPersonFab(onClick: () -> Unit, listState: LazyListState? = null) {
    val expanded by remember(listState) {
        derivedStateOf { listState == null || listState.firstVisibleItemIndex == 0 }
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.common_new_person)) },
    )
}

/** The docked search bar: results update as you type, from the first character. */
@Composable
private fun PeopleSearchBar(query: TextFieldState, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(R.string.people_search_hint)
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (query.text.isEmpty()) {
                    Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                }
                BasicTextField(
                    state = query,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = { focusManager.clearFocus() },
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                )
            }
            if (query.text.isNotEmpty()) {
                IconButton(onClick = { query.clearText() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.people_search_clear))
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/** Sort ▾: shows the current sort; the menu picks another (a backed-up preference). */
@Composable
private fun SortChip(sort: PeopleSort, onSort: (PeopleSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = sortLabel(sort)
    val description = stringResource(R.string.people_sort_description, label)
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = { Icon(OrbitIcons.Sort, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            modifier = Modifier.semantics { contentDescription = description },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PeopleSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    trailingIcon = if (option == sort) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun sortLabel(sort: PeopleSort): String = stringResource(
    when (sort) {
        PeopleSort.Name -> R.string.people_sort_name
        PeopleSort.Ring -> R.string.people_sort_ring
        PeopleSort.LastInteraction -> R.string.people_sort_last_interaction
        PeopleSort.RecentlyAdded -> R.string.people_sort_recently_added
    },
)

/** Archived ›: switches to the Archived view. */
@Composable
private fun ArchivedChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.people_filter_archived)) },
        trailingIcon = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
    )
}

/** The list, or the empty state that fits. */
@Composable
private fun PeopleContent(
    state: PeopleUiState.Loaded,
    query: TextFieldState,
    listState: LazyListState,
    actions: PeopleListActions,
    onPersonClick: (personId: Long) -> Unit,
    onPersonLongClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val language = LocalOrbitFormats.current.language
    val items = remember(state, language) { peopleListItems(state, language, nameComparator(language)) }
    // A new query, filter, sort or view starts at the top; coming back to the same one (from
    // Person detail, another tab) keeps the scroll.
    val listKey = listOf(query.text.toString(), state.filters, state.sort, state.archivedView).toString()
    var shownFor by rememberSaveable { mutableStateOf(listKey) }
    LaunchedEffect(listKey) {
        if (listKey != shownFor) {
            listState.scrollToItem(0)
            shownFor = listKey
        }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val emptyPadding = Modifier.padding(bottom = bottomPadding)
        when {
            items.any { it is PeopleListItem.Person } -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        // Room for the FAB over the last row.
                        bottom = bottomPadding + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    items(items, key = PeopleListItem::key, contentType = { it::class }) { item ->
                        when (item) {
                            is PeopleListItem.Header -> SectionHeader(item.label)
                            is PeopleListItem.Person -> PersonListRow(
                                item = item,
                                today = state.today,
                                onClick = { onPersonClick(item.row.summary.id) },
                                // Archived People have no quick actions.
                                onLongClick = if (state.archivedView) null else ({ onPersonLongClick(item.row.summary.id) }),
                            )
                            is PeopleListItem.ArchivedMatches -> ArchivedMatchesRow(item.count, onClick = actions::openArchived)
                        }
                    }
                }
                if (!state.searching && state.sort == PeopleSort.Name) {
                    FastScrollThumb(
                        listState = listState,
                        labelAt = { index -> (items.getOrNull(index) as? PeopleListItem.Person)?.section ?: (items.getOrNull(index) as? PeopleListItem.Header)?.label },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, bottom = bottomPadding + 8.dp),
                    )
                }
            }

            state.archivedView && state.archivedCount == 0 -> EmptyMessage(stringResource(R.string.archived_empty), emptyPadding)

            !state.archivedView && !state.hasPeople && !state.searching && !state.filters.isActive ->
                // Only Archived People: the usual first-person invitation, under the chips.
                NoPeople(onAddPerson = onAddPerson, modifier = emptyPadding)

            else -> EmptyMessage(stringResource(R.string.search_no_matches), emptyPadding) {
                if (!state.archivedView && state.filters.isActive) {
                    Button(onClick = actions::clearFilters) { Text(stringResource(R.string.search_clear_filters)) }
                }
                if (state.archivedMatches > 0) ArchivedMatchesRow(state.archivedMatches, onClick = actions::openArchived)
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun PersonListRow(
    item: PeopleListItem.Person,
    today: LocalDate,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val row = item.row
    val language = LocalOrbitFormats.current.language
    val separator = stringResource(R.string.separator)
    val name = row.summary.names.displayName(language)
    val presentation = row.hit?.presentation(row.summary.names.displayedField(language))
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(item.index, item.count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = row.photo) },
        supportingContent = {
            val why = presentation?.why
            if (why != null) {
                WhyMatchedLine(why, today)
            } else {
                val ring = row.summary.ringName
                val secondary = listOfNotNull(
                    ring?.let(::bidiIsolate),
                    lastTalkedText(row.due.lastInteraction, today, inline = ring != null),
                ).joinToString(separator)
                Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = if (row.due.isDue || row.dateSoon != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.due.isDue) DueBadge()
                    row.dateSoon?.let { DateSoonBadge(it) }
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(highlighted(name, presentation?.nameRanges.orEmpty()), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "2 archived people match": opens the Archived view with the same query. */
@Composable
private fun ArchivedMatchesRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val numbers = LocalOrbitFormats.current.numbers
    TextButton(onClick = onClick, modifier = modifier.padding(top = 8.dp)) {
        Text(LocalResources.current.getQuantityString(R.plurals.search_archived_matches, count, numbers.format(count)))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/**
 * The apricot date badge: an outline when an Important date is in the next 30 days, filled when
 * it's today (the Orbit view uses the same rule).
 */
@Composable
fun DateSoonBadge(soon: DateSoon, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = OrbitShapes.dateBadge()
    val description = stringResource(if (soon == DateSoon.Today) R.string.people_date_today else R.string.people_date_soon)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .clip(shape)
            .then(
                if (soon == DateSoon.Today) {
                    Modifier.background(colors.tertiary)
                } else {
                    Modifier.border(2.dp, colors.tertiary, shape)
                },
            )
            .semantics { contentDescription = description },
    ) {
        Icon(
            OrbitIcons.Cake,
            contentDescription = null,
            tint = if (soon == DateSoon.Today) colors.onTertiary else colors.tertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun NoPeople(onAddPerson: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_no_people_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_no_people_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddPerson) {
            Text(stringResource(R.string.action_add_person))
        }
    }
}
