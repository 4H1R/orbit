package dev.alializadeh.orbit.person.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import dev.alializadeh.orbit.ui.theme.isDarkScheme
import java.io.File
import java.text.BreakIterator

/**
 * A Person's photo clipped to [shape], or with no photo an initials avatar coloured from their
 * name. The initial is drawn underneath the photo too, so there's no blank frame while Coil loads.
 */
@Composable
fun PersonAvatar(
    name: String,
    photo: File?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val colours = AvatarColours.of(name, MaterialTheme.colorScheme.isDarkScheme())
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colours.container),
        contentAlignment = Alignment.Center,
    ) {
        val initial = AvatarColours.initial(name)
        if (initial.isEmpty()) {
            // No name yet (a new Person on the form): a photo glyph instead of a letter.
            Icon(OrbitIcons.Photo, contentDescription = null, tint = colours.content, modifier = Modifier.size(size / 3))
        } else {
            Text(text = initial, style = textStyle, color = colours.content)
        }
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The initials avatar's look, derived from the name alone so it never changes between screens. */
data class AvatarColours(val container: Color, val content: Color) {
    companion object {
        private const val HUES = 12

        /** One of [HUES] evenly spaced hues, picked by a stable hash of the name. */
        fun hueIndex(name: String): Int {
            val hash = name.trim().lowercase().fold(7) { h, c -> 31 * h + c.code }
            return Math.floorMod(hash, HUES)
        }

        fun of(name: String, dark: Boolean): AvatarColours {
            val hue = hueIndex(name) * (360f / HUES) + 15f
            return if (dark) {
                AvatarColours(Color.hsl(hue, 0.32f, 0.30f), Color.hsl(hue, 0.60f, 0.88f))
            } else {
                AvatarColours(Color.hsl(hue, 0.55f, 0.86f), Color.hsl(hue, 0.55f, 0.26f))
            }
        }

        /** The first letter (one grapheme) of [name], upper-cased: "S", «س». */
        fun initial(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return ""
            val iterator = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
            return trimmed.substring(0, iterator.next()).uppercase()
        }
    }
}
