package dev.alializadeh.orbit.person.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import dev.alializadeh.orbit.person.CadenceSetting
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.PersonItem
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactInput
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.dates.DateTypeBadge
import dev.alializadeh.orbit.person.dates.importantDateName
import dev.alializadeh.orbit.person.dates.remindersShort
import dev.alializadeh.orbit.person.detail.AddListItem
import dev.alializadeh.orbit.person.detail.PersonHeroAvatar
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.person.ui.displayName
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.sentenceStart
import dev.alializadeh.orbit.ui.components.OrbitAdaptiveChoice
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.expressive.OrbitAutocompleteField
import dev.alializadeh.orbit.ui.expressive.OrbitSelectField
import dev.alializadeh.orbit.ui.expressive.OrbitSuggestion
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.io.File

/*
 * The form's anatomy matches Person detail's: titled groups of segmented rows on the surface, a
 * field in a row of its own, and one add-affordance, the "+" row (AddListItem), at the end of a
 * group.
 */

/** One row of a [FormGroup]: drawn with its place in the group, for its segmented shape. */
internal class FormItem(val key: Any, val content: @Composable (index: Int, count: Int) -> Unit)

/** A titled group of [items], each with its segmented shape and the Expressive gap between them. */
@Composable
internal fun FormGroup(title: String?, items: List<FormItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier) {
        if (title != null) OrbitSectionTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item -> key(item.key) { item.content(index, items.size) } }
        }
    }
}

