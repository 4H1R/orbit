package dev.alializadeh.orbit.settings

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.ui.CadenceCountUnitFields
import dev.alializadeh.orbit.person.ui.cadenceOf
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.person.ui.sentenceStart
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.CadenceUnit
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.ring.RingWithCount
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.OrbitSelectField
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Settings → Rings. */
@Serializable
data object RingsSettingsKey : NavKey

sealed interface RingsUiState {
    data object Loading : RingsUiState

    /** Every Ring, innermost first, with its count of People. */
    data class Loaded(val rings: List<RingWithCount>) : RingsUiState {
        val canAdd: Boolean get() = rings.size < Ring.MAX_COUNT
    }
}

@HiltViewModel
class RingsViewModel @Inject constructor(private val ringRepository: RingRepository) : ViewModel() {

    val uiState: StateFlow<RingsUiState> = ringRepository.observeRingsWithCounts()
        .map { RingsUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RingsUiState.Loading)

    /** Adds an outermost Ring (Add Ring is disabled at the cap, so this ignores one past it). */
    fun add(name: String, cadence: Cadence?) {
        val loaded = uiState.value as? RingsUiState.Loaded
        if (name.isBlank() || loaded?.canAdd == false) return
        viewModelScope.launch { ringRepository.add(name, cadence) }
    }

    fun update(id: Long, name: String, cadence: Cadence?) {
        if (name.isBlank()) return
        viewModelScope.launch { ringRepository.update(id, name, cadence) }
    }

    /** Moves everyone in [fromId] to [toId], after which [fromId] can be removed. */
    fun movePeople(fromId: Long, toId: Long) {
        viewModelScope.launch { ringRepository.movePeople(fromId, toId) }
    }

    /** Removes an empty Ring that isn't the only one (the sheet only offers that). */
    fun remove(id: Long) {
        val ring = (uiState.value as? RingsUiState.Loaded)?.rings ?: return
        if (ring.size <= 1 || ring.firstOrNull { it.ring.id == id }?.peopleCount != 0) return
        viewModelScope.launch { ringRepository.remove(id) }
    }

    /** Stores a new order, innermost first. */
    fun reorder(ids: List<Long>) {
        viewModelScope.launch { ringRepository.reorder(ids) }
    }
}

@Composable
fun RingsRoute(onBack: () -> Unit, viewModel: RingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RingsScreen(
        uiState = uiState,
        onBack = onBack,
        onAdd = viewModel::add,
        onUpdate = viewModel::update,
        onMovePeople = viewModel::movePeople,
        onRemove = viewModel::remove,
        onReorder = viewModel::reorder,
    )
}

/**
 * Settings → Rings: the Rings innermost first, each with its default Cadence and count of People,
 * reordered by dragging their handles. Tapping one opens its sheet; Add ring is disabled at 6.
 */
