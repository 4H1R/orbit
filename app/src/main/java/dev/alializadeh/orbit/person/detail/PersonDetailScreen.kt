package dev.alializadeh.orbit.person.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.KeepInTouchSheets
import dev.alializadeh.orbit.interaction.KeepInTouchViewModel
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.person.ImportantDateDraft
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.dates.ImportantDateSheet
import dev.alializadeh.orbit.person.gifts.GiftIdeaSheet
import dev.alializadeh.orbit.person.gifts.GivenDateSheet
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun PersonDetailRoute(
    key: PersonDetailKey,
    onBack: () -> Unit,
    onEdit: (focus: PersonField?) -> Unit,
    onAllInteractions: () -> Unit,
    /** A Likes, Dislikes or Can help with chip: search People for its text. */
    onSearchPeople: (String) -> Unit,
    /** Archived: show "[name] archived · Undo" ([undo] outlives this screen) and leave. */
    onArchived: (displayName: String, undo: suspend () -> Unit) -> Unit,
    viewModel: PersonDetailViewModel =
        hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(key.personId) },
        ),
    keepInTouchViewModel: KeepInTouchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from the background: it may be another day (Due, years counts).
    LifecycleResumeEffect(viewModel) {
        viewModel.checkDate()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val snackbars = LocalOrbitSnackbars.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val language = LocalOrbitFormats.current.language
    val noApp = stringResource(R.string.contact_no_app)
    val copied = stringResource(R.string.contact_copied)
    val markedGiven = stringResource(R.string.gift_idea_marked_given)
    val undo = stringResource(R.string.common_undo)
    val startAction: (ContactAction) -> Unit = { action ->
        try {
            context.startActivity(Intent(action.intentAction, action.uri.toUri()))
        } catch (_: ActivityNotFoundException) {
            snackbars?.show(noApp)
        }
    }

    val loaded = uiState as? PersonDetailUiState.Loaded
    LaunchedEffect(loaded?.exit) {
        when (loaded?.exit) {
            DetailExit.Archived -> onArchived(loaded.profile.names.displayName(language), viewModel.undoArchive)
            DetailExit.Deleted -> onBack()
            null -> Unit
        }
    }

    val keepInTouch = remember(keepInTouchViewModel, key.personId) {
        object : PersonDetailKeepInTouch {
            override fun logInteraction() = keepInTouchViewModel.logInteraction(key.personId)
            override fun openInteraction(interactionId: Long) = keepInTouchViewModel.editInteraction(interactionId)
            override fun allInteractions() = onAllInteractions()
            override fun snooze() = keepInTouchViewModel.openSnooze(key.personId)
            override fun cancelSnooze() = keepInTouchViewModel.cancelSnooze(key.personId)
        }
    }
    KeepInTouchSheets(keepInTouchViewModel)
    PersonDetailScreen(
        uiState = uiState,
        keepInTouch = keepInTouch,
        onBack = onBack,
        onEdit = { onEdit(null) },
        onAddDetail = onEdit,
        onArchive = viewModel::archive,
        onUnarchive = viewModel::unarchive,
        onDelete = viewModel::askToDelete,
        onDeleteConfirm = viewModel::delete,
        onDeleteDismiss = viewModel::dismissDelete,
        edits = viewModel,
        onMarkGiven = { id, date ->
            viewModel.markGiven(id, date)
            val undoGiven = viewModel.undoGiven
            snackbars?.show(markedGiven, actionLabel = undo) { undoGiven(id) }
        },
        onOpenLink = { link -> ContactActions.of(ContactType.Link, link).firstOrNull()?.let(startAction) },
        onContactAction = startAction,
        onCopy = { value ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(value, value))) }
            // Android 13 and later confirm a copy themselves.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbars?.show(copied)
        },
        onSearchPeople = onSearchPeople,
    )
}

/**
 * Person detail: the header (photo, names, Ring and Tags, the contact button group), then the
 * sections that have content, then Add details for the rest.
 */
