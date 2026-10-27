package dev.alializadeh.orbit.ring

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Reads Rings. Editing them (Settings → Rings) arrives in slice 4. */
interface RingRepository {
    /** Every Ring, innermost first. */
    fun observeRings(): Flow<List<Ring>>
}

class RoomRingRepository @Inject constructor(private val ringDao: RingDao) : RingRepository {
    override fun observeRings(): Flow<List<Ring>> = ringDao.observeAll()
}

/** The Ring a new Person starts in: the outermost. */
fun List<Ring>.outermost(): Ring? = maxWithOrNull(compareBy(Ring::position, Ring::id))

@Module
@InstallIn(SingletonComponent::class)
interface RingModule {

    @Binds
    fun bindRingRepository(repository: RoomRingRepository): RingRepository
}
