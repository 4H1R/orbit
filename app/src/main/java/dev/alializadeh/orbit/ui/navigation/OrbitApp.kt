package dev.alializadeh.orbit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.about.AboutKey
import dev.alializadeh.orbit.about.AboutRoute
import dev.alializadeh.orbit.about.LicenceTextKey
import dev.alializadeh.orbit.about.LicenceTextRoute
import dev.alializadeh.orbit.about.LicencesKey
import dev.alializadeh.orbit.about.LicencesScreen
import dev.alializadeh.orbit.backup.BackupHost
import dev.alializadeh.orbit.backup.BackupSettingsKey
import dev.alializadeh.orbit.backup.BackupSettingsRoute
import dev.alializadeh.orbit.backup.BackupViewModel
import dev.alializadeh.orbit.backup.ChangePassphraseKey
import dev.alializadeh.orbit.backup.ChangePassphraseRoute
import dev.alializadeh.orbit.backup.RestoreKey
import dev.alializadeh.orbit.backup.RestoreRoute
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.AllInteractionsKey
import dev.alializadeh.orbit.interaction.AllInteractionsRoute
import dev.alializadeh.orbit.lock.AppLockSettingsKey
import dev.alializadeh.orbit.lock.AppLockSettingsRoute
import dev.alializadeh.orbit.orbitview.OrbitViewKey
import dev.alializadeh.orbit.orbitview.OrbitViewRoute
import dev.alializadeh.orbit.person.AddPersonKey
import dev.alializadeh.orbit.person.EditPersonKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.person.detail.PersonDetailRoute
import dev.alializadeh.orbit.person.edit.PersonFormRoute
import dev.alializadeh.orbit.person.list.PeopleRoute
import dev.alializadeh.orbit.reminders.NotificationEntry
import dev.alializadeh.orbit.reminders.NotificationEntryViewModel
import dev.alializadeh.orbit.reminders.ReminderHealthKey
import dev.alializadeh.orbit.reminders.ReminderHealthRoute
import dev.alializadeh.orbit.reminders.ReminderPermissionHost
import dev.alializadeh.orbit.reminders.RemindersSettingsKey
import dev.alializadeh.orbit.reminders.RemindersSettingsRoute
import dev.alializadeh.orbit.search.PeopleLinksViewModel
import dev.alializadeh.orbit.settings.AppearanceRoute
import dev.alializadeh.orbit.settings.AppearanceSettingsKey
import dev.alializadeh.orbit.settings.LanguageDatesRoute
import dev.alializadeh.orbit.settings.LanguageDatesSettingsKey
import dev.alializadeh.orbit.settings.RingsRoute
import dev.alializadeh.orbit.settings.RingsSettingsKey
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.settings.SettingsRoute
import dev.alializadeh.orbit.settings.TagsRoute
import dev.alializadeh.orbit.settings.TagsSettingsKey
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.rememberOrbitSnackbars
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import dev.alializadeh.orbit.upcoming.UpcomingKey
import dev.alializadeh.orbit.upcoming.UpcomingRoute

/**
 * The app shell: four bottom tabs over a single [NavDisplay], with Back up now ([BackupHost])
 * drawn above everything.
 */
