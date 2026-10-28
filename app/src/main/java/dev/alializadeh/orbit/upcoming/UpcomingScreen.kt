package dev.alializadeh.orbit.upcoming

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.backup.BackupNudgeCard
import dev.alializadeh.orbit.backup.BackupNudgeUi
import dev.alializadeh.orbit.backup.BackupNudgeViewModel
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.OrbitDateFormatter
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.interaction.KeepInTouchSheets
import dev.alializadeh.orbit.interaction.KeepInTouchViewModel
import dev.alializadeh.orbit.interaction.lastTalkedText
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.dates.DateBadge
import dev.alializadeh.orbit.person.dates.dateTitle
import dev.alializadeh.orbit.person.dates.giftIdeasLine
import dev.alializadeh.orbit.person.dates.todayTitle
import dev.alializadeh.orbit.person.dates.yearsCountText
import dev.alializadeh.orbit.person.list.NewPersonFab
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.reminders.CriticalReminderProblem
import dev.alializadeh.orbit.lock.AppLockPausedCard
import dev.alializadeh.orbit.lock.AppLockWarningViewModel
import dev.alializadeh.orbit.lock.openSecuritySettings
import dev.alializadeh.orbit.reminders.ReminderWarningCard
import dev.alializadeh.orbit.reminders.ReminderWarningViewModel
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** The Upcoming tab's root, which is also Home. */
@Serializable
data object UpcomingKey : NavKey

/**
 * Upcoming's sections, top to bottom. Empty ones are hidden; when all are, an empty state shows.
 * The screen draws them in this order, so a later slice only fills its own.
 */
enum class UpcomingSection {
    /** App lock paused, then a critical Reminder health problem. */
    Warnings,

    /** The Backup nudge card. */
    BackupNudge,
    Today,
    Due,
    ComingUp,
}

