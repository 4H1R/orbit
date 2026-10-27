package dev.alializadeh.orbit.person.list

import androidx.compose.foundation.layout.Arrangement
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
import dev.alializadeh.orbit.person.sortByDisplayName
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar

@Composable
fun PeopleRoute(
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PeopleScreen(uiState = uiState, onPersonClick = onPersonClick, onAddPerson = onAddPerson)
}

/**
 * The People list: every Person who isn't Archived, by displayed name in the UI language's
 * collation. Search, filters and the other sorts arrive in slice 8.
 */
@Composable
fun PeopleScreen(
    uiState: PeopleUiState,
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPeople = uiState is PeopleUiState.Loaded && uiState.people.isNotEmpty()
    val listState = rememberLazyListState()
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
                    onPersonClick = onPersonClick,
                    listState = listState,
                    contentPadding = innerPadding,
                )
            }
        }
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
    onPersonClick: (personId: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
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
                SegmentedListItem(
                    onClick = { onPersonClick(row.summary.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, sorted.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { PersonAvatar(name = name, photo = row.photo) },
                    supportingContent = row.summary.ringName?.let { ring ->
                        { Text(ring, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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
