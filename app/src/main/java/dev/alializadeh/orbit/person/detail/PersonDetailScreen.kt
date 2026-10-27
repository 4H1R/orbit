package dev.alializadeh.orbit.person.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar

@Composable
fun PersonDetailRoute(
    key: PersonDetailKey,
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel =
        hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(key.personId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PersonDetailScreen(uiState = uiState, onBack = onBack)
}

@Composable
fun PersonDetailScreen(
    uiState: PersonDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = null, onBack = onBack) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
        when (uiState) {
            PersonDetailUiState.Loading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                OrbitLoadingIndicator()
            }

            is PersonDetailUiState.Loaded -> Column(contentModifier) {
                Text(
                    text = uiState.person.name,
                    style = MaterialTheme.typography.displaySmall,
                )
            }

            PersonDetailUiState.NotFound -> Box(contentModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.person_detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
