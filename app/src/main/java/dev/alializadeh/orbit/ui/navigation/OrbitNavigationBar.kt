package dev.alializadeh.orbit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/**
 * The tabs as a floating pill: the selected tab is a filled pill with its icon and label, the
 * others are icons alone (their label is still read by TalkBack). Centred above the system
 * navigation bar; the space around it is the page's own colour (the shell's container).
 */
@Composable
internal fun OrbitNavigationBar(
    currentTab: TopLevelTab,
    onTabSelected: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(6.dp).selectableGroup(),
            ) {
                TopLevelTab.entries.forEach { tab ->
                    TabPill(tab = tab, selected = tab == currentTab, onClick = { onTabSelected(tab) })
                }
            }
        }
    }
}

@Composable
private fun TabPill(tab: TopLevelTab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val still = LocalStillMotion.current
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHighest,
        animationSpec = if (still) snap() else spring(),
        label = "tab background",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = if (still) snap() else spring(),
        label = "tab content",
    )
    val label = stringResource(tab.label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = 56.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            // The label is read from the text while it shows, from here while it doesn't.
            contentDescription = if (selected) null else label,
            tint = content,
        )
        AnimatedVisibility(
            visible = selected,
            enter = if (still) fadeIn(snap()) else expandHorizontally() + fadeIn(),
            exit = if (still) fadeOut(snap()) else shrinkHorizontally() + fadeOut(),
        ) {
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@get:StringRes
internal val TopLevelTab.label: Int
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
