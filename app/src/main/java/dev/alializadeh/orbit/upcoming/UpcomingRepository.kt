package dev.alializadeh.orbit.upcoming

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.due.keepInTouch
import dev.alializadeh.orbit.interaction.InteractionDao
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.FollowUp
import dev.alializadeh.orbit.person.FollowUpOrder
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.GiftIdeaOrder
import dev.alializadeh.orbit.person.ImportantDateOrder
import dev.alializadeh.orbit.person.Loan
import dev.alializadeh.orbit.person.LoanOrder
import dev.alializadeh.orbit.person.PersonDao
import dev.alializadeh.orbit.person.names
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingDao
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What Upcoming is computed from. */
interface UpcomingRepository {
    /**
     * Every Person (Archived ones too, flagged) with their Important dates, open Gift ideas and
     * what Due is worked out from.
     */
    fun observePeople(): Flow<List<UpcomingPerson>>
}

class RoomUpcomingRepository @Inject constructor(
    private val personDao: PersonDao,
    private val ringDao: RingDao,
    private val interactionDao: InteractionDao,
    private val clock: Clock,
) : UpcomingRepository {

    override fun observePeople(): Flow<List<UpcomingPerson>> =
        combine(personDao.observeWithDates(), ringDao.observeAll(), interactionDao.observeLastDates()) { people, rings, last ->
            val ringsById = rings.associateBy(Ring::id)
            val lastDates = last.associate { it.personId to it.date }
            people.map { row ->
                val ring = ringsById[row.person.ringId]
                UpcomingPerson(
                    id = row.person.id,
                    names = row.person.names,
                    photoFileName = row.person.photoFileName,
                    isArchived = row.person.isArchived,
                    primaryContactMethod = row.contactMethods.firstOrNull(ContactMethod::isPrimary),
                    importantDates = row.importantDates.sortedWith(ImportantDateOrder),
                    openGiftIdeas = row.giftIdeas.filterNot(GiftIdea::isGiven).sortedWith(GiftIdeaOrder).map(GiftIdea::text),
                    keepInTouch = row.person.keepInTouch(ring, lastDates[row.person.id], clock.zone),
                    ringName = ring?.name,
                    ringPosition = ring?.position ?: Int.MAX_VALUE,
                    ringId = row.person.ringId,
                    tagIds = row.tags.map { it.tagId }.toSet(),
                    openFollowUps = row.followUps.filterNot(FollowUp::isDone).sortedWith(FollowUpOrder),
                    openLoans = row.loans.filterNot(Loan::isReturned).sortedWith(LoanOrder),
                )
            }
        }
}

@Module
@InstallIn(SingletonComponent::class)
interface UpcomingModule {

    @Binds
    fun bindUpcomingRepository(repository: RoomUpcomingRepository): UpcomingRepository
}
