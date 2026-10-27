package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Ring

/** A Person with everything Person detail shows, items and Tags in their order. */
data class PersonProfile(
    val person: Person,
    /** Null only for a moment while a Ring change is being written. */
    val ring: Ring?,
    val tags: List<Tag>,
    val contactMethods: List<ContactMethod>,
    val canHelpWith: List<String>,
    val likes: List<String>,
    val dislikes: List<String>,
) {
    val names: PersonNames get() = person.names

    val primaryContactMethod: ContactMethod? get() = contactMethods.firstOrNull(ContactMethod::isPrimary)

    fun items(kind: PersonItemKind): List<String> = when (kind) {
        PersonItemKind.CanHelpWith -> canHelpWith
        PersonItemKind.Like -> likes
        PersonItemKind.Dislike -> dislikes
    }

    companion object {
        fun of(details: PersonWithDetails, ring: Ring?): PersonProfile {
            val tagPositions = details.personTags.associate { it.tagId to it.position }
            val items = details.items.sortedBy(PersonItem::position).groupBy(PersonItem::kind)
            fun texts(kind: PersonItemKind) = items[kind].orEmpty().map(PersonItem::text)
            return PersonProfile(
                person = details.person,
                ring = ring,
                tags = details.tags.sortedBy { tagPositions[it.id] ?: Int.MAX_VALUE },
                contactMethods = details.contactMethods.sortedBy(ContactMethod::position),
                canHelpWith = texts(PersonItemKind.CanHelpWith),
                likes = texts(PersonItemKind.Like),
                dislikes = texts(PersonItemKind.Dislike),
            )
        }
    }
}

/** A Contact method as the form holds it, before it has an id. */
data class ContactMethodDraft(
    val type: ContactType,
    val value: String,
    val label: String? = null,
    val isPrimary: Boolean = false,
)

/**
 * Everything the Add/Edit form saves: a new Person when [id] is null. [normalised] is what gets
 * stored, and two drafts with equal normalised forms mean "no changes".
 */
data class PersonDraft(
    val id: Long? = null,
    val name: String,
    val nickname: String? = null,
    val otherScriptName: String? = null,
    val photoFileName: String? = null,
    val howWeMet: String? = null,
    val notes: String? = null,
    val ringId: Long?,
    val cadence: CadenceSetting = CadenceSetting.FromRing,
    val tags: List<String> = emptyList(),
    val contactMethods: List<ContactMethodDraft> = emptyList(),
    val canHelpWith: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
) {
    fun items(kind: PersonItemKind): List<String> = when (kind) {
        PersonItemKind.CanHelpWith -> canHelpWith
        PersonItemKind.Like -> likes
        PersonItemKind.Dislike -> dislikes
    }

    /**
     * Text trimmed and blanks dropped; Contact methods without a value dropped, phone digits as
     * ASCII, and exactly one primary when there are any (the first starred one, else the first);
     * Tags without repeats (ignoring case).
     */
    fun normalised(): PersonDraft = copy(
        name = name.trim(),
        nickname = nickname.orNullIfBlank(),
        otherScriptName = otherScriptName.orNullIfBlank(),
        howWeMet = howWeMet.orNullIfBlank(),
        notes = notes.orNullIfBlank(),
        tags = tags.map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase),
        contactMethods = contactMethods
            .map { it.copy(value = it.type.normalise(it.value), label = it.label.orNullIfBlank()) }
            .filter { it.value.isNotEmpty() }
            .withOnePrimary(),
        canHelpWith = canHelpWith.normalisedItems(),
        likes = likes.normalisedItems(),
        dislikes = dislikes.normalisedItems(),
    )

    companion object {
        fun of(profile: PersonProfile): PersonDraft = PersonDraft(
            id = profile.person.id,
            name = profile.person.name,
            nickname = profile.person.nickname,
            otherScriptName = profile.person.otherScriptName,
            photoFileName = profile.person.photoFileName,
            howWeMet = profile.person.howWeMet,
            notes = profile.person.notes,
            ringId = profile.person.ringId,
            cadence = profile.person.cadenceSetting,
            tags = profile.tags.map(Tag::name),
            contactMethods = profile.contactMethods.map {
                ContactMethodDraft(type = it.type, value = it.value, label = it.label, isPrimary = it.isPrimary)
            },
            canHelpWith = profile.canHelpWith,
            likes = profile.likes,
            dislikes = profile.dislikes,
        )
    }
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun List<String>.normalisedItems(): List<String> =
    map { it.trim().take(PersonItem.MAX_LENGTH) }.filter(String::isNotEmpty)

private fun List<ContactMethodDraft>.withOnePrimary(): List<ContactMethodDraft> {
    if (isEmpty()) return this
    val primaryIndex = indexOfFirst(ContactMethodDraft::isPrimary).coerceAtLeast(0)
    return mapIndexed { index, method -> method.copy(isPrimary = index == primaryIndex) }
}