/** A group row holding a field (or a field with its chips). */
@Composable
internal fun FieldRow(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

/** The photo slot (the cookie Person detail shows it in) and its "Add photo" / "Change photo" button. */
@Composable
internal fun PhotoSlot(name: String, photo: File?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val displayName = PersonNames(name).displayName(LocalOrbitFormats.current.language)
    Column(modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PersonHeroAvatar(
            name = displayName,
            photo = photo,
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.photo_choose), onClick = onClick),
        )
        TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
            Icon(OrbitIcons.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(if (photo == null) R.string.person_form_photo_add else R.string.person_form_photo_change),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Who they are: Name, then Nickname and Name in other script once they're showing, then
 * "+ Add from contacts" on a new Person.
 */
@Composable
internal fun NameGroup(
    uiState: PersonFormUiState,
    fields: PersonFormFields,
    focus: FormFocus,
    onSave: () -> Unit,
    onAddFromContacts: () -> Unit,
) {
    val items = buildList {
        add(FormItem("name") { index, count -> FieldRow(index, count) { NameField(fields.name, focus.requester(null), onSave) } })
        if (PersonField.Nickname in uiState.visibleFields) {
            add(
                FormItem(PersonField.Nickname) { index, count ->
                    FieldRow(index, count, Modifier.bringIntoViewRequester(focus.bringer(PersonField.Nickname))) {
                        SingleLineField(fields.nickname, stringResource(R.string.person_field_nickname), focus.requester(PersonField.Nickname))
                    }
                },
            )
        }
        if (PersonField.OtherScriptName in uiState.visibleFields) {
            add(
                FormItem(PersonField.OtherScriptName) { index, count ->
                    FieldRow(index, count, Modifier.bringIntoViewRequester(focus.bringer(PersonField.OtherScriptName))) {
                        SingleLineField(
                            fields.otherScriptName,
                            stringResource(R.string.person_field_other_script_name),
                            focus.requester(PersonField.OtherScriptName),
                        )
                    }
                },
            )
        }
        if (uiState.isNew) {
            add(
                FormItem("contacts") { index, count ->
                    SegmentedListItem(
                        onClick = onAddFromContacts,
                        shapes = ListItemDefaults.segmentedShapes(index, count),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        leadingContent = { Icon(OrbitIcons.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.person_form_add_from_contacts), color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
    FormGroup(title = null, items = items, modifier = Modifier.padding(top = 8.dp))
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

/** Ring: the Ring picker, then the Cadence row ("Every 30 days (from ring)"), which opens the Cadence sheet. */
@Composable
internal fun RingSection(uiState: PersonFormUiState, onSelect: (Long) -> Unit, onChangeCadence: () -> Unit) {
    val cadence = when (val setting = uiState.cadence) {
        CadenceSetting.FromRing ->
            stringResource(R.string.person_form_cadence_from_ring, sentenceStart(cadenceText(uiState.selectedRing?.cadence)))
        is CadenceSetting.Own -> sentenceStart(cadenceText(setting.cadence))
    }
    FormGroup(
        title = stringResource(R.string.person_section_ring),
        items = listOf(
            FormItem("ring") { index, count ->
                FieldRow(index, count) {
                    OrbitAdaptiveChoice(
                        options = uiState.rings,
                        selected = uiState.selectedRing,
                        onSelect = { onSelect(it.id) },
                        label = { it.name },
                    )
                }
            },
            FormItem("cadence") { index, count ->
                SegmentedListItem(
                    onClick = onChangeCadence,
                    shapes = ListItemDefaults.segmentedShapes(index, count),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    supportingContent = { Text(cadence) },
                    trailingContent = {
                        Text(
                            stringResource(R.string.person_form_cadence_change),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.person_field_cadence))
                }
            },
        ),
    )
}

/** Contact methods: an editor row each, then "+ Add contact method". */
@Composable
internal fun ContactMethodsSection(uiState: PersonFormUiState, actions: PersonFormActions, modifier: Modifier = Modifier) {
    val items = uiState.contacts.map { row ->
        FormItem(row.key) { index, count -> ContactRowEditor(row, autoFocus = row.key == uiState.newContactKey, actions, index, count) }
    } + FormItem("add") { index, count ->
        AddListItem(stringResource(R.string.person_form_contact_add), index, count, actions::addContact)
    }
    FormGroup(stringResource(R.string.person_section_contact_methods), items, modifier)
}

@Composable
private fun ContactRowEditor(row: ContactRow, autoFocus: Boolean, actions: PersonFormActions, index: Int, count: Int) {
    val valueFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { valueFocus.requestFocus() } }
    FieldRow(index, count) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.person_form_contact_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

/**
 * Important dates: the Birthday up front (its row, or "+ Add birthday"); the other dates and
 * "+ Add important date" once they're showing (behind More details).
 */
@Composable
internal fun ImportantDatesSection(uiState: PersonFormUiState, actions: PersonFormActions, focus: FormFocus) {
    val birthday = uiState.birthday
    val showsOthers = PersonField.ImportantDates in uiState.visibleFields
    val birthdayModifier = Modifier.bringIntoViewRequester(focus.bringer(PersonField.Birthday))
    val othersModifier = Modifier.bringIntoViewRequester(focus.bringer(PersonField.ImportantDates))
    val items = buildList {
        add(
            if (birthday != null) {
                FormItem(birthday.key) { index, count ->
                    DateRowItem(birthday, index, count, birthdayModifier, onClick = { actions.editImportantDate(birthday.key) })
                }
            } else {
                FormItem("add_birthday") { index, count ->
                    Box(birthdayModifier) {
                        AddListItem(stringResource(R.string.person_form_birthday_add), index, count) {
                            actions.addImportantDate(ImportantDateType.Birthday)
                        }
                    }
                }
            },
        )
        if (showsOthers) {
            uiState.otherDates.forEach { row ->
                add(FormItem(row.key) { index, count -> DateRowItem(row, index, count, onClick = { actions.editImportantDate(row.key) }) })
            }
            add(
                FormItem("add_date") { index, count ->
                    Box(othersModifier) {
                        AddListItem(stringResource(R.string.person_form_important_date_add), index, count) {
                            actions.addImportantDate(ImportantDateType.Anniversary)
                        }
                    }
                },
            )
        }
    }
    FormGroup(stringResource(R.string.person_section_important_dates), items)
}

/** One date as entered: "Birthday" over "14 Farvardin 1370 · default reminders". Tapping opens its sheet. */
@Composable
private fun DateRowItem(row: DateRow, index: Int, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val formats = LocalOrbitFormats.current
    val draft = row.draft
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { DateTypeBadge(draft.type) },
        supportingContent = {
            Text(listOf(formats.dates.format(draft.date), remindersShort(draft.leadTimes)).joinToString(stringResource(R.string.separator)))
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(importantDateName(draft.type, draft.label?.let(::bidiIsolate)))
    }
}

/**
 * The fields behind "More details" that live in their own groups (Tags, Can help with, Likes,
 * Dislikes, How we met and Notes), once showing, in the form's order. Nickname, Name in other
 * script and the other Important dates join their groups above.
 */
@Composable
internal fun MoreDetailFields(
    uiState: PersonFormUiState,
    fields: PersonFormFields,
    actions: PersonFormActions,
    focus: FormFocus,
) {
    val visible = uiState.visibleFields
    if (PersonField.Tags in visible) {
        ChipsGroup(
            title = stringResource(R.string.person_section_tags),
            modifier = Modifier.bringIntoViewRequester(focus.bringer(PersonField.Tags)),
        ) {
            ChipRow(
                texts = uiState.tags,
                selectedIndex = null,
                onClick = { index -> actions.removeTag(uiState.tags[index]) },
                onRemove = { index -> actions.removeTag(uiState.tags[index]) },
            )
            OrbitAutocompleteField(
                state = fields.tagInput,
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
                modifier = Modifier.fillMaxWidth().focusRequester(focus.requester(PersonField.Tags)),
            )
        }
    }
    listOf(PersonItemKind.CanHelpWith, PersonItemKind.Like, PersonItemKind.Dislike).forEach { kind ->
        val field = PersonField.of(kind)
        if (field in visible) {
            val items = uiState.items[kind].orEmpty()
            ChipsGroup(stringResource(kind.titleRes), Modifier.bringIntoViewRequester(focus.bringer(field))) {
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
                    modifier = Modifier.fillMaxWidth().focusRequester(focus.requester(field)),
                )
            }
        }
    }
    // How we met and Notes: one group of the two free-text fields, each labelled by its field.
    val texts = listOfNotNull(
        (PersonField.HowWeMet to fields.howWeMet).takeIf { PersonField.HowWeMet in visible },
        (PersonField.Notes to fields.notes).takeIf { PersonField.Notes in visible },
    )
    FormGroup(
        title = null,
        items = texts.map { (field, state) ->
            FormItem(field) { index, count ->
                FieldRow(index, count, Modifier.bringIntoViewRequester(focus.bringer(field))) {
                    MultiLineField(
                        state = state,
                        label = stringResource(if (field == PersonField.HowWeMet) R.string.person_section_how_we_met else R.string.person_section_notes),
                        focusRequester = focus.requester(field),
                    )
                }
            }
        },
        modifier = Modifier.padding(top = 24.dp),
    )
}

/** A titled group of one row: the chips entered so far, then the field that adds one. */
@Composable
private fun ChipsGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FormGroup(
        title = title,
        items = listOf(FormItem(title) { index, count -> FieldRow(index, count) { Column { content() } } }),
        modifier = modifier,
    )
}

@Composable
private fun SingleLineField(state: TextFieldState, label: String, focusRequester: FocusRequester) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Composable
private fun MultiLineField(state: TextFieldState, label: String, focusRequester: FocusRequester) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
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

/** "More details ⌄" with what's behind it: a group row of its own, revealing the rest of the form. */
@Composable
internal fun MoreDetailsButton(onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        supportingContent = { Text(stringResource(R.string.person_form_more_details_summary)) },
        trailingContent = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Text(stringResource(R.string.person_form_more_details), color = MaterialTheme.colorScheme.primary)
    }
}
