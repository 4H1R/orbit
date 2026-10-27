package dev.alializadeh.orbit.upcoming

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.GiftIdeaOrder
import dev.alializadeh.orbit.person.ImportantDateOrder
import dev.alializadeh.orbit.person.PersonDao
import dev.alializadeh.orbit.person.names
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What Upcoming is computed from. */
interface UpcomingRepository {
    /** Every Person (Archived ones too, flagged) with their Important dates and open Gift ideas. */
    fun observePeople(): Flow<List<UpcomingPerson>>
}

class RoomUpcomingRepository @Inject constructor(private val personDao: PersonDao) : UpcomingRepository {

    override fun observePeople(): Flow<List<UpcomingPerson>> = personDao.observeWithDates().map { people ->
        people.map { row ->
            UpcomingPerson(
                id = row.person.id,
                names = row.person.names,
                photoFileName = row.person.photoFileName,
                isArchived = row.person.isArchived,
                primaryContactMethod = row.contactMethods.firstOrNull(ContactMethod::isPrimary),
                importantDates = row.importantDates.sortedWith(ImportantDateOrder),
                openGiftIdeas = row.giftIdeas.filterNot(GiftIdea::isGiven).sortedWith(GiftIdeaOrder).map(GiftIdea::text),
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
