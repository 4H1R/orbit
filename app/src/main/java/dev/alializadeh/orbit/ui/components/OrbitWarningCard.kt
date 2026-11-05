package dev.alializadeh.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** What an [OrbitAttentionCard] is about, which picks its colours. */
enum class AttentionTone {
    /** Worth doing, nothing is wrong (the Backup nudge): `secondaryContainer`. */
    Nudge,

    /** Something is wrong (reminders can't reach the user, App lock is paused): `errorContainer`. */
    Problem,
}

/**
 * A card at the top of Upcoming that asks for the user's attention. One anatomy for all of them:
 * the topic's icon, a short [title], what it means ([body]), then the actions at the end: an
 * optional text [dismissLabel] ("Not now") and one filled [actionLabel]. The [tone] decides the
 * colours, so the icon can say what it's about (the Settings page it leads to).
 */
@Composable
fun OrbitAttentionCard(
    icon: ImageVector,
    title: String,
    body: String,
    tone: AttentionTone,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        AttentionTone.Nudge -> colors.secondaryContainer to colors.onSecondaryContainer
        AttentionTone.Problem -> colors.errorContainer to colors.onErrorContainer
    }
    val buttonColors = when (tone) {
        AttentionTone.Nudge -> ButtonDefaults.buttonColors()
        AttentionTone.Problem -> ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(end = 4.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (dismissLabel != null) {
                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = content)) {
                        Text(dismissLabel)
                    }
                }
                Button(onClick = onAction, colors = buttonColors) { Text(actionLabel) }
            }
        }
    }
}

/** A [Problem][AttentionTone.Problem] card: what's wrong, and the one button that fixes it. */
@Composable
fun OrbitWarningCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitAttentionCard(icon, title, body, AttentionTone.Problem, actionLabel, onAction, modifier)
}
