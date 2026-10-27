package dev.alializadeh.orbit.upcoming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewPerson,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.common_new_person)) },
            )
        },
    ) { innerPadding ->
        // Empty until the slice that builds this screen.
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}
