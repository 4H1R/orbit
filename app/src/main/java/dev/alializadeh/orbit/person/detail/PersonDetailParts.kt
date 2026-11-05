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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.contact.ContactInput
import dev.alializadeh.orbit.person.ui.displayName
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.OrbitSheetTitle
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet

@Composable
internal fun ArchivedBanner(onUnarchive: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
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

/**
 * Who this is: the photo in its cookie, the name (the page's one headline), the other-script name
 * and nickname, and the Tags. [nameModifier] lets the screen see when the name scrolls away.
 */
@Composable
internal fun PersonHeader(uiState: PersonDetailUiState.Loaded, nameModifier: Modifier = Modifier) {
    val profile = uiState.profile
    val language = LocalOrbitFormats.current.language
    val separator = stringResource(R.string.separator)
    val displayName = profile.names.displayName(language)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        PersonHeroAvatar(name = displayName, photo = uiState.photo)
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = nameModifier.padding(top = 16.dp).semantics { heading() },
        )
        val subtitle = profile.names.subtitleParts(language)
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle.joinToString(separator) { bidiIsolate(it) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (profile.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                profile.tags.forEach { LabelChip(it.name) }
            }
        }
    }
}

/**
 * The sections under the keep-in-touch card, in the order the page answers questions.
 *
 * What to remember, managed here in place: open Follow-ups and Loans first (what to bring up when
 * you talk), Important dates, Gift ideas, then Follow-ups and Loans with nothing open. Each ends
 * with its "+" row; the ones with nothing in them at all become one group of "+ Birthday",
 * "+ Gift idea", "+ Follow-up" and "+ Lent or borrowed" rows instead of sections holding only an
 * add row.
 *
 * Then the details, edited on the form: Contact methods, Can help with, Likes, Dislikes, How we
 * met, Notes; then Interactions (when there are any).
 */
