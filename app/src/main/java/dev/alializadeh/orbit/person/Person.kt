package dev.alializadeh.orbit.person

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant

/**
 * Someone the user keeps in Orbit. Only the Name is required; slice 2 adds the rest of the profile.
 */
@Entity(tableName = "person", indices = [Index("name")])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant,
)
