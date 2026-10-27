package dev.alializadeh.orbit.ui.navigation

import androidx.annotation.StringRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.orbitview.OrbitViewKey
import dev.alializadeh.orbit.orbitview.OrbitViewScreen
import dev.alializadeh.orbit.person.AddPersonKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.person.add.AddPersonRoute
import dev.alializadeh.orbit.person.detail.PersonDetailRoute
import dev.alializadeh.orbit.person.list.PeopleRoute
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.settings.SettingsRoute
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import dev.alializadeh.orbit.upcoming.UpcomingKey
import dev.alializadeh.orbit.upcoming.UpcomingScreen

/** The app shell: four bottom tabs over a single [NavDisplay]. */
@Composable
fun OrbitApp(navigator: OrbitNavigator = rememberOrbitNavigator()) {
    val entries = navigator.rememberEntries(
        entryProvider {
            entry<UpcomingKey> {
                UpcomingScreen(onNewPerson = { navigator.navigate(AddPersonKey) })
            }
            entry<PeopleKey> {
                PeopleRoute(
                    onPersonClick = { id -> navigator.navigate(PersonDetailKey(id)) },
                    onAddPerson = { navigator.navigate(AddPersonKey) },
                )
            }
            entry<AddPersonKey> {
                AddPersonRoute(onBack = navigator::goBack, onSaved = navigator::goBack)
            }
            entry<PersonDetailKey> { key ->
                PersonDetailRoute(key = key, onBack = navigator::goBack)
            }
            entry<OrbitViewKey> { OrbitViewScreen() }
            entry<SettingsKey> { SettingsRoute() }
        },
    )

    Scaffold(
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
