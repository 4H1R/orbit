package dev.alializadeh.orbit.person.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import java.io.File

/**
 * A Person's face where they are the subject of the page: their photo (or initial) in the cookie,
 * the same on Person detail's header and the Add/Edit form's photo slot.
 */
@Composable
internal fun PersonHeroAvatar(name: String, photo: File?, modifier: Modifier = Modifier) {
    PersonAvatar(
        name = name,
        photo = photo,
        size = 120.dp,
        shape = OrbitShapes.cookie(),
        textStyle = MaterialTheme.typography.displayMedium,
        modifier = modifier,
    )
}
