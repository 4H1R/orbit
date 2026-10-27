package dev.alializadeh.orbit.person.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.bidiIsolateLtr
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonProfile
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.contact.ContactInput
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.person.ui.displayName
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.icons.OrbitIcons

@Composable
internal fun ArchivedBanner(onUnarchive: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.person_detail_archived_banner),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUnarchive) { Text(stringResource(R.string.person_detail_unarchive)) }
        }
    }
}

/** Photo, names, Ring and Tag chips, and the contact button group. */
@Composable
internal fun PersonHeader(
    uiState: PersonDetailUiState.Loaded,
    onContactAction: (ContactAction) -> Unit,
    onMoreContact: () -> Unit,
) {
    val profile = uiState.profile
    val language = LocalOrbitFormats.current.language
    val separator = stringResource(R.string.separator)
    val displayName = profile.names.displayName(language)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        PersonAvatar(
            name = displayName,
            photo = uiState.photo,
            size = 128.dp,
            shape = OrbitShapes.cookie(),
            textStyle = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        val subtitle = profile.names.subtitleParts(language)
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle.joinToString(separator) { bidiIsolate(it) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            profile.ring?.let { ring ->
                LabelChip(ring.name, icon = OrbitIcons.Orbit, emphasised = true)
            }
            profile.tags.forEach { LabelChip(it.name) }
        }
        ContactButtons(profile, onContactAction, onMoreContact, Modifier.padding(top = 8.dp))
    }
}

/**
 * The connected button group: [primary contact action] [More contact ▾]. More contact only shows
 * when there are other Contact methods. Slice 4 puts [Log interaction] between the two.
 */
