package dev.alializadeh.orbit.orbitview

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.interaction.KeepInTouchSheets
import dev.alializadeh.orbit.interaction.KeepInTouchViewModel
import dev.alializadeh.orbit.interaction.QuickActionSheet
import dev.alializadeh.orbit.interaction.QuickActionTarget
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.search.FilteredBar
import dev.alializadeh.orbit.search.PeopleFilterChips
import dev.alializadeh.orbit.search.RingFilterSheet
import dev.alializadeh.orbit.search.TagFilterSheet
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlinx.serialization.Serializable

/** The Orbit tab's root: the Orbit view. */
@Serializable
data object OrbitViewKey : NavKey

/**
 * The Orbit tab. [opening] counts the times the tab was picked in the navigation bar: each one
 * opens the view at fit, with the People springing out.
 */
@Composable
fun OrbitViewRoute(
    opening: Int,
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    viewModel: OrbitViewModel = hiltViewModel(),
    keepInTouch: KeepInTouchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from the background: it may be another day, and the drift moves with it.
    LifecycleResumeEffect(viewModel) {
        viewModel.checkDate()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val snackbars = LocalOrbitSnackbars.current
    val noApp = stringResource(R.string.contact_no_app)
    KeepInTouchSheets(keepInTouch)
    OrbitViewScreen(
        uiState = uiState,
        opening = opening,
        filterActions = viewModel,
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

/** Which sheet the top bar opened. */
private enum class OrbitSheet { Rings, Tags, Legend }

/**
 * The Orbit view's screen: the top bar with ⓘ (the legend), the filter chips (Ring ▾, Tags ▾, Due,
 * Date soon) over this tab's own filters, "Filtered · Clear" (or "No one matches · Clear"), and the
 * drawing. With no People yet: the Rings drawn faintly, a line and Add person. No search, no FAB.
 */
@Composable
fun OrbitViewScreen(
    uiState: OrbitUiState,
    opening: Int,
    filterActions: OrbitFilterActions,
    onPersonClick: (personId: Long) -> Unit,
    onAddPerson: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (personId: Long) -> Unit,
    onSnooze: (personId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = uiState as? OrbitUiState.Loaded
    var sheet by rememberSaveable { mutableStateOf<OrbitSheet?>(null) }
    var quickActionsFor by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            OrbitTopAppBar(
                title = stringResource(R.string.nav_tab_orbit),
                actions = {
                    IconButton(onClick = { sheet = OrbitSheet.Legend }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.orbit_legend_title))
                    }
                },
            )
        },
        snackbarHost = { OrbitSnackbarHost() },
    ) { innerPadding ->
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { OrbitLoadingIndicator() }
            return@Scaffold
        }
        val actions = OrbitPersonActions(
            onOpen = onPersonClick,
            onQuickActions = { quickActionsFor = it },
            onContactAction = onContactAction,
            onLogInteraction = onLogInteraction,
            onSnooze = onSnooze,
        )
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (loaded.hasPeople) {
                PeopleFilterChips(
                    filters = loaded.filters,
                    rings = loaded.allRings,
                    tags = loaded.tags,
                    onOpenRings = { sheet = OrbitSheet.Rings },
                    onOpenTags = { sheet = OrbitSheet.Tags },
                    onToggleDue = filterActions::toggleDue,
                    onToggleDateSoon = filterActions::toggleDateSoon,
                )
                if (loaded.filters.isActive) {
                    FilteredBar(
                        onClear = filterActions::clearFilters,
                        label = stringResource(if (loaded.nobodyMatches) R.string.search_no_matches else R.string.filter_bar_filtered),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            OrbitDrawing(
                rings = loaded.rings,
                people = loaded.people,
                today = loaded.today,
                opening = opening,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
            if (!loaded.hasPeople) NoPeopleYet(onAddPerson)
        }
    }

    if (loaded != null) {
        when (sheet) {
            OrbitSheet.Rings -> RingFilterSheet(loaded.allRings, loaded.filters.ringIds, filterActions::setRingFilter, onDismiss = { sheet = null })
            OrbitSheet.Tags -> TagFilterSheet(loaded.tags, loaded.filters.tagIds, filterActions::setTagFilter, onDismiss = { sheet = null })
            OrbitSheet.Legend -> OrbitLegendSheet(onDismiss = { sheet = null })
            null -> Unit
        }
        val target = loaded.people.firstOrNull { it.id == quickActionsFor }
        if (target != null) {
            QuickActionSheet(
                target = QuickActionTarget(
                    personId = target.id,
                    names = target.names,
                    photo = target.photo,
                    primaryContactMethod = target.primaryContactMethod,
                    isDue = target.isDue,
                ),
                onContactAction = onContactAction,
                onLogInteraction = onLogInteraction,
                onSnooze = onSnooze,
                onDismiss = { quickActionsFor = null },
            )
        }
    }
}

/** Under the faint Rings when there's nobody yet: the copy deck's line and Add person. */
@Composable
private fun NoPeopleYet(onAddPerson: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.orbit_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddPerson, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.action_add_person))
        }
    }
}
