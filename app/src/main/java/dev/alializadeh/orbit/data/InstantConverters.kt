package dev.alializadeh.orbit.data

import androidx.room3.ColumnTypeConverter
import java.time.Instant

/** Stores an [Instant] as epoch milliseconds (UTC). */
object InstantConverters {

    @ColumnTypeConverter
    fun fromEpochMillis(value: Long): Instant = Instant.ofEpochMilli(value)

    @ColumnTypeConverter
    fun toEpochMillis(instant: Instant): Long = instant.toEpochMilli()
}
