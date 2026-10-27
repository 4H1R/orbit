package dev.alializadeh.orbit.person.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.Person
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

@Composable
fun PeopleScreen(
    uiState: PeopleUiState,
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPeople = uiState is PeopleUiState.Loaded && uiState.people.isNotEmpty()
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = stringResource(R.string.nav_tab_people)) },
        floatingActionButton = {
            // The empty state has its own Add person button.
            if (hasPeople) {
                ExtendedFloatingActionButton(
                    onClick = onAddPerson,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.common_new_person)) },
                )
            }
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
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun PeopleList(
    people: List<Person>,
    onPersonClick: (personId: Long) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(people, key = Person::id) { person ->
            ListItem(onClick = { onPersonClick(person.id) }) {
                Text(person.name)
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