@Composable
fun UpcomingRoute(
    onNewPerson: () -> Unit,
    onPersonClick: (personId: Long) -> Unit,
    onBackUpNow: () -> Unit,
    onFixReminders: () -> Unit = {},
    /** Opens the Wishes sheet for a date today. */
    onSendWishes: (personId: Long, dateId: Long) -> Unit = { _, _ -> },
    /** A notification asked for Upcoming at Due: scroll there once loaded, then [onScrolledToDue]. */
    scrollToDue: Boolean = false,
    onScrolledToDue: () -> Unit = {},
    viewModel: UpcomingViewModel = hiltViewModel(),
    keepInTouch: KeepInTouchViewModel = hiltViewModel(),
    backupNudge: BackupNudgeViewModel = hiltViewModel(),
    reminderWarning: ReminderWarningViewModel = hiltViewModel(),
    appLockWarning: AppLockWarningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nudge by backupNudge.nudge.collectAsStateWithLifecycle()
    val warning by reminderWarning.warning.collectAsStateWithLifecycle()
    val appLockPaused by appLockWarning.paused.collectAsStateWithLifecycle()
    // Back from the background: it may be another day.
    LifecycleResumeEffect(viewModel) {
        viewModel.checkDate()
        backupNudge.checkDate()
        reminderWarning.refresh()
        appLockWarning.refresh()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val snackbars = LocalOrbitSnackbars.current
    val noApp = stringResource(R.string.contact_no_app)
    KeepInTouchSheets(keepInTouch)
    UpcomingScreen(
        uiState = uiState,
        onNewPerson = onNewPerson,
        onPersonClick = onPersonClick,
        onContactAction = { action ->
            try {
                context.startActivity(Intent(action.intentAction, action.uri.toUri()))
            } catch (_: ActivityNotFoundException) {
                snackbars?.show(noApp)
            }
        },
        onLogInteraction = keepInTouch::logInteraction,
        onSnooze = keepInTouch::openSnooze,
        onShowAllDue = viewModel::showAllDue,
        onSendWishes = onSendWishes,
        backupNudge = nudge,
        onBackUpNow = onBackUpNow,
        onNudgeNotNow = backupNudge::notNow,
        reminderWarning = warning,
        onFixReminders = onFixReminders,
        appLockPaused = appLockPaused,
        onSetScreenLock = { openSecuritySettings(context) },
        scrollToDue = scrollToDue,
        onScrolledToDue = onScrolledToDue,
    )
}

/** Upcoming (Home): [UpcomingSection]s in order, or an empty state. */
@Composable
fun UpcomingScreen(
    uiState: UpcomingUiState,
    onNewPerson: () -> Unit,
    onPersonClick: (personId: Long) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (personId: Long) -> Unit,
    onSnooze: (personId: Long) -> Unit,
    onShowAllDue: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSendWishes: (personId: Long, dateId: Long) -> Unit = { _, _ -> },
    /** Slice 5's Backup nudge, or null when it isn't shown. */
    backupNudge: BackupNudgeUi? = null,
    onBackUpNow: () -> Unit = {},
    onNudgeNotNow: () -> Unit = {},
    /** Slice 6's critical Reminder health problem, or null. */
    reminderWarning: CriticalReminderProblem? = null,
    onFixReminders: () -> Unit = {},
    /** App lock is on but the phone has no screen lock: its warning comes first. */
    appLockPaused: Boolean = false,
    onSetScreenLock: () -> Unit = {},
    scrollToDue: Boolean = false,
    onScrolledToDue: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val pausedCard: (@Composable (Modifier) -> Unit)? = if (appLockPaused) {
        { cardModifier -> AppLockPausedCard(onSetScreenLock = onSetScreenLock, modifier = cardModifier) }
    } else {
        null
    }
    val warningCard: (@Composable (Modifier) -> Unit)? = reminderWarning?.let { problem ->
        { cardModifier -> ReminderWarningCard(problem, onFix = onFixReminders, modifier = cardModifier) }
    }
    val nudgeCard: (@Composable (Modifier) -> Unit)? = backupNudge?.let { nudge ->
        { cardModifier -> BackupNudgeCard(nudge, onBackUpNow = onBackUpNow, onNotNow = onNudgeNotNow, modifier = cardModifier) }
    }
    // Above an empty state: the warnings, then the nudge.
    val topCards: (@Composable (Modifier) -> Unit)? = if (pausedCard == null && warningCard == null && nudgeCard == null) {
        null
    } else {
        { cardModifier ->
            Column(cardModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pausedCard?.invoke(Modifier)
                warningCard?.invoke(Modifier)
                nudgeCard?.invoke(Modifier)
            }
        }
    }
    LaunchedEffect(scrollToDue, uiState is UpcomingUiState.Loaded) {
        val loaded = uiState as? UpcomingUiState.Loaded ?: return@LaunchedEffect
        if (!scrollToDue) return@LaunchedEffect
        if (loaded.feed.due.isNotEmpty()) {
            listState.scrollToItem(dueTitleIndex(loaded, pausedCard != null, warningCard != null, nudgeCard != null))
        }
        onScrolledToDue()
    }
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = stringResource(R.string.nav_tab_upcoming)) },
        snackbarHost = { OrbitSnackbarHost() },
        floatingActionButton = { NewPersonFab(onClick = onNewPerson, listState = listState) },
    ) { innerPadding ->
        when {
            uiState !is UpcomingUiState.Loaded -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OrbitLoadingIndicator()
            }

            !uiState.feed.hasPeople -> EmptyState(
                title = stringResource(R.string.empty_no_people_title),
                body = stringResource(R.string.empty_no_people_body),
                modifier = Modifier.padding(innerPadding),
                card = topCards,
            )

            uiState.isEmpty -> EmptyState(
                title = stringResource(R.string.upcoming_all_caught_up),
                body = uiState.feed.next?.let { nextLine(it, uiState.today) },
                modifier = Modifier.padding(innerPadding),
                card = topCards,
            )

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding(),
                        // Room for the FAB over the last row.
                        bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    ),
                ) {
                    UpcomingSection.entries.forEach { section ->
                        when (section) {
                            UpcomingSection.Warnings -> {
                                if (pausedCard != null) {
                                    item(key = "app_lock_paused") { pausedCard(Modifier.padding(top = 8.dp, bottom = 8.dp)) }
                                }
                                if (warningCard != null) {
                                    item(key = "reminder_warning") { warningCard(Modifier.padding(top = 8.dp, bottom = 8.dp)) }
                                }
                            }
                            UpcomingSection.BackupNudge -> if (nudgeCard != null) {
                                item(key = "backup_nudge") { nudgeCard(Modifier.padding(top = 8.dp, bottom = 8.dp)) }
                            }
                            UpcomingSection.Today ->
                                todaySection(uiState, onPersonClick, onContactAction, onLogInteraction, onSendWishes)
                            UpcomingSection.Due ->
                                dueSection(uiState, onPersonClick, onContactAction, onLogInteraction, onSnooze, onShowAllDue)
                            UpcomingSection.ComingUp -> comingUpSection(uiState, onPersonClick)
                        }
                    }
                }
            }
        }
    }
}

