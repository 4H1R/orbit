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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
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
import dev.alializadeh.orbit.interaction.QuickActionSheet
import dev.alializadeh.orbit.interaction.QuickActionTarget
import dev.alializadeh.orbit.interaction.lastTalkedText
import dev.alializadeh.orbit.lock.AppLockPausedCard
import dev.alializadeh.orbit.lock.AppLockWarningViewModel
import dev.alializadeh.orbit.lock.openSecuritySettings
import dev.alializadeh.orbit.occasion.occasionName
import dev.alializadeh.orbit.occasion.occasionNameInSentence
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.dates.DateBadge
import dev.alializadeh.orbit.person.dates.dateTitle
import dev.alializadeh.orbit.person.dates.giftIdeasLine
import dev.alializadeh.orbit.person.dates.todayTitle
import dev.alializadeh.orbit.person.dates.yearsCountText
import dev.alializadeh.orbit.person.detail.taskWhen
import dev.alializadeh.orbit.person.list.NewPersonFab
import dev.alializadeh.orbit.person.loans.displayName
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.reminders.CriticalReminderProblem
import dev.alializadeh.orbit.reminders.ReminderWarningCard
import dev.alializadeh.orbit.reminders.ReminderWarningViewModel
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitEmptyState
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

    /** Follow-ups and Loans to come back. */
    Tasks,
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
    /** Opens an Occasion with its People. */
    onOccasionClick: (occasionId: Long) -> Unit = {},
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
    val markedDone = stringResource(R.string.follow_up_marked_done)
    val markedReturned = stringResource(R.string.loan_marked_returned)
    val undoLabel = stringResource(R.string.common_undo)
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
        onCloseTask = { task ->
            when (task) {
                is UpcomingFollowUp -> keepInTouch.markFollowUpDone(task.followUp.id) { undo -> snackbars?.show(markedDone, undoLabel, undo) }
                is UpcomingLoan -> keepInTouch.markLoanReturned(task.loan.id) { undo -> snackbars?.show(markedReturned, undoLabel, undo) }
            }
        },
        onSendWishes = onSendWishes,
        onOccasionClick = onOccasionClick,
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
    onOccasionClick: (occasionId: Long) -> Unit = {},
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
    onCloseTask: (UpcomingTask) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // Long-pressing a Person opens their quick actions (as on People and the Orbit view). Saveable:
    // it must outlive App lock, which takes the screen out of composition.
    var quickActionsFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val onPersonLongClick: (Long) -> Unit = { quickActionsFor = it }
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
                icon = OrbitIcons.People,
                title = stringResource(R.string.empty_no_people_title),
                body = stringResource(R.string.empty_no_people_body),
                modifier = Modifier.padding(innerPadding),
                card = topCards,
            )

            uiState.isEmpty -> EmptyState(
                icon = Icons.Filled.Check,
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
                            UpcomingSection.Today -> todaySection(
                                uiState,
                                onPersonClick,
                                onPersonLongClick,
                                onContactAction,
                                onLogInteraction,
                                onSnooze,
                                onSendWishes,
                                onOccasionClick,
                            )
                            UpcomingSection.Tasks -> tasksSection(uiState, onPersonClick, onCloseTask)
                            UpcomingSection.Due -> dueSection(
                                uiState,
                                onPersonClick,
                                onPersonLongClick,
                                onContactAction,
                                onLogInteraction,
                                onSnooze,
                                onShowAllDue,
                            )
                            UpcomingSection.ComingUp -> comingUpSection(
                                uiState,
                                onPersonClick,
                                onPersonLongClick,
                                onContactAction,
                                onLogInteraction,
                                onSnooze,
                                onOccasionClick,
                            )
                        }
                    }
                }
            }
        }
    }
    val target = (uiState as? UpcomingUiState.Loaded)?.let { loaded -> quickActionsFor?.let(loaded::quickActionTarget) }
    if (target != null) {
        QuickActionSheet(
            target = target,
            onContactAction = onContactAction,
            onLogInteraction = onLogInteraction,
            onSnooze = onSnooze,
            onDismiss = { quickActionsFor = null },
        )
    }
}

