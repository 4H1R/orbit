package dev.alializadeh.orbit.person

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import java.time.Instant
import java.time.LocalDate

/**
 * Someone the user keeps in Orbit. Only the [name] is required.
 *
 * Their Likes, Dislikes and Can help with ([PersonItem]), Tags ([PersonTag]) and Contact methods
 * ([ContactMethod]) live in their own tables and go when the Person is deleted. The photo is a file
 * in app storage (see `PhotoStore`); only its name is kept here.
 */
@Entity(
    tableName = "person",
    foreignKeys = [
        // A Ring holding People can't be removed until they're moved (RESTRICT).
        ForeignKey(
            entity = Ring::class,
            parentColumns = ["id"],
            childColumns = ["ringId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("ringId")],
)
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String? = null,
    /** The Person's name in the other script (Latin or Persian), shown as a subtitle. */
    val otherScriptName: String? = null,
    /** The photo's file name in `filesDir/photos`, or null for the initials avatar. */
    val photoFileName: String? = null,
    val howWeMet: String? = null,
    val notes: String? = null,
    val ringId: Long,
    /**
     * Whether the Person overrides their Ring's Cadence. When true, [ownCadence] is their Cadence
     * and null means "No Cadence"; when false, the Ring's Cadence applies. Read both through
     * [cadenceSetting].
     */
    val hasOwnCadence: Boolean = false,
    @Embedded(prefix = "own_cadence_") val ownCadence: Cadence? = null,
    /** Set while the Person is Archived. */
    val archivedAt: Instant? = null,
    val createdAt: Instant,
    /**
     * Snooze: while a Due Person's [snoozedUntil] is after today they aren't Due; they're Due again
     * on that day (if still behind on their Cadence). It's not an Interaction and doesn't change the
     * Cadence. Read through `KeepInTouch`.
     */
    val snoozedUntil: LocalDate? = null,
    /**
     * The day the Cadence digest last named them as Due (slice 6): they're notified once per Due
     * period, so a marker on or after the period's start means "already notified". `reconcile()`
     * clears it while they aren't Due, so the next time they become Due is a new period.
     */
    val lastNotifiedDueOn: LocalDate? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** Where a Person's Cadence comes from. */
sealed interface CadenceSetting {
    /** Their Ring's default Cadence. */
    data object FromRing : CadenceSetting

    /** Their own: every [cadence], or "No Cadence" when null. */
    data class Own(val cadence: Cadence?) : CadenceSetting
}

val Person.cadenceSetting: CadenceSetting
    get() = if (hasOwnCadence) CadenceSetting.Own(ownCadence) else CadenceSetting.FromRing

/** The Cadence that applies to this Person in [ring]; null is "No Cadence". */
fun Person.cadenceIn(ring: Ring?): Cadence? = when (val setting = cadenceSetting) {
    CadenceSetting.FromRing -> ring?.cadence
    is CadenceSetting.Own -> setting.cadence
}

fun Person.withCadenceSetting(setting: CadenceSetting): Person = when (setting) {
    CadenceSetting.FromRing -> copy(hasOwnCadence = false, ownCadence = null)
    is CadenceSetting.Own -> copy(hasOwnCadence = true, ownCadence = setting.cadence)
}
