package dev.alializadeh.orbit.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.TagRepository
import dev.alializadeh.orbit.person.TagWithCount
import dev.alializadeh.orbit.person.nameComparator
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Settings → Tags. */
@Serializable
data object TagsSettingsKey : NavKey

/** Renaming [source] to [target]'s name: asks "Merge into Gym?". */
data class PendingMerge(val source: Tag, val target: Tag)

sealed interface TagsUiState {
    data object Loading : TagsUiState

    /** Every Tag with its count of People (sorted by the screen, in the UI language's collation). */
    data class Loaded(val tags: List<TagWithCount>, val pendingMerge: PendingMerge? = null) : TagsUiState
}

@HiltViewModel
class TagsViewModel @Inject constructor(private val tagRepository: TagRepository) : ViewModel() {

    private val pendingMerge = MutableStateFlow<PendingMerge?>(null)

    val uiState: StateFlow<TagsUiState> =
        combine(tagRepository.observeTags(), pendingMerge) { tags, merge -> TagsUiState.Loaded(tags, merge) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState.Loading)

    /**
     * Renames the Tag; when another Tag already has [name] (ignoring case), asks to merge into it
     * instead ([confirmMerge]).
     */
    fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val clash = tagRepository.find(trimmed)
            val source = (uiState.value as? TagsUiState.Loaded)?.tags?.firstOrNull { it.tag.id == id }?.tag
            if (clash != null && clash.id != id && source != null) {
                pendingMerge.value = PendingMerge(source, clash)
            } else {
                tagRepository.rename(id, trimmed)
            }
        }
    }

    fun confirmMerge() {
        val merge = pendingMerge.value ?: return
        pendingMerge.value = null
        viewModelScope.launch { tagRepository.merge(merge.source.id, merge.target.id) }
    }

    fun dismissMerge() {
        pendingMerge.value = null
    }

    /** Deletes the Tag and takes it off everyone (the screen confirms first when anyone has it). */
    fun delete(id: Long) {
        viewModelScope.launch { tagRepository.delete(id) }
    }
}

@Composable
fun TagsRoute(onBack: () -> Unit, onShowPeople: (tagId: Long) -> Unit, viewModel: TagsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TagsScreen(
        uiState = uiState,
        onBack = onBack,
        onRename = viewModel::rename,
        onConfirmMerge = viewModel::confirmMerge,
        onDismissMerge = viewModel::dismissMerge,
        onDelete = viewModel::delete,
        onShowPeople = onShowPeople,
    )
}

/**
 * Settings → Tags: every Tag with its count of People. Tapping one opens a sheet to rename it
 * (renaming to an existing Tag's name merges, after asking) or delete it (after asking, when anyone
 * has it).
 *
 * The sheet's Show people ([onShowPeople]) opens the People tab filtered by the Tag.
 */
@Composable
fun TagsScreen(
    uiState: TagsUiState,
    onBack: () -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onConfirmMerge: () -> Unit,
    onDismissMerge: () -> Unit,
    onDelete: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
    onShowPeople: ((tagId: Long) -> Unit)? = null,
) {
    var sheetFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val loaded = uiState as? TagsUiState.Loaded
    val language = LocalOrbitFormats.current.language
    val numbers = LocalOrbitFormats.current.numbers
    OrbitSettingsScaffold(title = stringResource(R.string.settings_tags_title), onBack = onBack, modifier = modifier) {
        if (loaded == null) return@OrbitSettingsScaffold
        if (loaded.tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@OrbitSettingsScaffold
        }
        val sorted = remember(loaded.tags, language) {
            val comparator = nameComparator(language)
            loaded.tags.sortedWith { a, b -> comparator.compare(a.tag.name, b.tag.name) }
        }
        OrbitListGroup(title = null, modifier = Modifier.padding(top = 8.dp)) {
            sorted.forEachIndexed { index, item ->
                NavigationRow(
                    title = item.tag.name,
                    summary = peopleCountText(item.peopleCount),
                    index = index,
                    count = sorted.size,
                    onClick = { sheetFor = item.tag.id },
                )
            }
        }
    }

    val open = loaded?.tags?.firstOrNull { it.tag.id == sheetFor }
    if (open != null) {
        OrbitBottomSheet(onDismiss = { sheetFor = null }) { sheet ->
            TagSheetContent(
                tag = open,
                onRename = { name -> onRename(open.tag.id, name) },
                onDelete = {
                    if (open.peopleCount == 0) onDelete(open.tag.id) else confirmingDelete = open.tag.id
                },
                onShowPeople = onShowPeople?.let { show -> { show(open.tag.id) } },
                sheet = sheet,
            )
        }
    }

    val deleting = loaded?.tags?.firstOrNull { it.tag.id == confirmingDelete }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = {
                Text(
                    LocalResources.current.getQuantityString(
                        R.plurals.tag_delete_title,
                        deleting.peopleCount,
                        bidiIsolate(deleting.tag.name),
                        numbers.format(deleting.peopleCount),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = null
                        onDelete(deleting.tag.id)
                    },
                ) { Text(stringResource(R.string.tag_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    val merge = loaded?.pendingMerge
    if (merge != null) {
        AlertDialog(
            onDismissRequest = onDismissMerge,
            title = { Text(stringResource(R.string.tag_merge_title, bidiIsolate(merge.target.name))) },
            text = { Text(stringResource(R.string.tag_merge_body, bidiIsolate(merge.source.name), bidiIsolate(merge.target.name))) },
            confirmButton = { TextButton(onClick = onConfirmMerge) { Text(stringResource(R.string.tag_merge_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissMerge) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** A Tag's sheet: its name with Rename, and Delete (and, from slice 8, Show People). */
@Composable
internal fun TagSheetContent(
    tag: TagWithCount,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onShowPeople: (() -> Unit)?,
    sheet: SheetCloser,
) {
    val name = rememberTextFieldState(tag.tag.name)
    val changed = name.text.toString().trim().let { it.isNotEmpty() && it != tag.tag.name }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(tag.tag.name, style = MaterialTheme.typography.titleLarge)
        Text(peopleCountText(tag.peopleCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            state = name,
            label = { Text(stringResource(R.string.tag_name)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (onShowPeople != null) {
            TextButton(onClick = { sheet.close(onShowPeople) }) { Text(stringResource(R.string.tag_show_people)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { sheet.close(onDelete) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.common_delete))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(
                enabled = changed,
                onClick = {
                    val newName = name.text.toString()
                    sheet.close { onRename(newName) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.tag_rename))
            }
        }
    }
}