/** The quick-action sheet's Person: anyone a row on Upcoming shows, with Snooze while they're Due. */
private fun UpcomingUiState.Loaded.quickActionTarget(personId: Long): QuickActionTarget? {
    val person = feed.due.map { it.person }.firstOrNull { it.id == personId }
        ?: feed.today.map { it.person }.firstOrNull { it.id == personId }
        ?: feed.comingUpEntries.filterIsInstance<UpcomingDate>().map { it.person }.firstOrNull { it.id == personId }
        ?: return null
    return QuickActionTarget(
        personId = person.id,
        names = person.names,
        photo = photos[person.id],
        primaryContactMethod = person.primaryContactMethod,
        isDue = isDue(person.id),
    )
}

private fun UpcomingUiState.Loaded.isDue(personId: Long): Boolean = feed.due.any { it.person.id == personId }

/**
 * TalkBack's custom actions for a Person's row: what the long-press sheet offers that the row
 * doesn't show as a button ([contact] and [log] when the row has none, Snooze while [isDue]).
 */
@Composable
private fun hiddenActions(
    contact: ContactAction?,
    onContactAction: ((ContactAction) -> Unit)?,
    onLogInteraction: (() -> Unit)?,
    onSnooze: (() -> Unit)?,
): List<CustomAccessibilityAction> {
    val contactLabel = contact?.kind?.label()
    val log = stringResource(R.string.interaction_log)
    val snooze = stringResource(R.string.snooze_action)
    return listOfNotNull(
        if (contact != null && contactLabel != null && onContactAction != null) {
            CustomAccessibilityAction(contactLabel) { onContactAction(contact); true }
        } else {
            null
        },
        onLogInteraction?.let { CustomAccessibilityAction(log) { it(); true } },
        onSnooze?.let { CustomAccessibilityAction(snooze) { it(); true } },
    )
}

/** Where Due's title sits in the list: after the cards and Today (title, hero, other rows). */
private fun dueTitleIndex(uiState: UpcomingUiState.Loaded, vararg cards: Boolean): Int {
    var index = cards.count { it }
    val today = uiState.feed.today.size + uiState.feed.todayOccasions.size
    if (today > 0) index += 1 + today
    if (uiState.feed.tasks.isNotEmpty()) index += 1 + uiState.feed.tasks.size
    return index
}

/** The nearest date today as the hero card, then any others today as rows. */
private fun LazyListScope.todaySection(
    uiState: UpcomingUiState.Loaded,
    onPersonClick: (Long) -> Unit,
    onPersonLongClick: (Long) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSendWishes: (personId: Long, dateId: Long) -> Unit,
    onOccasionClick: (occasionId: Long) -> Unit,
) {
    val dates = uiState.feed.today
    val occasions = uiState.feed.todayOccasions
    if (dates.isEmpty() && occasions.isEmpty()) return
    item(key = "today_title") { SectionTitle(stringResource(R.string.upcoming_section_today)) }
    itemsIndexed(occasions, key = { _, it -> "today_occasion_${it.occasion.id}" }) { index, occasion ->
        OccasionRow(
            occasion = occasion,
            whenLine = null,
            index = index,
            count = occasions.size,
            onClick = { onOccasionClick(occasion.occasion.id) },
            modifier = if (index == occasions.lastIndex && dates.isNotEmpty()) Modifier.padding(bottom = 8.dp) else Modifier,
        )
    }
    if (dates.isEmpty()) return
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
            onLongClick = { onPersonLongClick(date.person.id) },
            onContactAction = onContactAction,
            onLogInteraction = { onLogInteraction(date.person.id) },
            onSnooze = if (uiState.isDue(date.person.id)) ({ onSnooze(date.person.id) }) else null,
            onSendWishes = { onSendWishes(date.person.id, date.importantDate.id) },
            modifier = if (index == 0) Modifier.padding(top = 8.dp) else Modifier,
        )
    }
}