/** Where Due's title sits in the list: after the cards and Today (title, hero, other rows). */
private fun dueTitleIndex(uiState: UpcomingUiState.Loaded, vararg cards: Boolean): Int {
    var index = cards.count { it }
    val today = uiState.feed.today.size
    if (today > 0) index += 1 + today
    return index
}

/** The nearest date today as the hero card, then any others today as rows. */
private fun LazyListScope.todaySection(
    uiState: UpcomingUiState.Loaded,
    onPersonClick: (Long) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (Long) -> Unit,
    onSendWishes: (personId: Long, dateId: Long) -> Unit,
) {
    val dates = uiState.feed.today
    if (dates.isEmpty()) return
    item(key = "today_title") { SectionTitle(stringResource(R.string.upcoming_section_today)) }
    val hero = dates.first()
    item(key = "today_hero") {
        HeroCard(
            date = hero,
            photo = uiState.photos[hero.person.id],
            today = uiState.today,
            onClick = { onPersonClick(hero.person.id) },
            onContactAction = onContactAction,
            onLogInteraction = { onLogInteraction(hero.person.id) },
            onSendWishes = { onSendWishes(hero.person.id, hero.importantDate.id) },
        )
    }
    val rest = dates.drop(1)
    itemsIndexed(rest, key = { _, it -> "today_${it.importantDate.id}" }) { index, date ->
        TodayRow(
            date = date,
            photo = uiState.photos[date.person.id],
            index = index,
            count = rest.size,
            onClick = { onPersonClick(date.person.id) },
            onContactAction = onContactAction,
            onLogInteraction = { onLogInteraction(date.person.id) },
            onSendWishes = { onSendWishes(date.person.id, date.importantDate.id) },
            modifier = if (index == 0) Modifier.padding(top = 8.dp) else Modifier,
        )
    }
}

/**
 * Everyone Due: innermost Ring first, then longest since the last Interaction. Five show, then
 * "Show all (n)" expands the rest in place, with "Show fewer" at the end.
 */
private fun LazyListScope.dueSection(
    uiState: UpcomingUiState.Loaded,
    onPersonClick: (Long) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onShowAllDue: (Boolean) -> Unit,
) {
    val due = uiState.feed.due
    if (due.isEmpty()) return
    item(key = "due_title") { SectionTitle(stringResource(R.string.upcoming_section_due)) }
    val shown = uiState.dueShown
    itemsIndexed(shown, key = { _, it -> "due_${it.person.id}" }) { index, row ->
        DueRow(
            row = row,
            photo = uiState.photos[row.person.id],
            today = uiState.today,
            index = index,
            count = shown.size,
            onClick = { onPersonClick(row.person.id) },
            onContactAction = onContactAction,
            onLogInteraction = { onLogInteraction(row.person.id) },
            onSnooze = { onSnooze(row.person.id) },
        )
    }
    if (uiState.dueHasMore) {
        item(key = "due_more") {
            val numbers = LocalOrbitFormats.current.numbers
            TextButton(onClick = { onShowAllDue(!uiState.showAllDue) }) {
                Text(
                    if (uiState.showAllDue) {
                        stringResource(R.string.upcoming_show_fewer)
                    } else {
                        stringResource(R.string.upcoming_show_all, numbers.format(due.size))
                    },
                )
            }
        }
    }
}