@Composable
fun PersonDetailScreen(
    uiState: PersonDetailUiState,
    keepInTouch: PersonDetailKeepInTouch,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddDetail: (PersonField) -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    edits: PersonDetailEdits,
    onMarkGiven: (id: Long, date: LocalDate) -> Unit,
    onOpenLink: (String) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearchPeople: (String) -> Unit = {},
) {
    val loaded = uiState as? PersonDetailUiState.Loaded
    var moreContactSheet by rememberSaveable { mutableStateOf(false) }
    // Which date or idea a sheet is open on (NEW_GIFT_IDEA: a new idea).
    var dateSheet by rememberSaveable { mutableStateOf<Long?>(null) }
    var giftSheet by rememberSaveable { mutableStateOf<Long?>(null) }
    var givenSheet by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { OrbitSnackbarHost() },
        topBar = {
            OrbitTopAppBar(
                title = null,
                onBack = onBack,
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.common_edit))
                        }
                        OverflowMenu(
                            archived = loaded.profile.person.isArchived,
                            onArchive = onArchive,
                            onUnarchive = onUnarchive,
                            onDelete = onDelete,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            PersonDetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OrbitLoadingIndicator()
            }

            PersonDetailUiState.NotFound -> Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.person_detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is PersonDetailUiState.Loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.profile.person.isArchived) ArchivedBanner(onUnarchive)
                    PersonHeader(
                        uiState = uiState,
                        keepInTouch = keepInTouch,
                        onContactAction = onContactAction,
                        onMoreContact = { moreContactSheet = true },
                    )
                    PersonSections(
                        uiState = uiState,
                        keepInTouch = keepInTouch,
                        onOpenDate = { dateSheet = it.id },
                        onAddGiftIdea = { giftSheet = NEW_GIFT_IDEA },
                        onOpenGiftIdea = { giftSheet = it.id },
                        onMarkGiven = { givenSheet = it.id },
                        onOpenLink = onOpenLink,
                        onContactAction = onContactAction,
                        onCopy = onCopy,
                        onSearchPeople = onSearchPeople,
                    )
                    AddDetails(uiState.missingFields, onAddDetail)
                }
            }
        }
    }

    if (loaded != null && moreContactSheet) {
        MoreContactSheet(
            methods = loaded.profile.contactMethods,
            onContactAction = onContactAction,
            onCopy = onCopy,
            onDismiss = { moreContactSheet = false },
        )
    }
    if (loaded != null) {
        DetailSheets(
            uiState = loaded,
            dateSheet = dateSheet,
            giftSheet = giftSheet,
            givenSheet = givenSheet,
            edits = edits,
            onMarkGiven = onMarkGiven,
            onOpenLink = onOpenLink,
            onGiven = { givenSheet = it },
            onDismissDate = { dateSheet = null },
            onDismissGift = { giftSheet = null },
            onDismissGiven = { givenSheet = null },
        )
    }
    if (loaded != null && loaded.confirmingDelete) {
        val name = loaded.profile.names.displayName(LocalOrbitFormats.current.language)
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.delete_person_title, bidiIsolate(name))) },
            text = { Text(stringResource(R.string.delete_person_body)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun OverflowMenu(archived: Boolean, onArchive: () -> Unit, onUnarchive: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (archived) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.person_detail_unarchive)) },
                    onClick = {
                        expanded = false
                        onUnarchive()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.person_detail_archive)) },
                    onClick = {
                        expanded = false
                        onArchive()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.person_detail_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

private const val NEW_GIFT_IDEA = -1L

/** The date sheet, the Gift idea sheet and the given-date sheet, whichever is open. */
@Composable
private fun DetailSheets(
    uiState: PersonDetailUiState.Loaded,
    dateSheet: Long?,
    giftSheet: Long?,
    givenSheet: Long?,
    edits: PersonDetailEdits,
    onMarkGiven: (id: Long, date: LocalDate) -> Unit,
    onOpenLink: (String) -> Unit,
    onGiven: (id: Long) -> Unit,
    onDismissDate: () -> Unit,
    onDismissGift: () -> Unit,
    onDismissGiven: () -> Unit,
) {
    val profile = uiState.profile
    val date = dateSheet?.let { id -> profile.importantDates.firstOrNull { it.id == id } }
    if (date != null) {
        ImportantDateSheet(
            initial = ImportantDateDraft.of(date),
            newType = date.type,
            birthdayTaken = profile.importantDates.any { it.type == ImportantDateType.Birthday && it.id != date.id },
            defaultLeadTimes = uiState.defaultLeadTimes,
            today = uiState.today,
            onDone = edits::saveImportantDate,
            onDelete = { edits.deleteImportantDate(date.id) },
            onDismiss = onDismissDate,
        )
    }
    if (giftSheet != null) {
        val idea = profile.giftIdeas.firstOrNull { it.id == giftSheet }
        // A deleted idea's sheet doesn't reopen as a new one.
        if (idea != null || giftSheet == NEW_GIFT_IDEA) {
            GiftIdeaSheet(
                initial = idea,
                today = uiState.today,
                onSave = edits::saveGiftIdea,
                onDelete = idea?.let { { edits.deleteGiftIdea(it.id) } },
                onMarkGiven = if (idea != null && !idea.isGiven) {
                    { draft ->
                        edits.saveGiftIdea(draft)
                        onGiven(idea.id)
                    }
                } else {
                    null
                },
                onOpenLink = onOpenLink,
                onDismiss = onDismissGift,
            )
        }
    }
    if (givenSheet != null) {
        GivenDateSheet(
            today = uiState.today,
            onConfirm = { onMarkGiven(givenSheet, it) },
            onDismiss = onDismissGiven,
        )
    }
}