@Composable
fun OrbitApp(
    resetNoticePending: Boolean = false,
    onResetNoticeShown: () -> Unit = {},
    navigator: OrbitNavigator = rememberOrbitNavigator(),
    backup: BackupViewModel = hiltViewModel(),
    entries: NotificationEntryViewModel = hiltViewModel(),
    peopleLinks: PeopleLinksViewModel = hiltViewModel(),
) {
    val snackbars = rememberOrbitSnackbars()
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    val restoredPeople by backup.restoredPeople.collectAsStateWithLifecycle()
    // A notification tap: Upcoming, then Person detail over it, or Upcoming scrolled to Due.
    // App lock's gate sits in front of OrbitApp (MainActivity), so this waits until it's passed.
    val entry by entries.pending.collectAsStateWithLifecycle()
    var scrollToDue by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entry) {
        val target = entry ?: return@LaunchedEffect
        when (target) {
            is NotificationEntry.PersonDetail -> navigator.openOnUpcoming(PersonDetailKey(target.personId))
            NotificationEntry.UpcomingDue -> {
                navigator.openOnUpcoming(null)
                scrollToDue = true
            }
        }
        entries.consume(target)
    }
    // After a Restore: every tab back to its root (the old screens' data is gone), Upcoming, and
    // "Restored 84 people". Consumed only once shown, so an activity recreation (the Backup's
    // language) shows it again rather than losing it.
    LaunchedEffect(restoredPeople) {
        val people = restoredPeople ?: return@LaunchedEffect
        navigator.resetToRoots()
        snackbars.hostState.currentSnackbarData?.dismiss()
        snackbars.hostState.showSnackbar(
            resources.getQuantityString(R.plurals.restored_snackbar, people, numbers.format(people)),
        )
        backup.consumeRestored()
    }
    // The device-state file was unreadable and replaced (App lock back on where possible, the
    // remembered passphrase gone): told once, until the user acknowledges or another snackbar.
    LaunchedEffect(resetNoticePending) {
        if (!resetNoticePending) return@LaunchedEffect
        snackbars.hostState.showSnackbar(
            message = resources.getString(R.string.device_state_reset_snackbar),
            actionLabel = resources.getString(R.string.common_ok),
            duration = SnackbarDuration.Indefinite,
        )
        onResetNoticeShown()
    }
    val entries = navigator.rememberEntries(
        entryProvider {
            entry<UpcomingKey> {
                UpcomingRoute(
                    onNewPerson = { navigator.navigate(AddPersonKey) },
                    onPersonClick = { id -> navigator.navigate(PersonDetailKey(id)) },
                    onBackUpNow = backup::start,
                    onFixReminders = { navigator.navigate(ReminderHealthKey) },
                    scrollToDue = scrollToDue,
                    onScrolledToDue = { scrollToDue = false },
                )
            }
            entry<PeopleKey> {
                PeopleRoute(
                    onPersonClick = { id -> navigator.navigate(PersonDetailKey(id)) },
                    onAddPerson = { navigator.navigate(AddPersonKey) },
                )
            }
            entry<AddPersonKey> {
                PersonFormRoute(
                    personId = null,
                    focus = null,
                    onClose = navigator::goBack,
                    // After Add, land on the new Person's detail.
                    onSaved = { id -> navigator.replaceTop(PersonDetailKey(id)) },
                )
            }
            entry<EditPersonKey> { key ->
                PersonFormRoute(
                    personId = key.personId,
                    focus = key.focus,
                    onClose = navigator::goBack,
                    onSaved = { navigator.goBack() },
                )
            }
            entry<PersonDetailKey> { key ->
                PersonDetailRoute(
                    key = key,
                    onBack = navigator::goBack,
                    onEdit = { focus -> navigator.navigate(EditPersonKey(key.personId, focus)) },
                    onAllInteractions = { navigator.navigate(AllInteractionsKey(key.personId)) },
                    onSearchPeople = { text ->
                        peopleLinks.search(text)
                        navigator.openPeople()
                    },
                    onArchived = { name, undo ->
                        snackbars.show(
                            message = resources.getString(R.string.archived_snackbar, bidiIsolate(name)),
                            actionLabel = resources.getString(R.string.common_undo),
                            onAction = undo,
                        )
                        navigator.goBack()
                    },
                )
            }
            entry<AllInteractionsKey> { key -> AllInteractionsRoute(key = key, onBack = navigator::goBack) }
            entry<OrbitViewKey> {
                OrbitViewRoute(
                    opening = navigator.selections(TopLevelTab.Orbit),
                    onPersonClick = { id -> navigator.navigate(PersonDetailKey(id)) },
                    onAddPerson = { navigator.navigate(AddPersonKey) },
                )
            }
            entry<SettingsKey> { SettingsRoute(onOpen = navigator::navigate) }
            entry<AppearanceSettingsKey> { AppearanceRoute(onBack = navigator::goBack) }
            entry<LanguageDatesSettingsKey> { LanguageDatesRoute(onBack = navigator::goBack) }
            entry<RingsSettingsKey> { RingsRoute(onBack = navigator::goBack) }
            entry<TagsSettingsKey> {
                TagsRoute(
                    onBack = navigator::goBack,
                    onShowPeople = { tagId ->
                        peopleLinks.showTag(tagId)
                        navigator.openPeople()
                    },
                )
            }
            entry<RemindersSettingsKey> {
                RemindersSettingsRoute(onBack = navigator::goBack, onOpenHealth = { navigator.navigate(ReminderHealthKey) })
            }
            entry<ReminderHealthKey> { ReminderHealthRoute(onBack = navigator::goBack) }
            entry<AppLockSettingsKey> { AppLockSettingsRoute(onBack = navigator::goBack) }
            entry<BackupSettingsKey> {
                BackupSettingsRoute(
                    onBack = navigator::goBack,
                    onBackUpNow = backup::start,
                    onRestore = { uri -> navigator.navigate(RestoreKey(uri)) },
                    onChangePassphrase = { navigator.navigate(ChangePassphraseKey) },
                )
            }
            entry<ChangePassphraseKey> { ChangePassphraseRoute(onClose = navigator::goBack) }
            entry<AboutKey> { AboutRoute(onBack = navigator::goBack, onOpenLicences = { navigator.navigate(LicencesKey) }) }
            entry<LicencesKey> { LicencesScreen(onBack = navigator::goBack, onOpen = navigator::navigate) }
            entry<LicenceTextKey> { key -> LicenceTextRoute(key = key, onBack = navigator::goBack) }
            entry<RestoreKey> { key ->
                RestoreRoute(
                    uri = key.uri,
                    onClose = navigator::goBack,
                    // The shell moves on when RestoreNotices announces it (above).
                    onRestored = {},
                    onBackUpFirst = backup::start,
                )
            }
        },
    )

    CompositionLocalProvider(LocalOrbitSnackbars provides snackbars) {
        Box(Modifier.fillMaxSize()) {
            OrbitShell(navigator, entries)
            BackupHost(backup)
            ReminderPermissionHost()
        }
    }
}

@Composable
private fun OrbitShell(navigator: OrbitNavigator, entries: List<NavEntry<NavKey>>) {
    Scaffold(
        // Each screen's own Scaffold handles the system bars, so its background reaches behind
        // them; this one only makes room for the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (navigator.showsNavigationBar) {
                OrbitNavigationBar(
                    currentTab = navigator.currentTab,
                    onTabSelected = navigator::selectTab,
                )
            }
        },
    ) { innerPadding ->
        NavDisplay(
            entries = entries,
            onBack = navigator::goBack,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}

@Composable
private fun OrbitNavigationBar(
    currentTab: TopLevelTab,
    onTabSelected: (TopLevelTab) -> Unit,
) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

@get:StringRes
private val TopLevelTab.label: Int
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
