package dev.alializadeh.orbit.person

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PersonModule {

    @Binds
    fun bindPersonRepository(repository: RoomPersonRepository): PersonRepository
}
