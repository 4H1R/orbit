package dev.alializadeh.orbit.person.edit

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.photo.PhotoCropScreen
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlinx.coroutines.android.awaitFrame

/**
 * The Add/Edit Person form for [personId] (null adds a Person), scrolled to [focus] if given.
 * [onSaved] gets the Person's id once stored; [onClose] leaves without saving.
 */
@Composable
fun PersonFormRoute(
    personId: Long?,
    focus: PersonField?,
    onClose: () -> Unit,
    onSaved: (personId: Long) -> Unit,
    viewModel: PersonFormViewModel = hiltViewModel<PersonFormViewModel, PersonFormViewModel.Factory>(
        creationCallback = { factory -> factory.create(personId, focus) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.savedPersonId) { uiState.savedPersonId?.let(onSaved) }
    LaunchedEffect(uiState.notFound) { if (uiState.notFound) onClose() }
    val onPicked = { uri: Uri? -> if (uri != null) viewModel.startCrop(uri.toString()) }
    val pickPhoto = rememberLauncherForActivityResult(PickVisualMedia(), onPicked)
    // Some system images ship without the photo picker activity: fall back to the document picker.
    val openImage = rememberLauncherForActivityResult(OpenDocument(), onPicked)
    val choosePhoto = {
        try {
            pickPhoto.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            try {
                openImage.launch(arrayOf("image/*"))
            } catch (_: ActivityNotFoundException) {
                // Nothing on this phone can pick a photo.
            }
        }
    }
    val requestClose = { if (viewModel.requestClose()) onClose() }
    BackHandler(onBack = requestClose)

    val crop = uiState.crop
    val focusManager = LocalFocusManager.current
    // The keyboard has no business over the crop screen.
    LaunchedEffect(crop != null) { if (crop != null) focusManager.clearFocus() }
    Box {
        PersonFormScreen(
            uiState = uiState,
            fields = viewModel.fields,
            actions = viewModel,
            onChoosePhoto = choosePhoto,
            onClose = requestClose,
            onDiscard = {
                viewModel.discard()
                onClose()
            },
            modifier = if (crop != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
        if (crop != null) {
            PhotoCropScreen(
                state = crop,
                onCancel = viewModel::cancelCrop,
                onDone = viewModel::finishCrop,
                // Swallows touches, so nothing reaches the form underneath.
                modifier = Modifier.clickable(interactionSource = null, indication = null) {},
            )
        }
    }
}

/** The form itself: Photo, Name, Ring, Contact methods up front, the rest behind More details. */
@Composable
fun PersonFormScreen(
    uiState: PersonFormUiState,
    fields: PersonFormFields,
    actions: PersonFormActions,
    onChoosePhoto: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var photoSheet by rememberSaveable { mutableStateOf(false) }
    var cadenceSheet by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FormFocus() }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            OrbitTopAppBar(
                title = stringResource(if (uiState.isNew) R.string.person_add_title else R.string.person_edit_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    Button(
                        onClick = actions::save,
                        enabled = uiState.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                OrbitLoadingIndicator()
            }
            return@Scaffold
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhotoSlot(
                    name = fields.name.text.toString(),
                    photo = uiState.photo,
                    onClick = {
                        focusManager.clearFocus()
                        photoSheet = true
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).bringIntoViewRequester(focus.bringer(PersonField.Photo)),
                )
                NameField(fields.name, focus.requester(null), onDone = actions::save)
                RingSection(
                    uiState = uiState,
                    onSelect = actions::selectRing,
                    onChangeCadence = {
                        focusManager.clearFocus()
                        cadenceSheet = true
                    },
                )
                ContactMethodsSection(
                    uiState = uiState,
                    actions = actions,
                    modifier = Modifier.bringIntoViewRequester(focus.bringer(PersonField.ContactMethods)),
                )
                // Slice 3: the Birthday row (and "+ Important date") goes here, up front.
                MoreDetailFields(uiState, fields, actions, focus)
                if (uiState.showsMoreDetails) {
                    MoreDetailsButton(onClick = actions::showMoreDetails)
                }
            }
        }
    }

    FocusEffect(uiState, actions, focus, onPhoto = { photoSheet = true })

    if (photoSheet) {
        PhotoSheet(
            hasPhoto = uiState.photo != null,
            onChoose = onChoosePhoto,
            onRemove = actions::removePhoto,
            onDismiss = { photoSheet = false },
        )
    }
    if (cadenceSheet) {
        CadenceSheet(
            current = uiState.cadence,
            ringCadence = uiState.selectedRing?.cadence,
            onPick = actions::setCadence,
            onDismiss = { cadenceSheet = false },
        )
    }
    when (val dialog = uiState.dialog) {
        FormDialog.Discard -> AlertDialog(
            onDismissRequest = actions::dismissDialog,
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard_confirm)) } },
            dismissButton = {
                TextButton(onClick = actions::dismissDialog) { Text(stringResource(R.string.discard_keep)) }
            },
        )
        is FormDialog.DuplicateName -> AlertDialog(
            onDismissRequest = actions::dismissDialog,
            title = { Text(stringResource(R.string.duplicate_name_title, bidiIsolate(dialog.name))) },
            confirmButton = {
                TextButton(onClick = actions::saveAnyway) { Text(stringResource(R.string.duplicate_save_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDialog) { Text(stringResource(R.string.duplicate_go_back)) }
            },
        )
        null -> Unit
    }
}

/** Where each field is, so an Add details chip can scroll to it and focus it. */
internal class FormFocus {
    private val bringers = mutableMapOf<PersonField?, BringIntoViewRequester>()
    private val requesters = mutableMapOf<PersonField?, FocusRequester>()

    fun bringer(field: PersonField?): BringIntoViewRequester = bringers.getOrPut(field) { BringIntoViewRequester() }

    fun requester(field: PersonField?): FocusRequester = requesters.getOrPut(field) { FocusRequester() }

    suspend fun show(field: PersonField) {
        bringer(field).bringIntoView()
        requesters[field]?.let { runCatching { it.requestFocus() } }
    }
}

/** On opening: focus the Name of a new Person, or scroll to the field an Add details chip asked for. */
@Composable
private fun FocusEffect(
    uiState: PersonFormUiState,
    actions: PersonFormActions,
    focus: FormFocus,
    onPhoto: () -> Unit,
) {
    val target = uiState.focus
    LaunchedEffect(uiState.isLoading, target) {
        if (uiState.isLoading) return@LaunchedEffect
        when {
            target == null -> if (uiState.isNew) runCatching { focus.requester(null).requestFocus() }
            target == PersonField.Photo -> onPhoto()
            else -> {
                if (target == PersonField.ContactMethods && uiState.contacts.isEmpty()) actions.addContact()
                awaitFrame()
                focus.show(target)
            }
        }
        if (target != null) actions.focusDone()
    }
}
