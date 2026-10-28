package dev.alializadeh.orbit.person.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.dates.DateTypeBadge
import dev.alializadeh.orbit.person.dates.importantDateName
import dev.alializadeh.orbit.person.dates.remindersShort
import dev.alializadeh.orbit.person.CadenceSetting
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.PersonItem
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactInput
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.person.ui.displayName
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.ui.components.OrbitAdaptiveChoice
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.expressive.OrbitAutocompleteField
import dev.alializadeh.orbit.ui.expressive.OrbitSelectField
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.expressive.OrbitSuggestion
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.io.File

/** The photo slot (flower shape) and its "Add photo" / "Change photo" button. */
@Composable
internal fun PhotoSlot(name: String, photo: File?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val displayName = PersonNames(name).displayName(LocalOrbitFormats.current.language)
    Column(modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PersonAvatar(
            name = displayName,
            photo = photo,
            size = 112.dp,
            shape = OrbitShapes.flower(),
            textStyle = MaterialTheme.typography.displaySmall,
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.photo_choose), onClick = onClick),
        )
        TextButton(onClick = onClick) {
            Icon(OrbitIcons.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(if (photo == null) R.string.person_form_photo_add else R.string.person_form_photo_change),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
internal fun NameField(state: TextFieldState, focusRequester: FocusRequester, onDone: () -> Unit) {
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(R.string.person_form_name)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

/** The Ring picker and, under it, "Cadence: every 30 days (from ring)" with Change. */
@Composable
internal fun RingSection(uiState: PersonFormUiState, onSelect: (Long) -> Unit, onChangeCadence: () -> Unit) {
    Column {
        OrbitSectionTitle(stringResource(R.string.person_section_ring), horizontalPadding = 4.dp)
        OrbitAdaptiveChoice(
            options = uiState.rings,
            selected = uiState.selectedRing,
            onSelect = { onSelect(it.id) },
            label = { it.name },
        )
        val cadence = when (val setting = uiState.cadence) {
            CadenceSetting.FromRing ->
                stringResource(R.string.person_form_cadence_from_ring, cadenceText(uiState.selectedRing?.cadence, inline = true))
            is CadenceSetting.Own -> cadenceText(setting.cadence, inline = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(R.string.person_form_cadence, cadence),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChangeCadence) { Text(stringResource(R.string.person_form_cadence_change)) }
        }
    }
}

@Composable
internal fun ContactMethodsSection(uiState: PersonFormUiState, actions: PersonFormActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrbitSectionTitle(stringResource(R.string.person_section_contact_methods), horizontalPadding = 4.dp)
        uiState.contacts.forEach { row ->
            ContactRowEditor(row, autoFocus = row.key == uiState.newContactKey, actions = actions)
        }
        TextButton(onClick = actions::addContact) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.person_form_contact_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ContactRowEditor(row: ContactRow, autoFocus: Boolean, actions: PersonFormActions) {
    val valueFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { valueFocus.requestFocus() } }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrbitSelectField(
                    selected = row.type,
                    options = ContactType.entries,
                    label = stringResource(R.string.person_form_contact_type),
                    optionText = { it.displayName() },
                    onSelect = { actions.setContactType(row.key, it) },
                    leadingIcon = { Icon(it.icon, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
                val starLabel = stringResource(
                    if (row.isPrimary) R.string.person_form_contact_primary else R.string.person_form_contact_make_primary,
                )
                IconToggleButton(
                    checked = row.isPrimary,
                    onCheckedChange = { if (it) actions.setPrimary(row.key) },
                    modifier = Modifier.semantics { contentDescription = starLabel },
                ) {
                    Icon(
                        imageVector = if (row.isPrimary) Icons.Filled.Star else OrbitIcons.StarOutline,
                        contentDescription = null,
                        tint = if (row.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { actions.removeContact(row.key) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.person_form_contact_remove))
                }
            }
            OutlinedTextField(
                state = row.value,
                label = { Text(stringResource(row.type.valueLabelRes)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = row.type.keyboardOptions(),
                // Values read left to right in both languages (an Address follows its own text).
                textStyle = if (row.type.input == ContactInput.Text) {
                    LocalTextStyle.current
                } else {
                    LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(valueFocus),
            )
            OutlinedTextField(
                state = row.label,
                label = { Text(stringResource(R.string.person_form_contact_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun ContactType.keyboardOptions(): KeyboardOptions = when (input) {
    ContactInput.PhoneNumber -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
    ContactInput.Email -> KeyboardOptions(
        keyboardType = KeyboardType.Email,
        autoCorrectEnabled = false,
        imeAction = ImeAction.Next,
    )
    ContactInput.Handle -> KeyboardOptions(
        keyboardType = if (this == ContactType.Telegram) KeyboardType.Text else KeyboardType.Ascii,
        autoCorrectEnabled = false,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Next,
    )
    ContactInput.Link -> KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next)
    ContactInput.Text -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
}

/** The Birthday, up front: its row, or Add birthday. */
@Composable
internal fun BirthdaySection(uiState: PersonFormUiState, actions: PersonFormActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrbitSectionTitle(stringResource(R.string.important_date_type_birthday), horizontalPadding = 4.dp)
        val birthday = uiState.birthday
        if (birthday != null) {
            DateRowItem(birthday, onClick = { actions.editImportantDate(birthday.key) })
        } else {
            TextButton(onClick = { actions.addImportantDate(ImportantDateType.Birthday) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.person_form_birthday_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** The other Important dates, behind More details: a row each, then Add important date. */
@Composable
private fun ImportantDatesField(uiState: PersonFormUiState, actions: PersonFormActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrbitSectionTitle(stringResource(R.string.person_section_important_dates), horizontalPadding = 4.dp)
        uiState.otherDates.forEach { row -> DateRowItem(row, onClick = { actions.editImportantDate(row.key) }) }
        TextButton(onClick = { actions.addImportantDate(ImportantDateType.Anniversary) }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.person_form_important_date_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** One date as entered: "Birthday · 14 Farvardin 1370 · default reminders". Tapping opens its sheet. */
@Composable
private fun DateRowItem(row: DateRow, onClick: () -> Unit) {
    val formats = LocalOrbitFormats.current
    val draft = row.draft
    val text = listOf(
        importantDateName(draft.type, draft.label?.let(::bidiIsolate)),
        formats.dates.format(draft.date),
        remindersShort(draft.leadTimes),
    ).joinToString(stringResource(R.string.separator))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            DateTypeBadge(draft.type, size = 40.dp)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The fields behind "More details" that are showing, in the form's order. */
@Composable
internal fun MoreDetailFields(
    uiState: PersonFormUiState,
    fields: PersonFormFields,
    actions: PersonFormActions,
    focus: FormFocus,
) {
    PersonField.moreDetails.filter { it in uiState.visibleFields }.forEach { field ->
        val modifier = Modifier.bringIntoViewRequester(focus.bringer(field))
        when (field) {
            PersonField.Nickname -> SingleLineField(
                state = fields.nickname,
                label = stringResource(R.string.person_field_nickname),
                focusRequester = focus.requester(field),
                modifier = modifier,
            )
            PersonField.OtherScriptName -> SingleLineField(
                state = fields.otherScriptName,
                label = stringResource(R.string.person_field_other_script_name),
                focusRequester = focus.requester(field),
                modifier = modifier,
            )
            PersonField.Tags -> TagsField(uiState, fields.tagInput, actions, focus.requester(field), modifier)
            PersonField.ImportantDates -> ImportantDatesField(uiState, actions, modifier)
            PersonField.CanHelpWith -> ItemsField(PersonItemKind.CanHelpWith, uiState, fields, actions, focus, modifier)
            PersonField.Likes -> ItemsField(PersonItemKind.Like, uiState, fields, actions, focus, modifier)
            PersonField.Dislikes -> ItemsField(PersonItemKind.Dislike, uiState, fields, actions, focus, modifier)
            PersonField.HowWeMet -> MultiLineField(
                state = fields.howWeMet,
                label = stringResource(R.string.person_section_how_we_met),
                focusRequester = focus.requester(field),
                modifier = modifier,
            )
            PersonField.Notes -> MultiLineField(
                state = fields.notes,
                label = stringResource(R.string.person_section_notes),
                focusRequester = focus.requester(field),
                modifier = modifier,
            )
            PersonField.Photo, PersonField.ContactMethods, PersonField.Birthday -> Unit
        }
    }
}

@Composable
private fun SingleLineField(
    state: TextFieldState,
    label: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
    )
}

@Composable
private fun MultiLineField(
    state: TextFieldState,
    label: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
    )
}

@Composable
private fun TagsField(
    uiState: PersonFormUiState,
    input: TextFieldState,
    actions: PersonFormActions,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OrbitSectionTitle(stringResource(R.string.person_section_tags), horizontalPadding = 4.dp)
        ChipRow(
            texts = uiState.tags,
            selectedIndex = null,
            onClick = { index -> actions.removeTag(uiState.tags[index]) },
            onRemove = { index -> actions.removeTag(uiState.tags[index]) },
        )
        OrbitAutocompleteField(
            state = input,
            label = stringResource(R.string.person_form_tag_add),
            suggestions = uiState.tagSuggestions.map { suggestion ->
                OrbitSuggestion(
                    text = suggestion.name,
                    label = if (suggestion.isNew) {
                        stringResource(R.string.person_form_tag_create, suggestion.name)
                    } else {
                        suggestion.name
                    },
                )
            },
            onSuggestion = { picked -> actions.pickTag(uiState.tagSuggestions.first { it.name == picked.text }) },
            onSubmit = actions::submitTag,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

@Composable
private fun ItemsField(
    kind: PersonItemKind,
    uiState: PersonFormUiState,
    fields: PersonFormFields,
    actions: PersonFormActions,
    focus: FormFocus,
    modifier: Modifier = Modifier,
) {
    val items = uiState.items[kind].orEmpty()
    Column(modifier) {
        OrbitSectionTitle(stringResource(kind.titleRes), horizontalPadding = 4.dp)
        ChipRow(
            texts = items,
            selectedIndex = uiState.editingItem?.takeIf { it.kind == kind }?.index,
            onClick = { index -> actions.editItem(kind, index) },
            onRemove = { index -> actions.removeItem(kind, index) },
        )
        OrbitAutocompleteField(
            state = fields.itemInputs.getValue(kind),
            label = stringResource(R.string.person_form_item_add),
            suggestions = uiState.itemSuggestions[kind].orEmpty().map { OrbitSuggestion(it) },
            onSuggestion = { actions.pickItem(kind, it.text) },
            onSubmit = { actions.submitItem(kind) },
            inputTransformation = ItemLength,
            modifier = Modifier.fillMaxWidth().focusRequester(focus.requester(PersonField.of(kind))),
        )
    }
}

private val ItemLength = InputTransformation.maxLength(PersonItem.MAX_LENGTH)

/** Input chips: tap one ([onClick]), or its × to remove it. */
@Composable
private fun ChipRow(
    texts: List<String>,
    selectedIndex: Int?,
    onClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    if (texts.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        texts.forEachIndexed { index, text ->
            val removeLabel = stringResource(R.string.common_remove_item, text)
            InputChip(
                selected = index == selectedIndex,
                onClick = { onClick(index) },
                label = { Text(text) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = removeLabel,
                        modifier = Modifier
                            .size(InputChipDefaults.IconSize)
                            .clickable(role = Role.Button) { onRemove(index) },
                    )
                },
            )
        }
    }
}

@Composable
internal fun MoreDetailsButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(stringResource(R.string.person_form_more_details))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
    }
}
