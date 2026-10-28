package dev.alializadeh.orbit.occasion

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import dev.alializadeh.orbit.R

@get:StringRes
val BuiltInOccasion.nameRes: Int
    get() = when (this) {
        BuiltInOccasion.Nowruz -> R.string.occasion_name_nowruz
        BuiltInOccasion.Yalda -> R.string.occasion_name_yalda
        BuiltInOccasion.Sepandarmazgan -> R.string.occasion_name_sepandarmazgan
        BuiltInOccasion.TeachersDay -> R.string.occasion_name_teachers_day
        BuiltInOccasion.MothersDay -> R.string.occasion_name_mothers_day
        BuiltInOccasion.FathersDay -> R.string.occasion_name_fathers_day
        BuiltInOccasion.EidAlFitr -> R.string.occasion_name_eid_al_fitr
        BuiltInOccasion.EidAlAdha -> R.string.occasion_name_eid_al_adha
        BuiltInOccasion.EidAlGhadir -> R.string.occasion_name_eid_al_ghadir
        BuiltInOccasion.NewYearsDay -> R.string.occasion_name_new_years_day
        BuiltInOccasion.Christmas -> R.string.occasion_name_christmas
        BuiltInOccasion.ValentinesDay -> R.string.occasion_name_valentines_day
    }

/** The default Wishes on this Occasion (`%1$s` is the name). */
@get:StringRes
val BuiltInOccasion.wishesRes: Int
    get() = when (this) {
        BuiltInOccasion.Nowruz -> R.string.wishes_occasion_nowruz
        BuiltInOccasion.Yalda -> R.string.wishes_occasion_yalda
        BuiltInOccasion.Sepandarmazgan -> R.string.wishes_occasion_sepandarmazgan
        BuiltInOccasion.TeachersDay -> R.string.wishes_occasion_teachers_day
        BuiltInOccasion.MothersDay -> R.string.wishes_occasion_mothers_day
        BuiltInOccasion.FathersDay -> R.string.wishes_occasion_fathers_day
        BuiltInOccasion.EidAlFitr -> R.string.wishes_occasion_eid_al_fitr
        BuiltInOccasion.EidAlAdha -> R.string.wishes_occasion_eid_al_adha
        BuiltInOccasion.EidAlGhadir -> R.string.wishes_occasion_eid_al_ghadir
        BuiltInOccasion.NewYearsDay -> R.string.wishes_occasion_new_years_day
        BuiltInOccasion.Christmas -> R.string.wishes_occasion_christmas
        BuiltInOccasion.ValentinesDay -> R.string.wishes_occasion_valentines_day
    }

/**
 * The Occasion's name: a built-in one's in the UI language, or the user's own label as written.
 * It's user text then: bidi-isolate it in a sentence.
 */
fun Resources.occasionName(occasion: Occasion): String =
    occasion.builtIn?.let { getString(it.nameRes) } ?: occasion.label.orEmpty()

@Composable
@ReadOnlyComposable
fun occasionName(occasion: Occasion): String = LocalResources.current.occasionName(occasion)
