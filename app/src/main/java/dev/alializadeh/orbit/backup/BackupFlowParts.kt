package dev.alializadeh.orbit.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * The parts the Backup, Restore and passphrase screens share, so the whole flow reads as one:
 * each step opens with a [FlowHeader] (what this step is), problems are an [FlowErrorCard] where
 * they happen, and things to know before going on are a [FlowNoticeCard].
 */

/** A step's opening: its icon in a tonal circle, the title (the screen's one headline) and what to do. */
@Composable
internal fun FlowHeader(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Something that went wrong, said specifically, where it happened: `errorContainer`, a warning icon. */
@Composable
internal fun FlowErrorCard(text: String, modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    FlowCard(
        icon = Icons.Filled.Warning,
        container = MaterialTheme.colorScheme.errorContainer,
        contentColour = MaterialTheme.colorScheme.onErrorContainer,
        title = title,
        text = text,
        modifier = modifier,
        content = content,
    )
}

/** Something to know before going on (not a problem): `secondaryContainer`. */
@Composable
internal fun FlowNoticeCard(
    icon: ImageVector,
    title: String?,
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FlowCard(
        icon = icon,
        container = MaterialTheme.colorScheme.secondaryContainer,
        contentColour = MaterialTheme.colorScheme.onSecondaryContainer,
        title = title,
        text = text,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun FlowCard(
    icon: ImageVector,
    container: Color,
    contentColour: Color,
    title: String?,
    text: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = container, contentColor = contentColour, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                content()
            }
        }
    }
}

/**
 * A rule the typed passphrase must meet, as a field's supporting line: a tick in primary once
 * [met], a neutral dot until then.
 */
@Composable
internal fun RequirementLine(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (met) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        } else {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
        Text(text)
    }
}
