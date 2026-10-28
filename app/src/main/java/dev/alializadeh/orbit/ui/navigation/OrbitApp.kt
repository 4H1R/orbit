package dev.alializadeh.orbit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.orbitview.OrbitViewKey
import dev.alializadeh.orbit.orbitview.OrbitViewScreen
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.AllInteractionsKey
import dev.alializadeh.orbit.interaction.AllInteractionsRoute
import dev.alializadeh.orbit.person.AddPersonKey
import dev.alializadeh.orbit.person.EditPersonKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.person.edit.PersonFormRoute
import dev.alializadeh.orbit.person.detail.PersonDetailRoute
import dev.alializadeh.orbit.person.list.PeopleRoute
import dev.alializadeh.orbit.settings.AppearanceRoute
import dev.alializadeh.orbit.settings.AppearanceSettingsKey
import dev.alializadeh.orbit.settings.LanguageDatesRoute
import dev.alializadeh.orbit.settings.LanguageDatesSettingsKey
import dev.alializadeh.orbit.settings.RingsRoute
import dev.alializadeh.orbit.settings.RingsSettingsKey
import dev.alializadeh.orbit.settings.TagsRoute
import dev.alializadeh.orbit.settings.TagsSettingsKey
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.settings.SettingsRoute
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.rememberOrbitSnackbars
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import dev.alializadeh.orbit.upcoming.UpcomingKey
import dev.alializadeh.orbit.upcoming.UpcomingRoute

/** The app shell: four bottom tabs over a single [NavDisplay]. */
@Composable
fun OrbitApp(navigator: OrbitNavigator = rememberOrbitNavigator()) {
    val snackbars = rememberOrbitSnackbars()
    val resources = LocalResources.current
    val entries = navigator.rememberEntries(
        entryProvider {
            entry<UpcomingKey> {
                UpcomingRoute(
                    onNewPerson = { navigator.navigate(AddPersonKey) },
                    onPersonClick = { id -> navigator.navigate(PersonDetailKey(id)) },
                )
            }
            entry<PeopleKey> {
                PeopleRoute(
                    onPersonClick = { id -> navigator.navigate(PersonDetailKey(id)) },
                    onAddPerson = { navigator.navigate(AddPersonKey) },
                )
            }
            entry<AddPersonKey> {
                PersonFormRoute(
                    personId = null,
                    focus = null,
                    onClose = navigator::goBack,
                    // After Add, land on the new Person's detail.
                    onSaved = { id -> navigator.replaceTop(PersonDetailKey(id)) },
                )
            }
            entry<EditPersonKey> { key ->
                PersonFormRoute(
                    personId = key.personId,
                    focus = key.focus,
                    onClose = navigator::goBack,
                    onSaved = { navigator.goBack() },
                )
            }
            entry<PersonDetailKey> { key ->
                PersonDetailRoute(
                    key = key,
                    onBack = navigator::goBack,
                    onEdit = { focus -> navigator.navigate(EditPersonKey(key.personId, focus)) },
                    onAllInteractions = { navigator.navigate(AllInteractionsKey(key.personId)) },
                    onArchived = { name, undo ->
                        snackbars.show(
                            message = resources.getString(R.string.archived_snackbar, bidiIsolate(name)),
                            actionLabel = resources.getString(R.string.common_undo),
                            onAction = undo,
                        )
                        navigator.goBack()
                    },
                )
            }
            entry<AllInteractionsKey> { key -> AllInteractionsRoute(key = key, onBack = navigator::goBack) }
            entry<OrbitViewKey> { OrbitViewScreen() }
            entry<SettingsKey> { SettingsRoute(onOpen = navigator::navigate) }
            entry<AppearanceSettingsKey> { AppearanceRoute(onBack = navigator::goBack) }
            entry<LanguageDatesSettingsKey> { LanguageDatesRoute(onBack = navigator::goBack) }
            entry<RingsSettingsKey> { RingsRoute(onBack = navigator::goBack) }
            entry<TagsSettingsKey> { TagsRoute(onBack = navigator::goBack) }
        },
    )

    CompositionLocalProvider(LocalOrbitSnackbars provides snackbars) {
        OrbitShell(navigator, entries)
    }
}

@Composable
private fun OrbitShell(navigator: OrbitNavigator, entries: List<NavEntry<NavKey>>) {
    Scaffold(
        // Each screen's own Scaffold handles the system bars, so its background reaches behind
        // them; this one only makes room for the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (navigator.showsNavigationBar) {
                OrbitNavigationBar(
                    currentTab = navigator.currentTab,
                    onTabSelected = navigator::selectTab,
                )
            }
        },
    ) { innerPadding ->
        NavDisplay(
            entries = entries,
            onBack = navigator::goBack,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}

@Composable
private fun OrbitNavigationBar(
    currentTab: TopLevelTab,
    onTabSelected: (TopLevelTab) -> Unit,
) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

@get:StringRes
private val TopLevelTab.label: Int
    get() = when (this) {
        TopLevelTab.Upcoming -> R.string.nav_tab_upcoming
        TopLevelTab.People -> R.string.nav_tab_people
        TopLevelTab.Orbit -> R.string.nav_tab_orbit
        TopLevelTab.Settings -> R.string.nav_tab_settings
    }

private val TopLevelTab.selectedIcon: ImageVector
    get() = when (this) {
        TopLevelTab.Upcoming -> Icons.Filled.Home
        TopLevelTab.People -> Icons.Filled.Person
        TopLevelTab.Orbit -> OrbitIcons.Orbit
        TopLevelTab.Settings -> Icons.Filled.Settings
    }

private val TopLevelTab.unselectedIcon: ImageVector
    get() = when (this) {
        TopLevelTab.Upcoming -> Icons.Outlined.Home
        TopLevelTab.People -> Icons.Outlined.Person
        TopLevelTab.Orbit -> OrbitIcons.Orbit
        TopLevelTab.Settings -> Icons.Outlined.Settings
    }
