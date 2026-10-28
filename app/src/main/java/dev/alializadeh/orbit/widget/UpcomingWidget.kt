package dev.alializadeh.orbit.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.LunarHijriAdjustment
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.format.OrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.occasion.OccasionRepository
import dev.alializadeh.orbit.occasion.occasionName
import dev.alializadeh.orbit.person.dates.dateTitle
import dev.alializadeh.orbit.person.dates.todayTitle
import dev.alializadeh.orbit.person.dates.yearsCountText
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.reminders.AndroidReminderNotifier
import dev.alializadeh.orbit.reminders.AvatarBitmaps
import dev.alializadeh.orbit.reminders.NotificationDetail
import dev.alializadeh.orbit.reminders.NotificationEntry
import dev.alializadeh.orbit.reminders.NotificationIntents
import dev.alializadeh.orbit.reminders.NotificationRenderingPolicy
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import dev.alializadeh.orbit.ui.theme.orbitColorScheme
import dev.alializadeh.orbit.upcoming.UpcomingDate
import dev.alializadeh.orbit.upcoming.UpcomingEntry
import dev.alializadeh.orbit.upcoming.UpcomingFeed
import dev.alializadeh.orbit.upcoming.UpcomingOccasion
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** What the widget needs from the app's graph; a widget receiver isn't an `@AndroidEntryPoint`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpcomingWidgetEntryPoint {
    fun upcoming(): UpcomingRepository
    fun occasions(): OccasionRepository
    fun preferences(): PreferencesRepository
    fun rendering(): NotificationRenderingPolicy
    fun photos(): PhotoStore
    fun clock(): Clock
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

/** One row as drawn: everything already in words, so the composition only lays it out. */
private data class WidgetRow(val title: String, val whenText: String, val avatar: Bitmap?, val open: Intent)

/** The widget's words and taps, worked out before anything is drawn. */
private data class WidgetUi(
    val header: String,
    val headerOpen: Intent,
    val rows: List<WidgetRow>,
    /** "3 people are due" (taps open Upcoming at Due), or the discreet counts, or "All caught up". */
    val footer: String?,
    val footerOpen: Intent,
    /** Orbit's language is Persian: the widget mirrors itself. */
    val rtl: Boolean,
)

/**
 * The Upcoming home-screen widget (PRD v1.1): Today's Occasions and dates, then Coming up, then how
 * many are Due. Discreet like notifications while App lock hides details. Every `reconcile()`
 * redraws it ([HomeWidgets]); its words follow Orbit's language, not the launcher's.
 */
class UpcomingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, UpcomingWidgetEntryPoint::class.java)
        val preferences = graph.preferences().preferences.first()
        LunarHijriAdjustment.set(preferences.lunarAdjustmentDays)
        val localized = AndroidReminderNotifier.localizedContext(context)
        val formats = OrbitFormats(
            language = OrbitLanguage.of(localized.resources.configuration.locales[0]),
            useLatinDigits = preferences.useLatinDigits,
            primaryCalendar = preferences.primaryCalendar,
        )
        val today = LocalDate.now(graph.clock())
        val feed = UpcomingFeed.of(graph.upcoming().observePeople().first(), today, graph.occasions().observeOccasions().first())
        val discreet = graph.rendering().current().detail == NotificationDetail.Discreet
        val model = UpcomingWidgetModel.of(feed, today, discreet)
        val ui = widgetUi(localized, formats, model, graph.photos())
        val appearance = OrbitAppearance(themeMode = preferences.theme, colourSource = preferences.colourSource, customSeed = preferences.customSeedArgb?.let(::Color))
        val colors = ColorProviders(
            light = orbitColorScheme(context, appearance, darkTheme = false),
            dark = orbitColorScheme(context, appearance, darkTheme = true),
        )
        provideContent {
            GlanceTheme(colors = colors) { Content(ui) }
        }
    }

    private fun widgetUi(context: Context, formats: OrbitFormats, model: UpcomingWidgetModel, photos: PhotoStore): WidgetUi {
        val resources = context.resources
        val intents = NotificationIntents(context)
        val avatars = AvatarBitmaps(context)
        val numbers = formats.numbers
        val separator = resources.getString(R.string.separator)
        val rows = model.rows.map { entry ->
            val whenText = RelativeDays.ahead(entry.date, model.today)?.text(resources, numbers)
                ?: formats.dates.formatWithWeekday(entry.date, formats.primaryCalendar, model.today)
            when (entry) {
                is UpcomingDate -> {
                    val name = entry.person.names.displayName(formats.language)
                    val date = entry.importantDate
                    val today = entry.daysAway == 0
                    WidgetRow(
                        title = if (today) todayTitle(resources, name, date.type, date.label) else dateTitle(resources, name, date.type, date.label),
                        // "Today is Sara's birthday" says when already: the years count instead.
                        whenText = if (today) yearsCountText(resources, numbers, date.type, entry.occurrence.yearsCount).orEmpty() else whenText,
                        avatar = avatars.of(name, entry.person.photoFileName?.let(photos::file)),
                        open = intents.openIntent(NotificationEntry.PersonDetail(entry.person.id)),
                    )
                }
                is UpcomingOccasion -> WidgetRow(
                    title = resources.occasionName(entry.occasion.occasion),
                    whenText = listOf(
                        whenText,
                        resources.getQuantityString(R.plurals.occasion_people, entry.people.size, numbers.format(entry.people.size)),
                    ).joinToString(separator),
                    avatar = null,
                    open = intents.openIntent(NotificationEntry.Occasion(entry.occasion.id)),
                )
            }
        }
        val footer = when {
            model.isEmpty -> resources.getString(R.string.upcoming_all_caught_up)
            model.discreet -> discreetCounts(resources, formats, model).joinToString(separator)
            model.dueCount > 0 -> resources.getQuantityString(R.plurals.digest_body_several, model.dueCount, numbers.format(model.dueCount))
            else -> null
        }
        return WidgetUi(
            header = formats.dates.formatWithWeekday(model.today, formats.primaryCalendar, model.today),
            headerOpen = intents.openIntent(NotificationEntry.Upcoming),
            rows = rows,
            footer = footer,
            footerOpen = intents.openIntent(if (model.dueCount > 0 && !model.discreet) NotificationEntry.UpcomingDue else NotificationEntry.Upcoming),
            rtl = formats.language == OrbitLanguage.Persian,
        )
    }

    private fun discreetCounts(resources: Resources, formats: OrbitFormats, model: UpcomingWidgetModel): List<String> {
        val numbers = formats.numbers
        return listOfNotNull(
            model.todayCount.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.widget_discreet_today, it, numbers.format(it)) },
            model.comingUpCount.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.widget_discreet_coming_up, it, numbers.format(it)) },
            model.dueCount.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.widget_discreet_due, it, numbers.format(it)) },
        )
    }
}