/** Follow-ups and Loans whose day has come or is within a week, earliest first. */
private fun LazyListScope.tasksSection(
    uiState: UpcomingUiState.Loaded,
    onPersonClick: (Long) -> Unit,
    onClose: (UpcomingTask) -> Unit,
) {
    val tasks = uiState.feed.tasks
    if (tasks.isEmpty()) return
    item(key = "tasks_title") { SectionTitle(stringResource(R.string.upcoming_section_tasks)) }
    itemsIndexed(
        tasks,
        key = { _, it ->
            when (it) {
                is UpcomingFollowUp -> "follow_up_${it.followUp.id}"
                is UpcomingLoan -> "loan_${it.loan.id}"
            }
        },
    ) { index, task ->
        TaskRow(
            task = task,
            photo = uiState.photos[task.person.id],
            today = uiState.today,
            index = index,
            count = tasks.size,
            onClick = { onPersonClick(task.person.id) },
            onClose = { onClose(task) },
        )
    }
}

/**
 * A Follow-up or Loan: what it is, then whose and when ("Sara · I lent · 3 days ago"), and the
 * button that closes it. Tapping opens Person detail.
 */
@Composable
private fun TaskRow(
    task: UpcomingTask,
    photo: File?,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val formats = LocalOrbitFormats.current
    val name = task.person.names.displayName(formats.language)
    val supporting = listOfNotNull(
        bidiIsolate(name),
        (task as? UpcomingLoan)?.loan?.direction?.displayName(),
        taskWhen(task.date, today),
    ).joinToString(stringResource(R.string.separator))
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = photo) },
        supportingContent = {
            Text(
                supporting,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (task.date.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            val label = stringResource(if (task is UpcomingLoan) R.string.loan_mark_returned else R.string.follow_up_mark_done)
            IconButton(onClick = onClose) { Icon(Icons.Filled.Check, contentDescription = label) }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(task.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Everyone Due: innermost Ring first, then longest since the last Interaction. The title says how
 * many; five show, then the group's last row expands the rest in place ("Show 1 more") and folds
 * them back ("Show fewer").
 */
private fun LazyListScope.dueSection(
    uiState: UpcomingUiState.Loaded,
    onPersonClick: (Long) -> Unit,
    onPersonLongClick: (Long) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onShowAllDue: (Boolean) -> Unit,
) {
    val due = uiState.feed.due
    if (due.isEmpty()) return
    item(key = "due_title") {
        val numbers = LocalOrbitFormats.current.numbers
        SectionTitle(
            text = stringResource(R.string.upcoming_section_due),
            count = numbers.format(due.size),
            countDescription = pluralStringResource(R.plurals.digest_body_several, due.size, numbers.format(due.size)),
        )
    }
    val shown = uiState.dueShown
    // The toggle is the group's last segment, so the group's shapes count it.
    val count = shown.size + if (uiState.dueHasMore) 1 else 0
    itemsIndexed(shown, key = { _, it -> "due_${it.person.id}" }) { index, row ->
        DueRow(
            row = row,
            photo = uiState.photos[row.person.id],
            today = uiState.today,
            index = index,
            count = count,
            onClick = { onPersonClick(row.person.id) },
            onLongClick = { onPersonLongClick(row.person.id) },
            onContactAction = onContactAction,
            onLogInteraction = { onLogInteraction(row.person.id) },
            onSnooze = { onSnooze(row.person.id) },
            modifier = Modifier.animateItem(),
        )
    }
    if (uiState.dueHasMore) {
        item(key = "due_more") {
            ShowMoreRow(
                showingAll = uiState.showAllDue,
                hidden = due.size - UpcomingUiState.DUE_ROWS,
                index = count - 1,
                count = count,
                onClick = { onShowAllDue(!uiState.showAllDue) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * The Due group's last segment: "Show 3 more" with a down arrow, or "Show fewer" with an up one.
 * The arrow sits where the avatars do, so the label lines up with the names.
 */
@Composable
private fun ShowMoreRow(showingAll: Boolean, hidden: Int, index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val numbers = LocalOrbitFormats.current.numbers
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (showingAll) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
        },
        modifier = modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(
            if (showingAll) {
                stringResource(R.string.upcoming_show_fewer)
            } else {
                pluralStringResource(R.plurals.upcoming_show_more, hidden, numbers.format(hidden))
            },
            color = colors.primary,
        )
    }
}

/**
 * Important dates and Occasions in the next 30 days. Rows open Person detail or the Occasion; no
 * inline actions (a date's Person still has the long-press sheet).
 */
private fun LazyListScope.comingUpSection(
    uiState: UpcomingUiState.Loaded,
    onPersonClick: (Long) -> Unit,
    onPersonLongClick: (Long) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onOccasionClick: (Long) -> Unit,
) {
    val entries = uiState.feed.comingUpEntries
    if (entries.isEmpty()) return
    item(key = "coming_up_title") { SectionTitle(stringResource(R.string.upcoming_section_coming_up)) }
    itemsIndexed(
        entries,
        key = { _, it ->
            when (it) {
                is UpcomingDate -> "coming_up_${it.importantDate.id}"
                is UpcomingOccasion -> "coming_up_occasion_${it.occasion.id}"
            }
        },
    ) { index, entry ->
        when (entry) {
            is UpcomingDate -> ComingUpRow(
                date = entry,
                today = uiState.today,
                index = index,
                count = entries.size,
                onClick = { onPersonClick(entry.person.id) },
                onLongClick = { onPersonLongClick(entry.person.id) },
                actions = hiddenActions(
                    contact = entry.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() },
                    onContactAction = onContactAction,
                    onLogInteraction = { onLogInteraction(entry.person.id) },
                    onSnooze = if (uiState.isDue(entry.person.id)) ({ onSnooze(entry.person.id) }) else null,
                ),
            )
            is UpcomingOccasion -> OccasionRow(
                occasion = entry,
                whenLine = whenLine(entry, uiState.today),
                index = index,
                count = entries.size,
                onClick = { onOccasionClick(entry.occasion.id) },
            )
        }
    }
}

/**
 * An Occasion: its day badge (or the Occasion badge today), its name, when ([whenLine], null
 * today) and how many People it reaches.
 */
@Composable
private fun OccasionRow(
    occasion: UpcomingOccasion,
    whenLine: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val people = occasion.people.size
    val reach = pluralStringResource(R.plurals.occasion_people, people, formats.numbers.format(people))
    val inPrimary = occasion.date.inCalendar(formats.primaryCalendar)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            if (whenLine == null) {
                Box(
                    Modifier.size(40.dp).clip(OrbitShapes.dateBadge()).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(OrbitIcons.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            } else {
                DateBadge(
                    day = formats.numbers.format(inPrimary.day),
                    month = formats.dates.monthName(formats.primaryCalendar, inPrimary.month),
                )
            }
        },
        supportingContent = {
            Column {
                if (whenLine != null) Text(whenLine)
                Text(reach)
            }
        },
        modifier = modifier.fillMaxWidth().padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(occasionName(occasion.occasion.occasion), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "In 3 days · 5 Aban": when, with the entry's own Calendar when it differs from the Primary one. */
@Composable
private fun whenLine(entry: UpcomingEntry, today: LocalDate): String {
    val formats = LocalOrbitFormats.current
    val ownCalendar = when (entry) {
        is UpcomingDate -> entry.importantDate.date.calendar
        is UpcomingOccasion -> entry.occasion.occasion.date.calendar
    }
    val whenText = RelativeDays.ahead(entry.date, today)?.text()
        ?: formats.dates.formatWithWeekday(entry.date, formats.primaryCalendar, today)
    val second = OrbitDateFormatter.secondCalendar(ownCalendar, formats.primaryCalendar)
        ?.let { formats.dates.format(entry.date, it, today) }
    return listOfNotNull(whenText, second).joinToString(stringResource(R.string.separator))
}

/** A section's title; with [count], how many it holds next to it ([countDescription] for TalkBack). */
@Composable
private fun SectionTitle(text: String, count: String? = null, countDescription: String? = null) {
    if (count == null) {
        OrbitSectionTitle(text, horizontalPadding = 4.dp, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) { heading() },
    ) {
        OrbitSectionTitle(text, horizontalPadding = 4.dp)
        Text(
            count,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 8.dp)
                .semantics { countDescription?.let { contentDescription = it } },
        )
    }
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
    val action = date.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PersonAvatar(
                name = name,
                photo = photo,
                size = 64.dp,
                shape = OrbitShapes.cookie(),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(dateLine, style = MaterialTheme.typography.bodyMedium)
            giftIdeasLine(date.person.openGiftIdeas)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            HeroActions(action, onContactAction, onSendWishes, onLogInteraction)
        }
    }
}

/**
 * The hero's buttons on one row: the contact action (filled), Send wishes and Log interaction as
 * an icon. When a label wouldn't fit (a long translation, a large font size) they wrap instead,
 * with Log interaction labelled, so no label is ever cut.
 */
@Composable
private fun HeroActions(
    action: ContactAction?,
    onContactAction: (ContactAction) -> Unit,
    onSendWishes: () -> Unit,
    onLogInteraction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val filled = ButtonDefaults.buttonColors(containerColor = colors.onTertiaryContainer, contentColor = colors.tertiaryContainer)
    val outlined = ButtonDefaults.outlinedButtonColors(contentColor = colors.onTertiaryContainer)
    val border = BorderStroke(1.dp, colors.onTertiaryContainer)
    val fontScale = LocalDensity.current.fontScale
    var wrap by remember(action, fontScale) { mutableStateOf(false) }
    val onOverflow: (TextLayoutResult) -> Unit = { if (it.hasVisualOverflow) wrap = true }

    @Composable
    fun ButtonLabel(icon: ImageVector, text: String) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, maxLines = 1, softWrap = false, onTextLayout = onOverflow, modifier = Modifier.padding(start = 8.dp))
    }

    @Composable
    fun ContactButton(modifier: Modifier) {
        if (action != null) {
            Button(onClick = { onContactAction(action) }, colors = filled, modifier = modifier) {
                ButtonLabel(action.kind.icon, action.kind.label())
            }
        }
    }

    @Composable
    fun WishesButton(modifier: Modifier) {
        val wishes = stringResource(R.string.wishes_send)
        if (action != null) {
            OutlinedButton(onClick = onSendWishes, colors = outlined, border = border, modifier = modifier) {
                ButtonLabel(Icons.AutoMirrored.Filled.Send, wishes)
            }
        } else {
            Button(onClick = onSendWishes, colors = filled, modifier = modifier) {
                ButtonLabel(Icons.AutoMirrored.Filled.Send, wishes)
            }
        }
    }

    if (wrap) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContactButton(Modifier)
            WishesButton(Modifier)
            OutlinedButton(onClick = onLogInteraction, colors = outlined, border = border) {
                Icon(OrbitIcons.LogInteraction, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.interaction_log), modifier = Modifier.padding(start = 8.dp))
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactButton(Modifier)
            WishesButton(Modifier.weight(1f))
            OutlinedIconButton(
                onClick = onLogInteraction,
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = colors.onTertiaryContainer),
                border = border,
            ) {
                Icon(OrbitIcons.LogInteraction, contentDescription = stringResource(R.string.interaction_log), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Another date today: whose it is, the years count and Gift ideas, then Send wishes and Log
 * interaction. The contact action (and Snooze while Due) are a long-press away, and TalkBack
 * actions.
 */
@Composable
private fun TodayRow(
    date: UpcomingDate,
    photo: File?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onSnooze: (() -> Unit)?,
    onSendWishes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val name = date.person.names.displayName(formats.language)
    val supporting = detailsLine(date)
    val action = date.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    val hidden = hiddenActions(action, onContactAction, onLogInteraction = null, onSnooze = onSnooze)
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = photo) },
        supportingContent = supporting?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            RowActions {
                IconButton(onClick = onSendWishes) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.wishes_send))
                }
                IconButton(onClick = onLogInteraction) {
                    Icon(OrbitIcons.LogInteraction, contentDescription = stringResource(R.string.interaction_log))
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ListItemDefaults.SegmentedGap)
            .semantics { customActions = hidden },
    ) {
        Text(
            dateTitle(name, date.importantDate.type, date.importantDate.label),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row's (at most two) trailing icon buttons, in neutral `onSurfaceVariant`. */
@Composable
private fun RowActions(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/**
 * A Due Person: who, "Close · talked 34 days ago", then the two steps of the core loop as
 * buttons: their contact action (when they have one) and Log interaction. Tapping opens Person
 * detail; long-pressing opens the quick-action sheet, which also has Snooze (a TalkBack action too).
 */
@Composable
private fun DueRow(
    row: DuePerson,
    photo: File?,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val name = row.person.names.displayName(formats.language)
    val action = row.person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    val ring = row.person.ringName
    val last = row.state.lastInteraction
    // After the Ring, the short "talked 20 days ago": the line fits beside the two buttons.
    val talked = if (ring != null && last != null) {
        stringResource(R.string.upcoming_due_talked, RelativeDays.ago(last, today).text())
    } else {
        lastTalkedText(last, today, inline = ring != null)
    }
    val supporting = listOfNotNull(ring?.let(::bidiIsolate), talked).joinToString(stringResource(R.string.separator))
    val hidden = hiddenActions(contact = null, onContactAction = null, onLogInteraction = null, onSnooze = onSnooze)
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { PersonAvatar(name = name, photo = photo) },
        supportingContent = { Text(supporting, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            RowActions {
                action?.let {
                    IconButton(onClick = { onContactAction(it) }) {
                        Icon(it.kind.icon, contentDescription = it.kind.label())
                    }
                }
                IconButton(onClick = onLogInteraction) {
                    Icon(OrbitIcons.LogInteraction, contentDescription = stringResource(R.string.interaction_log))
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ListItemDefaults.SegmentedGap)
            .semantics { customActions = hidden },
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
private fun ComingUpRow(
    date: UpcomingDate,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: List<CustomAccessibilityAction>,
) {
    val formats = LocalOrbitFormats.current
    val name = date.person.names.displayName(formats.language)
    val inPrimary = date.date.inCalendar(formats.primaryCalendar)
    val whenLine = whenLine(date, today)
    val details = detailsLine(date)
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ListItemDefaults.SegmentedGap)
            .semantics { customActions = actions },
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

/** "Next: Sara's birthday · 12 Jan", "Next: Nowruz · 21 Mar". */
@Composable
private fun nextLine(entry: UpcomingEntry, today: LocalDate): String {
    val formats = LocalOrbitFormats.current
    val what = when (entry) {
        is UpcomingDate -> dateTitle(entry.person.names.displayName(formats.language), entry.importantDate.type, entry.importantDate.label)
        is UpcomingOccasion -> occasionNameInSentence(entry.occasion.occasion)
    }
    val whenText = formats.dates.format(entry.date, formats.primaryCalendar, today)
    return stringResource(R.string.upcoming_next, what + stringResource(R.string.separator) + whenText)
}

/** An empty state, under the Backup nudge [card] when it shows. */
@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    card: (@Composable (Modifier) -> Unit)? = null,
) {
    if (card != null) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            card(Modifier.widthIn(max = MaxContentWidth).padding(16.dp))
            OrbitEmptyState(icon, title, body, Modifier.weight(1f))
        }
        return
    }
    OrbitEmptyState(icon, title, body, modifier)
}
