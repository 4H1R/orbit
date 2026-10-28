package dev.alializadeh.orbit.person.list

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.detail.DueBadge
import dev.alializadeh.orbit.person.sortByDisplayName
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar

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

/**
 * The People list: every Person who isn't Archived, by displayed name in the UI language's
 * collation, each with their Ring and when they last talked, a Due chip and the date badge.
 * Long-press opens the quick-action sheet. Search, filters and the other sorts arrive in slice 8.
 */
@Composable
fun PeopleScreen(
    uiState: PeopleUiState,
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (personId: Long) -> Unit,
    onSnooze: (personId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPeople = uiState is PeopleUiState.Loaded && uiState.people.isNotEmpty()
    val listState = rememberLazyListState()
    var quickActionsFor by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = stringResource(R.string.nav_tab_people)) },
        snackbarHost = { OrbitSnackbarHost() },
        floatingActionButton = {
            // The empty state has its own Add person button.
            if (hasPeople) NewPersonFab(onClick = onAddPerson, listState = listState)
        },
    ) { innerPadding ->
        when (uiState) {
            PeopleUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OrbitLoadingIndicator()
            }

            is PeopleUiState.Loaded -> if (uiState.people.isEmpty()) {
                NoPeople(onAddPerson = onAddPerson, modifier = Modifier.padding(innerPadding))
            } else {
                PeopleList(
                    people = uiState.people,
                    today = uiState.today,
                    onPersonClick = onPersonClick,
                    onPersonLongClick = { quickActionsFor = it },
                    listState = listState,
                    contentPadding = innerPadding,
                )
            }
        }
    }

    val quickActionRow = (uiState as? PeopleUiState.Loaded)?.people?.firstOrNull { it.summary.id == quickActionsFor }
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

@Composable
private fun PeopleList(
    people: List<PersonRow>,
    today: LocalDate,
    onPersonClick: (personId: Long) -> Unit,
    onPersonLongClick: (personId: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val separator = stringResource(R.string.separator)
    val language = LocalOrbitFormats.current.language
    val sorted = remember(people, language) {
        sortByDisplayName(people, language, names = { it.summary.names }, tieBreak = { it.summary.id })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                // Room for the FAB over the last row.
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(sorted, key = { _, row -> row.summary.id }) { index, row ->
                val name = row.summary.names.displayName(language)
                val ring = row.summary.ringName
                val secondary = listOfNotNull(
                    ring?.let(::bidiIsolate),
                    lastTalkedText(row.due.lastInteraction, today, inline = ring != null),
                ).joinToString(separator)
                SegmentedListItem(
                    onClick = { onPersonClick(row.summary.id) },
                    onLongClick = { onPersonLongClick(row.summary.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, sorted.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { PersonAvatar(name = name, photo = row.photo) },
                    supportingContent = { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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