@Composable
private fun Content(ui: WidgetUi) {
    val colors = GlanceTheme.colors
    // The launcher lays the widget out in the phone's language; Orbit's may differ, so the widget
    // mirrors itself for Persian (and aligns its text) rather than trusting the layout direction.
    val align = if (ui.rtl) TextAlign.Right else TextAlign.Left
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(colors.widgetBackground)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (ui.rtl) Alignment.End else Alignment.Start,
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(ui.headerOpen)).padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            val mark: @Composable () -> Unit = {
                Image(
                    provider = ImageProvider(R.drawable.ic_notification),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.primary),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
            val header: @Composable () -> Unit = {
                Text(ui.header, style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = align), maxLines = 1)
            }
            if (ui.rtl) {
                header()
                Spacer(GlanceModifier.width(8.dp))
                mark()
            } else {
                mark()
                Spacer(GlanceModifier.width(8.dp))
                header()
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (ui.rows.isNotEmpty()) {
            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(ui.rows) { row -> RowItem(row, ui.rtl) }
            }
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        ui.footer?.let { footer ->
            Text(
                footer,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp, textAlign = align),
                maxLines = 2,
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(ui.footerOpen)).padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RowItem(row: WidgetRow, rtl: Boolean) {
    val colors = GlanceTheme.colors
    val align = if (rtl) TextAlign.Right else TextAlign.Left
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .cornerRadius(16.dp)
            .background(colors.secondaryContainer)
            .clickable(actionStartActivity(row.open))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val badge: @Composable () -> Unit = {
            if (row.avatar != null) {
                Image(provider = ImageProvider(row.avatar), contentDescription = null, modifier = GlanceModifier.size(32.dp).cornerRadius(16.dp))
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier.size(32.dp).cornerRadius(16.dp).background(colors.tertiaryContainer),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_occasion),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.onTertiaryContainer),
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }
        }
        if (!rtl) {
            badge()
            Spacer(GlanceModifier.width(10.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                row.title,
                style = TextStyle(color = colors.onSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = align),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
            )
            if (row.whenText.isNotEmpty()) {
                Text(
                    row.whenText,
                    style = TextStyle(color = colors.onSecondaryContainer, fontSize = 12.sp, textAlign = align),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }
        }
        if (rtl) {
            Spacer(GlanceModifier.width(10.dp))
            badge()
        }
    }
}