@Composable
private fun ContactButtons(
    profile: PersonProfile,
    onContactAction: (ContactAction) -> Unit,
    onMoreContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryAction = profile.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    val hasMore = profile.contactMethods.size > 1 || (primaryAction == null && profile.contactMethods.isNotEmpty())
    val buttons = listOfNotNull(
        primaryAction?.let { action ->
            ButtonSpec(action.kind.label(), action.kind.icon, filled = true) { onContactAction(action) }
        },
        // Slice 4: Log interaction.
        if (hasMore) {
            ButtonSpec(stringResource(R.string.person_detail_more_contact), Icons.Filled.ArrowDropDown, filled = false, iconAfter = true, onClick = onMoreContact)
        } else {
            null
        },
    )
    if (buttons.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        buttons.forEachIndexed { index, button ->
            val shape: Shape = when {
                buttons.size == 1 -> ButtonDefaults.shape
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShape
                index == buttons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShape
                else -> ButtonDefaults.shape
            }
            val content: @Composable () -> Unit = {
                if (!button.iconAfter) Icon(button.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(button.label, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
                if (button.iconAfter) Icon(button.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            val buttonModifier = Modifier.weight(1f)
            if (button.filled) {
                Button(onClick = button.onClick, shape = shape, modifier = buttonModifier) { content() }
            } else {
                FilledTonalButton(onClick = button.onClick, shape = shape, modifier = buttonModifier) { content() }
            }
        }
    }
}

private class ButtonSpec(
    val label: String,
    val icon: ImageVector,
    val filled: Boolean,
    val iconAfter: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The sections in order: Important dates (when there are any), Gift ideas (always), then the
 * others that have content.
 */
@Composable
internal fun PersonSections(
    uiState: PersonDetailUiState.Loaded,
    onOpenDate: (ImportantDate) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (GiftIdea) -> Unit,
    onMarkGiven: (GiftIdea) -> Unit,
    onOpenLink: (String) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    val profile = uiState.profile
    ImportantDatesSection(profile.importantDates, uiState.today, onOpenDate)
    GiftIdeasSection(
        open = profile.openGiftIdeas,
        given = profile.givenGiftIdeas,
        today = uiState.today,
        onAdd = onAddGiftIdea,
        onOpen = onOpenGiftIdea,
        onMarkGiven = onMarkGiven,
        onOpenLink = onOpenLink,
    )
    if (profile.contactMethods.isNotEmpty()) {
        Column {
            OrbitSectionTitle(stringResource(R.string.person_section_contact_methods), horizontalPadding = 4.dp)
            ContactMethodList(profile.contactMethods, onContactAction, onCopy)
        }
    }
    listOf(PersonItemKind.CanHelpWith, PersonItemKind.Like, PersonItemKind.Dislike).forEach { kind ->
        val items = profile.items(kind)
        if (items.isNotEmpty()) {
            Column {
                OrbitSectionTitle(stringResource(kind.titleRes), horizontalPadding = 4.dp)
                // Slice 8: tapping a chip searches People for its text.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { LabelChip(it) }
                }
            }
        }
    }
    profile.person.howWeMet?.let { TextSection(stringResource(R.string.person_section_how_we_met), it) }
    profile.person.notes?.let { TextSection(stringResource(R.string.person_section_notes), it) }
    // Slice 4: Interactions.
}

@Composable
private fun ContactMethodList(
    methods: List<ContactMethod>,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        methods.forEachIndexed { index, method ->
            ContactMethodRow(method, index, methods.size, onContactAction, onCopy)
        }
    }
}

/** A Contact method with its action buttons. Tapping runs the first action; long-press copies the value. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactMethodRow(
    method: ContactMethod,
    index: Int,
    count: Int,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    val actions = ContactActions.of(method)
    val separator = stringResource(R.string.separator)
    val supporting = listOfNotNull(
        method.type.displayName(),
        method.label?.let(::bidiIsolate),
        if (method.isPrimary) stringResource(R.string.person_form_contact_primary) else null,
    ).joinToString(separator)
    Surface(
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    onClick = { actions.firstOrNull()?.let(onContactAction) ?: onCopy(method.value) },
                    onLongClick = { onCopy(method.value) },
                )
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(method.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                // Values are shown as entered, left to right in both languages; an Address follows
                // its own script.
                val value = if (method.type.input == ContactInput.Text) bidiIsolate(method.value) else bidiIsolateLtr(method.value)
                Text(value, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (method.isPrimary) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            actions.forEach { action ->
                IconButton(onClick = { onContactAction(action) }) {
                    Icon(action.kind.icon, contentDescription = action.kind.label())
                }
            }
        }
    }
}

@Composable
internal fun MoreContactSheet(
    methods: List<ContactMethod>,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ContactMethodList(
                methods = methods,
                onContactAction = { action -> sheet.close { onContactAction(action) } },
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun TextSection(title: String, text: String) {
    Column {
        OrbitSectionTitle(title, horizontalPadding = 4.dp)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
}

/** Add details: a chip per empty field, each opening Edit at that field. */
@Composable
internal fun AddDetails(missing: List<PersonField>, onAddDetail: (PersonField) -> Unit) {
    if (missing.isEmpty()) return
    Column {
        OrbitSectionTitle(stringResource(R.string.person_detail_add_details), horizontalPadding = 4.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            missing.forEach { field ->
                AssistChip(
                    onClick = { onAddDetail(field) },
                    label = { Text(fieldName(field)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun fieldName(field: PersonField): String = stringResource(
    when (field) {
        PersonField.Photo -> R.string.person_field_photo
        PersonField.ContactMethods -> R.string.person_field_contact_method
        PersonField.Birthday -> R.string.important_date_type_birthday
        PersonField.ImportantDates -> R.string.person_field_important_date
        PersonField.Nickname -> R.string.person_field_nickname
        PersonField.OtherScriptName -> R.string.person_field_other_script_name
        PersonField.Tags -> R.string.person_section_tags
        PersonField.CanHelpWith -> R.string.person_section_can_help_with
        PersonField.Likes -> R.string.person_section_likes
        PersonField.Dislikes -> R.string.person_section_dislikes
        PersonField.HowWeMet -> R.string.person_section_how_we_met
        PersonField.Notes -> R.string.person_section_notes
    },
)

/** A chip that only shows a value (Ring, Tags, Likes…); not tappable yet. */
@Composable
private fun LabelChip(text: String, icon: ImageVector? = null, emphasised: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (emphasised) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (emphasised) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
