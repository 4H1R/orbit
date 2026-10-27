package dev.alializadeh.orbit.ui.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.alializadeh.orbit.R

// Every still-experimental Material 3 Expressive API (LoadingIndicator, MaterialShapes...) is used
// only through this package, so churn in the material3 1.5 betas stays in one place.

/** The Expressive loading indicator, announced as "Loading". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrbitLoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.common_loading)
    LoadingIndicator(modifier = modifier.semantics { contentDescription = description })
}
