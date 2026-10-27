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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlinx.coroutines.launch

@Composable
fun PersonDetailRoute(
    key: PersonDetailKey,
    onBack: () -> Unit,
    onEdit: (focus: PersonField?) -> Unit,
    /** Archived: show "[name] archived · Undo" ([undo] outlives this screen) and leave. */
    onArchived: (displayName: String, undo: suspend () -> Unit) -> Unit,
    viewModel: PersonDetailViewModel =
        hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(key.personId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = LocalOrbitSnackbars.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val language = LocalOrbitFormats.current.language
    val noApp = stringResource(R.string.contact_no_app)
    val copied = stringResource(R.string.contact_copied)

    val loaded = uiState as? PersonDetailUiState.Loaded
    LaunchedEffect(loaded?.exit) {
        when (loaded?.exit) {
            DetailExit.Archived -> onArchived(loaded.profile.names.displayName(language), viewModel.undoArchive)
            DetailExit.Deleted -> onBack()
            null -> Unit
        }
    }

    PersonDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEdit = { onEdit(null) },
        onAddDetail = onEdit,
        onArchive = viewModel::archive,
        onUnarchive = viewModel::unarchive,
        onDelete = viewModel::askToDelete,
        onDeleteConfirm = viewModel::delete,
        onDeleteDismiss = viewModel::dismissDelete,
        onContactAction = { action ->
            try {
                context.startActivity(Intent(action.intentAction, action.uri.toUri()))
            } catch (_: ActivityNotFoundException) {
                snackbars?.show(noApp)
            }
        },
        onCopy = { value ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(value, value))) }
            // Android 13 and later confirm a copy themselves.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbars?.show(copied)
        },
    )
}

/**
 * Person detail: the header (photo, names, Ring and Tags, the contact button group), then the
 * sections that have content, then Add details for the rest.
 */
@Composable
fun PersonDetailScreen(
    uiState: PersonDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddDetail: (PersonField) -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = uiState as? PersonDetailUiState.Loaded
    var moreContactSheet by rememberSaveable { mutableStateOf(false) }
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
                        onContactAction = onContactAction,
                        onMoreContact = { moreContactSheet = true },
                    )
                    PersonSections(uiState.profile, onContactAction = onContactAction, onCopy = onCopy)
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
