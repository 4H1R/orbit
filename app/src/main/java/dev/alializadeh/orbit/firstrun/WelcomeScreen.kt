package dev.alializadeh.orbit.firstrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.settings.languageName
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.SwitchRow
import dev.alializadeh.orbit.ui.datepicker.calendarName
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/** First run, one step at a time. Finishing it swaps in the tabs, on Upcoming. */
@Composable
fun FirstRunRoute(viewModel: FirstRunViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (uiState.step) {
        FirstRunStep.Welcome -> WelcomeScreen(
            uiState = uiState,
            onLanguageSelect = viewModel::selectLanguage,
            onPrimaryCalendarSelect = viewModel::selectPrimaryCalendar,
            onUseLatinDigitsChange = viewModel::setUseLatinDigits,
            onContinue = viewModel::continueFromStep,
        )
    }
}

/**
 * Welcome: the language (each in its own script; choosing one switches the UI at once), the
 * Primary calendar (following the language until touched), "Use Latin digits" for Persian, and
 * Continue. Slice 5 adds "Restore from a backup" here.
 */
@Composable
fun WelcomeScreen(
    uiState: FirstRunUiState,
    onLanguageSelect: (OrbitLanguage) -> Unit,
    onPrimaryCalendarSelect: (CalendarSystem) -> Unit,
    onUseLatinDigitsChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = !uiState.isFinishing,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_continue))
                }
            }
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = OrbitIcons.Orbit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 24.dp).semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.welcome_line),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OrbitSectionTitle(
                    stringResource(R.string.welcome_language),
                    Modifier.padding(top = 24.dp),
                    horizontalPadding = 0.dp,
                )
                OrbitConnectedChoice(
                    options = OrbitLanguage.entries,
                    selected = uiState.language,
                    onSelect = onLanguageSelect,
                    label = { languageName(it) },
                )
                OrbitSectionTitle(stringResource(R.string.welcome_primary_calendar), horizontalPadding = 0.dp)
                OrbitConnectedChoice(
                    options = CalendarSystem.entries,
                    selected = uiState.primaryCalendar,
                    onSelect = onPrimaryCalendarSelect,
                    label = { calendarName(it) },
                )
                if (uiState.showsLatinDigits) {
                    OrbitListGroup(title = null, modifier = Modifier.padding(top = 24.dp)) {
                        SwitchRow(
                            title = stringResource(R.string.welcome_latin_digits),
                            summary = null,
                            checked = uiState.useLatinDigits,
                            index = 0,
                            count = 1,
                            onCheckedChange = onUseLatinDigitsChange,
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        )
                    }
                }
            }
        }
    }
}
