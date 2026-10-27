package dev.alializadeh.orbit.ui.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** Orbit's MaterialShapes (still experimental): one place to swap them if the API moves. */
object OrbitShapes {

    /** A Person's photo or avatar on Person detail. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun cookie(): Shape = MaterialShapes.Cookie9Sided.toShape()

    /** The photo slot on the Add/Edit form. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun flower(): Shape = MaterialShapes.Flower.toShape()
}
