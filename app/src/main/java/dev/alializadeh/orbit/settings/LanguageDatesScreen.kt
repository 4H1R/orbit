package dev.alializadeh.orbit.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.LunarHijriAdjustment
import dev.alializadeh.orbit.calendar.LunarHijriRules
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.ContentRow
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.components.SwitchRow
import dev.alializadeh.orbit.ui.datepicker.calendarName
import kotlinx.serialization.Serializable

@Serializable
data object LanguageDatesSettingsKey : NavKey

@Composable
fun LanguageDatesRoute(onBack: () -> Unit, viewModel: LanguageDatesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LanguageDatesScreen(
        uiState = uiState,
        language = LocalOrbitFormats.current.language,
        onBack = onBack,
        onLanguageSelect = viewModel::selectLanguage,
        onPrimaryCalendarSelect = viewModel::selectPrimaryCalendar,
        onUseLatinDigitsChange = viewModel::setUseLatinDigits,
        onLunarAdjustmentSelect = viewModel::setLunarAdjustment,
    )
}

/**
 * Settings → Language & dates: Language (each shown in its own script), Primary calendar (each
 * option previews today's date), "Use Latin digits" in the Persian UI only, and the Lunar date
 * adjustment (each option previews today's Lunar Hijri date with it).
 */
@Composable
fun LanguageDatesScreen(
    uiState: LanguageDatesUiState,
    language: OrbitLanguage,
    onBack: () -> Unit,
    onLanguageSelect: (OrbitLanguage) -> Unit,
    onPrimaryCalendarSelect: (CalendarSystem) -> Unit,
    onUseLatinDigitsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLunarAdjustmentSelect: (Int) -> Unit = {},
) {
    OrbitSettingsScaffold(
        title = stringResource(R.string.settings_language_dates_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (uiState !is LanguageDatesUiState.Loaded) return@OrbitSettingsScaffold
        val dates = LocalOrbitFormats.current.dates
        OrbitListGroup(title = stringResource(R.string.language_dates_language)) {
            ContentRow(index = 0, count = 1) {
                OrbitConnectedChoice(
                    options = OrbitLanguage.entries,
                    selected = language,
                    onSelect = onLanguageSelect,
                    label = { languageName(it) },
                )
            }
        }
        OrbitListGroup(title = stringResource(R.string.language_dates_primary_calendar)) {
            CalendarSystem.Primaries.forEachIndexed { index, calendar ->
                ChoiceRow(
                    title = calendarName(calendar),
                    summary = dates.format(uiState.today.inCalendar(calendar)),
                    selected = uiState.primaryCalendar == calendar,
                    index = index,
                    count = CalendarSystem.Primaries.size,
                    onClick = { onPrimaryCalendarSelect(calendar) },
                )
            }
        }
        if (language == OrbitLanguage.Persian) {
            OrbitListGroup(title = null, modifier = Modifier.padding(top = 24.dp)) {
                SwitchRow(
                    title = stringResource(R.string.language_dates_latin_digits),
                    summary = stringResource(R.string.language_dates_latin_digits_summary),
                    checked = uiState.useLatinDigits,
                    index = 0,
                    count = 1,
                    onCheckedChange = onUseLatinDigitsChange,
                )
            }
        }
        val adjustments = (-LunarHijriAdjustment.MAX_DAYS..LunarHijriAdjustment.MAX_DAYS).toList()
        OrbitListGroup(title = stringResource(R.string.language_dates_lunar_title), modifier = Modifier.padding(top = 24.dp)) {
            adjustments.forEachIndexed { index, days ->
                ChoiceRow(
                    title = lunarAdjustmentName(days),
                    summary = dates.format(LunarHijriRules.fromLocalDate(uiState.today, adjustment = days)),
                    selected = uiState.lunarAdjustmentDays == days,
                    index = index,
                    count = adjustments.size,
                    onClick = { onLunarAdjustmentSelect(days) },
                )
            }
        }
        Text(
            text = stringResource(R.string.lunar_adjustment_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** "As calculated", "1 day earlier", "2 days later". */
@Composable
private fun lunarAdjustmentName(days: Int): String {
    val numbers = LocalOrbitFormats.current.numbers
    return when {
        days == 0 -> stringResource(R.string.lunar_adjustment_none)
        days < 0 -> pluralStringResource(R.plurals.lunar_adjustment_earlier, -days, numbers.format(-days))
        else -> pluralStringResource(R.plurals.lunar_adjustment_later, days, numbers.format(days))
    }
}
