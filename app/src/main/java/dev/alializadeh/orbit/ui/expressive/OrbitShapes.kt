package dev.alializadeh.orbit.ui.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * Orbit's MaterialShapes (still experimental): one place to swap them if the API moves.
 *
 * The shape rule, everywhere in Orbit:
 * - Buttons, the search field, avatars in rows and icon containers: fully round.
 * - Chips and small badges: `shapes.small` (8). Inline panels: `shapes.medium` (12).
 * - List groups: the segmented list shapes (`large` outer corners). Text blocks: `shapes.large` (16).
 * - Cards, sheets and dialogs: `shapes.extraLarge` (28).
 * - Expressive shapes carry meaning and are never decoration: [cookie] is a Person's face where it
 *   is the subject of the page (Person detail, the form's photo, Today's hero), [dateBadge] is an
 *   Important date or an Occasion.
 */
object OrbitShapes {

    /** A Person's photo or avatar where the Person is the subject: Person detail, the form, Today's hero. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun cookie(): Shape = MaterialShapes.Cookie9Sided.toShape()

    /** Important dates and Occasions: the day on Coming up rows, the type icon on Person detail. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun dateBadge(): Shape = MaterialShapes.Cookie4Sided.toShape()
}