@Composable
fun RingsScreen(
    uiState: RingsUiState,
    onBack: () -> Unit,
    onAdd: (name: String, cadence: Cadence?) -> Unit,
    onUpdate: (id: Long, name: String, cadence: Cadence?) -> Unit,
    onMovePeople: (fromId: Long, toId: Long) -> Unit,
    onRemove: (id: Long) -> Unit,
    onReorder: (ids: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Ring whose sheet is open; NEW_RING for Add ring.
    var sheetFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val loaded = uiState as? RingsUiState.Loaded
    OrbitSettingsScaffold(title = stringResource(R.string.settings_rings_title), onBack = onBack, modifier = modifier) {
        if (loaded == null) return@OrbitSettingsScaffold
        Text(
            text = stringResource(R.string.rings_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        )
        ReorderableRings(rings = loaded.rings, onOpen = { sheetFor = it }, onReorder = onReorder)
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            onClick = { sheetFor = NEW_RING },
            enabled = loaded.canAdd,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.rings_add), modifier = Modifier.padding(start = 8.dp))
        }
        if (!loaded.canAdd) {
            Text(
                text = pluralStringResource(R.plurals.rings_max, Ring.MAX_COUNT, LocalOrbitFormats.current.numbers.format(Ring.MAX_COUNT)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
    }

    val open = sheetFor
    if (loaded != null && open != null) {
        val ring = loaded.rings.firstOrNull { it.ring.id == open }
        if (ring != null || open == NEW_RING) {
            OrbitBottomSheet(onDismiss = { sheetFor = null }) { sheet ->
                RingSheetContent(
                    ring = ring,
                    rings = loaded.rings,
                    onSave = { name, cadence ->
                        if (ring == null) onAdd(name, cadence) else onUpdate(ring.ring.id, name, cadence)
                    },
                    onMovePeople = onMovePeople,
                    onRemove = onRemove,
                    sheet = sheet,
                )
            }
        }
    }
}

private const val NEW_RING = -1L

/**
 * The Rings as segmented rows, each with a drag handle: dragging a handle past half a row swaps it
 * with its neighbour, and letting go stores the order. TalkBack gets Move inward / outward.
 */
@Composable
private fun ReorderableRings(rings: List<RingWithCount>, onOpen: (Long) -> Unit, onReorder: (List<Long>) -> Unit) {
    val byId = rings.associateBy { it.ring.id }
    var order by remember(rings) { mutableStateOf(rings.map { it.ring.id }) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    val gap = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.toPx() }
    val resources = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        order.forEachIndexed { index, id ->
            val row = byId[id] ?: return@forEachIndexed
            // Keyed, so a row keeps its drag gesture while it moves to another slot.
            key(id) {
                val name = bidiIsolate(row.ring.name)
                fun move(by: Int) {
                    val target = index + by
                    if (target !in order.indices) return
                    order = order.toMutableList().apply { add(target, removeAt(index)) }
                    onReorder(order)
                }
                val actions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(resources.getString(R.string.rings_move_inward)) { move(-1); true })
                    if (index < order.lastIndex) add(CustomAccessibilityAction(resources.getString(R.string.rings_move_outward)) { move(1); true })
                }
                val isDragged = dragging == id
                RingRow(
                    row = row,
                    index = index,
                    count = order.size,
                    dragged = isDragged,
                    onClick = { onOpen(id) },
                    handle = Modifier
                        .semantics { contentDescription = resources.getString(R.string.rings_reorder, name) }
                        .pointerInput(id, rings) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = id
                                    offset = 0f
                                },
                                onDragEnd = {
                                    dragging = null
                                    offset = 0f
                                    onReorder(order)
                                },
                                onDragCancel = {
                                    dragging = null
                                    offset = 0f
                                    onReorder(order)
                                },
                            ) { change, amount ->
                                change.consume()
                                offset += amount.y
                                val at = order.indexOf(id)
                                val step = (heights[id] ?: 0) + gap
                                if (step <= gap) return@detectDragGestures
                                if (offset > step / 2 && at < order.lastIndex) {
                                    order = order.toMutableList().apply { add(at + 1, removeAt(at)) }
                                    offset -= step
                                } else if (offset < -step / 2 && at > 0) {
                                    order = order.toMutableList().apply { add(at - 1, removeAt(at)) }
                                    offset += step
                                }
                            }
                        },
                    modifier = Modifier
                        .onSizeChanged { heights[id] = it.height }
                        .semantics { customActions = actions }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) offset else 0f },
                )
            }
        }
    }
}

