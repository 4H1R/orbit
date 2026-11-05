package dev.alializadeh.orbit.firstrun

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.backup.RestoreRoute
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.lock.OrbitMark
import dev.alializadeh.orbit.lock.rememberScreenLockPrompt
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.settings.languageName
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.SwitchRow
import dev.alializadeh.orbit.ui.datepicker.calendarName
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion

/** First run, one step at a time. Finishing it swaps in the tabs, on Upcoming. */
@Composable
fun FirstRunRoute(viewModel: FirstRunViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Providers don't know an .age MIME type, so every file is offered.
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.startRestore(uri.toString())
    }
    val restoreUri = uiState.restoreUri
    if (restoreUri != null) {
        BackHandler(onBack = viewModel::cancelRestore)
        RestoreRoute(
            uri = restoreUri,
            onClose = viewModel::cancelRestore,
            onRestored = viewModel::onRestored,
            // A phone on its first run has no data to back up first.
            onBackUpFirst = null,
        )
        return
    }
    when (uiState.step) {
        FirstRunStep.Welcome -> WelcomeScreen(
            uiState = uiState,
            onLanguageSelect = viewModel::selectLanguage,
            onPrimaryCalendarSelect = viewModel::selectPrimaryCalendar,
            onUseLatinDigitsChange = viewModel::setUseLatinDigits,
            onContinue = viewModel::continueFromStep,
            onRestore = { openBackup.launch(arrayOf("*/*")) },
        )

        FirstRunStep.AppLockOffer -> {
            val prompt = rememberScreenLockPrompt()
            val title = stringResource(R.string.app_lock_prompt_turn_on)
            AppLockOfferScreen(
                isFinishing = uiState.isFinishing,
                onTurnOn = { prompt?.authenticate(title, onResult = viewModel::onTurnOnAppLock) },
                onNotNow = viewModel::continueFromStep,
            )
        }
    }
}

/**
 * The App lock offer, after Welcome (and after a Restore from it): Turn on (behind the system
 * prompt) or Not now. Not shown on a phone without a screen lock.
 */
@Composable
fun AppLockOfferScreen(
    isFinishing: Boolean,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = onTurnOn,
                    enabled = !isFinishing,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.app_lock_offer_turn_on))
                }
                TextButton(
                    onClick = onNotNow,
                    enabled = !isFinishing,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.common_not_now))
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.app_lock_offer_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 24.dp).semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.app_lock_offer_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Welcome: the language (each in its own script; choosing one switches the UI at once), the
 * Primary calendar (following the language until touched), "Use Latin digits" for Persian, and
 * Continue, or "Restore from a backup" instead.
 */
@Composable
fun WelcomeScreen(
    uiState: FirstRunUiState,
    onLanguageSelect: (OrbitLanguage) -> Unit,
    onPrimaryCalendarSelect: (CalendarSystem) -> Unit,
    onUseLatinDigitsChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = !uiState.isFinishing,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_continue))
                }
                TextButton(
                    onClick = onRestore,
                    enabled = !uiState.isFinishing,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_restore))
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
                ArrivingOrbitMark()
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 32.dp).semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.welcome_line),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OrbitSectionTitle(
                    stringResource(R.string.welcome_language),
                    Modifier.padding(top = 32.dp),
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
                    options = CalendarSystem.Primaries,
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

/**
 * The Orbit mark as it first appears: it fades and settles in while the body travels a third of
 * the way round its ring. First run's one moment of delight, played once (not again when choosing
 * a language recreates the screen) and not at all with animations off or in screenshots.
 */
@Composable
private fun ArrivingOrbitMark() {
    val still = LocalStillMotion.current
    var played by rememberSaveable { mutableStateOf(still) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            progress.animateTo(1f, tween(durationMillis = 1_100, easing = MotionEasingEmphasizedDecelerate))
            played = true
        }
    }
    val p = progress.value
    OrbitMark(
        rotation = (1f - p) * -120f,
        modifier = Modifier.graphicsLayer {
            alpha = p
            val scale = 0.8f + 0.2f * p
            scaleX = scale
            scaleY = scale
        },
    )
}

/** Material's emphasized-decelerate easing: things arriving on screen. */
private val MotionEasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