/** Important dates in the next 30 days. Rows open Person detail; no inline actions. */
private fun LazyListScope.comingUpSection(uiState: UpcomingUiState.Loaded, onPersonClick: (Long) -> Unit) {
    val dates = uiState.feed.comingUp
    if (dates.isEmpty()) return
    item(key = "coming_up_title") { SectionTitle(stringResource(R.string.upcoming_section_coming_up)) }
    itemsIndexed(dates, key = { _, it -> "coming_up_${it.importantDate.id}" }) { index, date ->
        ComingUpRow(
            date = date,
            today = uiState.today,
            index = index,
            count = dates.size,
            onClick = { onPersonClick(date.person.id) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    OrbitSectionTitle(text, horizontalPadding = 4.dp, modifier = Modifier.padding(top = 8.dp))
}

/**
 * The nearest date today: the photo, "Today is Sara's birthday · turns 35", the date, the open
 * Gift ideas, then the primary contact action, Send wishes and Log interaction.
 */
@Composable
private fun HeroCard(
    date: UpcomingDate,
    photo: File?,
    today: LocalDate,
    onClick: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onSendWishes: () -> Unit,
) {
    val formats = LocalOrbitFormats.current
    val separator = stringResource(R.string.separator)
    val name = date.person.names.displayName(formats.language)
    val type = date.importantDate.type
    val label = date.importantDate.label
    val title = listOfNotNull(
        todayTitle(name, type, label),
        yearsCountText(type, date.occurrence.yearsCount, inline = true),
    ).joinToString(separator)
    val dateLine = formats.dates
        .occurrenceParts(date.date, date.importantDate.date.calendar, formats.primaryCalendar, today, withWeekday = true)
        .joinToString(separator)
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(36.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Decoration: a sunny shape over the corner.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 46.dp, y = (-46).dp)
                    .size(170.dp)
                    .clip(OrbitShapes.sunny())
                    .background(colors.tertiary.copy(alpha = 0.16f)),
            )
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PersonAvatar(
                    name = name,
                    photo = photo,
                    size = 72.dp,
                    shape = OrbitShapes.cookie(),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(dateLine, style = MaterialTheme.typography.bodyMedium)
                giftIdeasLine(date.person.openGiftIdeas)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val action = date.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (action != null) {
                        Button(
                            onClick = { onContactAction(action) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.onTertiaryContainer,
                                contentColor = colors.tertiaryContainer,
                            ),
                        ) {
                            Icon(action.kind.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(action.kind.label(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = onSendWishes,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onTertiaryContainer),
                        border = BorderStroke(1.dp, colors.onTertiaryContainer),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.wishes_send), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = onLogInteraction,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onTertiaryContainer),
                        border = BorderStroke(1.dp, colors.onTertiaryContainer),
                    ) {
                        Icon(OrbitIcons.LogInteraction, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.interaction_log), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Another date today: whose it is, the years count and Gift ideas, the contact action, Send wishes
 * and Log interaction.
 */
@Composable
private fun TodayRow(
    date: UpcomingDate,
    photo: File?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onSendWishes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val name = date.person.names.displayName(formats.language)
    val supporting = detailsLine(date)
    val action = date.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = photo) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Row {
                action?.let {
                    IconButton(onClick = { onContactAction(it) }) {
                        Icon(it.kind.icon, contentDescription = it.kind.label())
                    }
                }
                IconButton(onClick = onSendWishes) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.wishes_send))
                }
                IconButton(onClick = onLogInteraction) {
                    Icon(OrbitIcons.LogInteraction, contentDescription = stringResource(R.string.interaction_log))
                }
            }
        },
        modifier = modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(
            dateTitle(name, date.importantDate.type, date.importantDate.label),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A Due Person: who, "Close · last talked 34 days ago", and their contact action, Log interaction
 * and Snooze. Tapping opens Person detail.
 */
@Composable
private fun DueRow(
    row: DuePerson,
    photo: File?,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onSnooze: () -> Unit,
) {
    val formats = LocalOrbitFormats.current
    val name = row.person.names.displayName(formats.language)
    val action = row.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    val supporting = listOfNotNull(
        row.person.ringName?.let(::bidiIsolate),
        lastTalkedText(row.state.lastInteraction, today, inline = row.person.ringName != null),
    ).joinToString(stringResource(R.string.separator))
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = photo) },
        supportingContent = { Text(supporting, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row {
                action?.let {
                    IconButton(onClick = { onContactAction(it) }) {
                        Icon(it.kind.icon, contentDescription = it.kind.label())
                    }
                }
                IconButton(onClick = onLogInteraction) {
                    Icon(OrbitIcons.LogInteraction, contentDescription = stringResource(R.string.interaction_log))
                }
                IconButton(onClick = onSnooze) {
                    Icon(OrbitIcons.Snooze, contentDescription = stringResource(R.string.snooze_action))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A date in the next 30 days: the day badge, whose date it is, when (relative words up to a week
 * ahead, then the date with its weekday) with the date's own Calendar when it differs, then the
 * years count and "2 gift ideas".
 */
@Composable
private fun ComingUpRow(date: UpcomingDate, today: LocalDate, index: Int, count: Int, onClick: () -> Unit) {
    val formats = LocalOrbitFormats.current
    val separator = stringResource(R.string.separator)
    val name = date.person.names.displayName(formats.language)
    val ownCalendar = date.importantDate.date.calendar
    val inPrimary = date.date.inCalendar(formats.primaryCalendar)
    val whenText = RelativeDays.ahead(date.date, today)?.text()
        ?: formats.dates.formatWithWeekday(date.date, formats.primaryCalendar, today)
    val second = OrbitDateFormatter.secondCalendar(ownCalendar, formats.primaryCalendar)
        ?.let { formats.dates.format(date.date, it, today) }
    val whenLine = listOfNotNull(whenText, second).joinToString(separator)
    val details = detailsLine(date)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            DateBadge(
                day = formats.numbers.format(inPrimary.day),
                month = formats.dates.monthName(formats.primaryCalendar, inPrimary.month),
            )
        },
        supportingContent = {
            Column {
                Text(whenLine)
                if (details != null) Text(details)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(dateTitle(name, date.importantDate.type, date.importantDate.label), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "Turns 32 · 2 gift ideas": the years count and the open Gift ideas, or null with neither. */
@Composable
private fun detailsLine(date: UpcomingDate): String? {
    val ideas = date.person.openGiftIdeas.size
    val parts = listOfNotNull(
        yearsCountText(date.importantDate.type, date.occurrence.yearsCount),
        if (ideas > 0) {
            pluralStringResource(R.plurals.upcoming_gift_ideas, ideas, LocalOrbitFormats.current.numbers.format(ideas))
        } else {
            null
        },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.separator))
}

/** "Next: Sara's birthday · 12 Jan". */
@Composable
private fun nextLine(date: UpcomingDate, today: LocalDate): String {
    val formats = LocalOrbitFormats.current
    val name = date.person.names.displayName(formats.language)
    val what = dateTitle(name, date.importantDate.type, date.importantDate.label)
    val whenText = formats.dates.format(date.date, formats.primaryCalendar, today)
    return stringResource(R.string.upcoming_next, what + stringResource(R.string.separator) + whenText)
}

/** An empty state, under the Backup nudge [card] when it shows. */
@Composable
private fun EmptyState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    card: (@Composable (Modifier) -> Unit)? = null,
) {
    if (card != null) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            card(Modifier.widthIn(max = MaxContentWidth).padding(16.dp))
            EmptyState(title, body, Modifier.weight(1f))
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