/** A Ring: its name, "every 14 days · 6 people", and the drag handle. */
@Composable
private fun RingRow(
    row: RingWithCount,
    index: Int,
    count: Int,
    dragged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The drag handle's own gestures and TalkBack label. */
    handle: Modifier,
) {
    val separator = stringResource(R.string.separator)
    val summary = listOf(sentenceStart(cadenceText(row.ring.cadence)), peopleCountText(row.peopleCount, inline = true))
        .joinToString(separator)
    Surface(
        onClick = onClick,
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
        color = ListItemDefaults.segmentedColors().containerColor,
        shadowElevation = if (dragged) 6.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
            Icon(
                OrbitIcons.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handle.padding(12.dp).size(24.dp),
            )
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(row.ring.name, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "6 people", "1 person", or "No one yet". */
@Composable
fun peopleCountText(count: Int, inline: Boolean = false): String =
    if (count == 0) {
        stringResource(if (inline) R.string.people_count_none_inline else R.string.people_count_none)
    } else {
        LocalResources.current.getQuantityString(R.plurals.people_count, count, LocalOrbitFormats.current.numbers.format(count))
    }

/**
 * A Ring's sheet: its name and default Cadence, Save, and Remove. Remove is disabled while the
 * Ring holds People, which it offers to move to another Ring first, and for the only Ring.
 * A new Ring ([ring] null) has only the name and Cadence.
 */
@Composable
internal fun RingSheetContent(
    ring: RingWithCount?,
    rings: List<RingWithCount>,
    onSave: (name: String, cadence: Cadence?) -> Unit,
    onMovePeople: (fromId: Long, toId: Long) -> Unit,
    onRemove: (id: Long) -> Unit,
    sheet: SheetCloser,
) {
    val numbers = LocalOrbitFormats.current.numbers
    val name = rememberTextFieldState(ring?.ring?.name.orEmpty())
    val start = ring?.ring?.cadence
    var hasCadence by rememberSaveable { mutableStateOf(ring == null || start != null) }
    var unit by rememberSaveable { mutableStateOf(start?.unit ?: CadenceUnit.Days) }
    val count = rememberTextFieldState(numbers.format(start?.count ?: DEFAULT_DAYS))
    val cadence = cadenceOf(count, unit)
    val canSave = name.text.isNotBlank() && (!hasCadence || cadence != null)
    val others = rings.filter { it.ring.id != ring?.ring?.id }
    var moveTo by rememberSaveable { mutableStateOf(others.firstOrNull()?.ring?.id) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(if (ring == null) R.string.ring_sheet_new else R.string.ring_sheet_edit),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            state = name,
            label = { Text(stringResource(R.string.ring_name)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.ring_default_cadence),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OrbitListGroup(title = null) {
            ChoiceRow(
                title = sentenceStart(cadenceText(cadence ?: Cadence(1, unit))),
                summary = null,
                selected = hasCadence,
                index = 0,
                count = 2,
                onClick = { hasCadence = true },
            )
            ChoiceRow(
                title = stringResource(R.string.cadence_none),
                summary = null,
                selected = !hasCadence,
                index = 1,
                count = 2,
                onClick = { hasCadence = false },
            )
        }
        if (hasCadence) CadenceCountUnitFields(count = count, unit = unit, onUnitSelect = { unit = it })
        if (ring != null) {
            when {
                rings.size <= 1 -> Notice(stringResource(R.string.ring_remove_only))
                ring.peopleCount > 0 -> {
                    Notice(stringResource(R.string.ring_remove_has_people))
                    val target = moveTo?.let { id -> others.firstOrNull { it.ring.id == id } } ?: others.first()
                    OrbitSelectField(
                        selected = target,
                        options = others,
                        label = LocalResources.current.getQuantityString(
                            R.plurals.ring_move_people,
                            ring.peopleCount,
                            numbers.format(ring.peopleCount),
                        ),
                        optionText = { it.ring.name },
                        onSelect = { moveTo = it.ring.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalButton(onClick = { onMovePeople(ring.ring.id, target.ring.id) }) {
                        Text(stringResource(R.string.ring_move))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (ring != null) {
                TextButton(
                    enabled = rings.size > 1 && ring.peopleCount == 0,
                    onClick = { sheet.close { onRemove(ring.ring.id) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.ring_remove))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(
                enabled = canSave,
                onClick = {
                    val newName = name.text.toString().trim()
                    val newCadence = if (hasCadence) cadence else null
                    sheet.close { onSave(newName, newCadence) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val DEFAULT_DAYS = 30
