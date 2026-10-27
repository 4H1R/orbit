package dev.alializadeh.orbit.orbitview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlinx.serialization.Serializable

/** The Orbit tab's root: the Orbit view. */
@Serializable
data object OrbitViewKey : NavKey

/** The Orbit view. A stub until slice 9 draws it. */
@Composable
fun OrbitViewScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = stringResource(R.string.nav_tab_orbit)) },
    ) { innerPadding ->
        // Empty until the slice that builds this screen.
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}