@Composable
internal fun PersonSections(
    uiState: PersonDetailUiState.Loaded,
    keepInTouch: PersonDetailKeepInTouch,
    onOpenDate: (ImportantDate) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (GiftIdea) -> Unit,
    onMarkGiven: (GiftIdea) -> Unit,
    onOpenLink: (String) -> Unit,
    onContactAction: (ContactAction) -> Unit,
    onCopy: (String) -> Unit,
    onSearchPeople: (String) -> Unit,
    onSendWishes: (dateId: Long) -> Unit = {},
    onAddDate: () -> Unit = {},
    onAddFollowUp: () -> Unit = {},
    onOpenFollowUp: (id: Long) -> Unit = {},
    onMarkFollowUpDone: (id: Long) -> Unit = {},
    onAddLoan: () -> Unit = {},
    onOpenLoan: (id: Long) -> Unit = {},
    onMarkLoanReturned: (id: Long) -> Unit = {},
) {
    val profile = uiState.profile
    val followUps = @Composable {
        FollowUpsSection(profile.openFollowUps, profile.doneFollowUps, uiState.today, onAddFollowUp, onOpenFollowUp, onMarkFollowUpDone)
    }
    val loans = @Composable {
        LoansSection(profile.openLoans, profile.returnedLoans, uiState.today, onAddLoan, onOpenLoan, onMarkLoanReturned)
    }
    val noGiftIdeas = profile.openGiftIdeas.isEmpty() && profile.givenGiftIdeas.isEmpty()
    val noFollowUps = profile.openFollowUps.isEmpty() && profile.doneFollowUps.isEmpty()
    val noLoans = profile.openLoans.isEmpty() && profile.returnedLoans.isEmpty()
    val addDateLabel = stringResource(if (profile.birthday == null) R.string.important_date_type_birthday else R.string.person_field_important_date)
    if (profile.openFollowUps.isNotEmpty()) followUps()
    if (profile.openLoans.isNotEmpty()) loans()
    // An Archived Person is left out of Upcoming and Reminders, so they get no Wishes either.
    ImportantDatesSection(
        dates = profile.importantDates,
        today = uiState.today,
        onOpen = onOpenDate,
        addLabel = addDateLabel,
        onAdd = onAddDate,
        onSendWishes = if (profile.person.isArchived) null else onSendWishes,
    )
    if (!noGiftIdeas) {
        GiftIdeasSection(
            open = profile.openGiftIdeas,
            given = profile.givenGiftIdeas,
            today = uiState.today,
            onAdd = onAddGiftIdea,
            onOpen = onOpenGiftIdea,
            onMarkGiven = onMarkGiven,
            onOpenLink = onOpenLink,
        )
    }
    if (profile.openFollowUps.isEmpty() && !noFollowUps) followUps()
    if (profile.openLoans.isEmpty() && !noLoans) loans()
    AddRows(
        listOfNotNull(
            if (profile.importantDates.isEmpty()) AddRow(addDateLabel, onAddDate) else null,
            if (noGiftIdeas) AddRow(stringResource(R.string.gift_idea_add), onAddGiftIdea) else null,
            if (noFollowUps) AddRow(stringResource(R.string.follow_up_add), onAddFollowUp) else null,
            if (noLoans) AddRow(stringResource(R.string.loan_add), onAddLoan) else null,
        ),
    )
    if (profile.contactMethods.isNotEmpty()) {
        Column {
            OrbitSectionTitle(stringResource(R.string.person_section_contact_methods))
            ContactMethodList(profile.contactMethods, onContactAction, onCopy)
        }
    }
    listOf(PersonItemKind.CanHelpWith, PersonItemKind.Like, PersonItemKind.Dislike).forEach { kind ->
        val items = profile.items(kind)
        if (items.isNotEmpty()) {
            Column {
                OrbitSectionTitle(stringResource(kind.titleRes))
                // Tapping a chip searches People for its text ("Who else likes jazz?").
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    items.forEach { item -> LabelChip(item, onClick = { onSearchPeople(item) }) }
                }
            }
        }
    }
    profile.person.howWeMet?.let { TextSection(stringResource(R.string.person_section_how_we_met), it) }
    profile.person.notes?.let { TextSection(stringResource(R.string.person_section_notes), it) }
    InteractionsSection(
        personId = profile.person.id,
        interactions = uiState.interactions,
        today = uiState.today,
        onOpen = keepInTouch::openInteraction,
        onAll = keepInTouch::allInteractions,
    )
}

private class AddRow(val label: String, val onClick: () -> Unit)

/** One group of "+ …" rows for the empty Important dates, Gift ideas, Follow-ups and Loans. */
@Composable
private fun AddRows(rows: List<AddRow>) {
    if (rows.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.padding(top = 24.dp),
    ) {
        rows.forEachIndexed { index, row -> AddListItem(row.label, index, rows.size, row.onClick) }
    }
}

/**
 * A "+ Gift idea" row: the one add-affordance of Person detail's groups, at the end of a section
 * or in the group of empty ones.
 */
@Composable
internal fun AddListItem(label: String, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary)
    }
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
            Icon(method.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            OrbitSheetTitle(
                stringResource(R.string.person_section_contact_methods),
                Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
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
        OrbitSectionTitle(title)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
}

/**
 * Add details: a chip per empty detail, each opening Edit at that field. Important dates aren't
 * among them: they're added here, with the "+ Birthday" / "+ Important date" row.
 */
@Composable
internal fun AddDetails(missing: List<PersonField>, onAddDetail: (PersonField) -> Unit) {
    val fields = missing.filter { it != PersonField.Birthday && it != PersonField.ImportantDates }
    if (fields.isEmpty()) return
    Column {
        OrbitSectionTitle(stringResource(R.string.person_detail_add_details))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            fields.forEach { field ->
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

/** An outlined chip showing a value (Tags); with [onClick], a tappable one (Likes… search People). */
@Composable
private fun LabelChip(text: String, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val shape = MaterialTheme.shapes.small
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = Color.Transparent, border = border) { LabelChipContent(text, icon) }
    } else {
        Surface(shape = shape, color = Color.Transparent, border = border) { LabelChipContent(text, icon) }
    }
}

@Composable
private fun LabelChipContent(text: String, icon: ImageVector?) {
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
