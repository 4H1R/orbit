package dev.alializadeh.orbit.testing

import androidx.compose.ui.graphics.ImageBitmap
import dev.alializadeh.orbit.person.photo.CropImage
import dev.alializadeh.orbit.person.photo.CropRect
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.interaction.Interaction
import dev.alializadeh.orbit.interaction.InteractionDraft
import dev.alializadeh.orbit.interaction.InteractionRepository
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.interaction.InteractionWithPeople
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.TagRepository
import dev.alializadeh.orbit.person.TagWithCount
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingWithCount
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class FakeRingRepository(rings: List<Ring> = TestRings.all) : RingRepository {
    val rings = MutableStateFlow(rings)

    /** How many People each Ring holds, by Ring id. */
    val peopleCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    override fun observeRings(): Flow<List<Ring>> = rings

    override fun observeRingsWithCounts(): Flow<List<RingWithCount>> = combine(rings, peopleCounts) { rings, counts ->
        rings.sortedBy(Ring::position).map { RingWithCount(it, counts[it.id] ?: 0) }
    }

    override suspend fun add(name: String, cadence: Cadence?): Long {
        require(rings.value.size < Ring.MAX_COUNT)
        val id = (rings.value.maxOfOrNull(Ring::id) ?: 0) + 1
        rings.value += Ring(id = id, name = name.trim(), position = rings.value.size, cadence = cadence)
        return id
    }

    override suspend fun update(id: Long, name: String, cadence: Cadence?) {
        rings.value = rings.value.map { if (it.id == id) it.copy(name = name.trim(), cadence = cadence) else it }
    }

    override suspend fun movePeople(fromId: Long, toId: Long) {
        val counts = peopleCounts.value
        peopleCounts.value = counts + (toId to (counts[toId] ?: 0) + (counts[fromId] ?: 0)) + (fromId to 0)
    }

    override suspend fun remove(id: Long) {
        require(rings.value.size > 1 && (peopleCounts.value[id] ?: 0) == 0)
        rings.value = rings.value.filter { it.id != id }.sortedBy(Ring::position).mapIndexed { i, ring -> ring.copy(position = i) }
    }

    override suspend fun reorder(ids: List<Long>) {
        rings.value = ids.mapIndexed { position, id -> rings.value.first { it.id == id }.copy(position = position) }
    }
}

/** An in-memory [InteractionRepository] over [people]'s Persons, keeping the Room one's rules. */
class FakeInteractionRepository(
    private val people: FakePersonRepository,
    private val clock: Clock,
) : InteractionRepository {

    /** Every Interaction with its People's ids, by id. */
    val interactions = MutableStateFlow<Map<Long, Pair<Interaction, List<Long>>>>(emptyMap())
    private var nextId = 1L

    private fun withPeople(entry: Pair<Interaction, List<Long>>, persons: Map<Long, Person>) =
        InteractionWithPeople(entry.first, entry.second.mapNotNull(persons::get))

    override fun observeForPerson(personId: Long): Flow<List<InteractionWithPeople>> =
        combine(interactions, people.profiles) { all, profiles ->
            val persons = profiles.mapValues { it.value.person }
            all.values
                .filter { personId in it.second }
                .sortedWith(compareByDescending<Pair<Interaction, List<Long>>> { it.first.date }.thenByDescending { it.first.id })
                .map { withPeople(it, persons) }
        }

    override fun observeInteraction(id: Long): Flow<InteractionWithPeople?> =
        combine(interactions, people.profiles) { all, profiles -> all[id]?.let { withPeople(it, profiles.mapValues { p -> p.value.person }) } }

    override suspend fun save(draft: InteractionDraft): Long {
        val d = draft.normalised()
        require(d.personIds.isNotEmpty())
        require(!d.date.isAfter(LocalDate.now(clock)))
        val id = d.id?.takeIf { it in interactions.value } ?: nextId++
        interactions.value += id to (Interaction(id = id, type = d.type, date = d.date, note = d.note) to d.personIds)
        return id
    }

    override suspend fun delete(id: Long) {
        interactions.value -= id
    }

    override suspend fun markAsContacted(personId: Long): Long =
        save(InteractionDraft(type = InteractionType.Other, date = LocalDate.now(clock), personIds = listOf(personId)))
}

/** An in-memory [TagRepository] with the Room one's rules (names unique ignoring case). */
class FakeTagRepository(tags: List<TagWithCount> = emptyList()) : TagRepository {
    val tags = MutableStateFlow(tags)

    override fun observeTags(): Flow<List<TagWithCount>> = tags

    override suspend fun find(name: String): Tag? = tags.value.map(TagWithCount::tag).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    override suspend fun rename(id: Long, name: String) {
        val clash = find(name)
        require(clash == null || clash.id == id)
        tags.value = tags.value.map { if (it.tag.id == id) it.copy(tag = it.tag.copy(name = name.trim())) else it }
    }

    /** Counts can't tell who had both, so the merged count is the larger one here. */
    override suspend fun merge(sourceId: Long, targetId: Long) {
        val source = tags.value.first { it.tag.id == sourceId }
        tags.value = tags.value.filter { it.tag.id != sourceId }
            .map { if (it.tag.id == targetId) it.copy(peopleCount = maxOf(it.peopleCount, source.peopleCount)) else it }
    }

    override suspend fun delete(id: Long) {
        tags.value = tags.value.filter { it.tag.id != id }
    }
}

/** A [PhotoStore] that only keeps file names: [files] are the photos that exist. */
class FakePhotoStore : PhotoStore {
    val files = mutableSetOf<String>()
    private var next = 1

    /** What [loadForCrop] returns; null makes the photo unreadable. */
    var image: CropImage? = FakeCropImage(1200, 800)

    override fun file(fileName: String): File = File("photos", fileName)

    override suspend fun loadForCrop(uri: String): CropImage? = image

    override suspend fun saveCrop(image: CropImage, crop: CropRect): String =
        "photo-${next++}.jpg".also { files += it }

    override fun delete(fileName: String) {
        files -= fileName
    }
}

class FakeCropImage(override val width: Int, override val height: Int) : CropImage {
    override fun imageBitmap(): ImageBitmap = error("Not drawn in JVM tests")
}

class FakeUpcomingRepository(people: List<UpcomingPerson> = emptyList()) : UpcomingRepository {
    val people = MutableStateFlow(people)

    override fun observePeople(): Flow<List<UpcomingPerson>> = this.people
}

/** A [Clock] whose instant tests move by hand. */
class MutableClock(var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    fun advance(duration: Duration) {
        instant += duration
    }
}
