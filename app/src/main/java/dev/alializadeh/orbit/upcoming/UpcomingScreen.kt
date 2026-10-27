package dev.alializadeh.orbit.upcoming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.list.NewPersonFab
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlinx.serialization.Serializable

/** The Upcoming tab's root, which is also Home. */
@Serializable
data object UpcomingKey : NavKey

/**
 * Upcoming (Home). A stub until slice 3 adds Today and Coming up; the New person FAB already
 * works.
 */
@Composable
fun UpcomingScreen(onNewPerson: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = stringResource(R.string.nav_tab_upcoming)) },
        snackbarHost = { OrbitSnackbarHost() },
        // Slice 3 gives Upcoming a list, whose scrolling then shrinks the FAB.
        floatingActionButton = { NewPersonFab(onClick = onNewPerson) },
    ) { innerPadding ->
        // Empty until the slice that builds this screen.
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}
