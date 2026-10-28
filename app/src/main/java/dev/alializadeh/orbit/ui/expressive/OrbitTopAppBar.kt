package dev.alializadeh.orbit.ui.expressive

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.alializadeh.orbit.R

/**
 * Orbit's top app bar (still `ExperimentalMaterial3Api`). With [onBack] it shows a back arrow,
 * which mirrors in RTL; [navigationIcon] replaces it (a close button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitTopAppBar(
    title: String?,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val defaults = TopAppBarDefaults.topAppBarColors()
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            titleContentColor = contentColor.takeOrElse { defaults.titleContentColor },
            navigationIconContentColor = contentColor.takeOrElse { defaults.navigationIconContentColor },
            actionIconContentColor = contentColor.takeOrElse { defaults.actionIconContentColor },
        ),
        title = { if (title != null) Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }
        },
        actions = actions,
    )
}
