package dev.alializadeh.orbit.data

import androidx.room3.ColumnTypeConverter
import java.time.LocalDate

/**
 * Stores an absolute day (a Gift idea's given date, later an Interaction's date) as ISO text,
 * "2026-10-28": readable inside a Backup, and it sorts as text.
 */
object LocalDateConverters {

    @ColumnTypeConverter
    fun fromIsoText(value: String): LocalDate = LocalDate.parse(value)

    @ColumnTypeConverter
    fun toIsoText(date: LocalDate): String = date.toString()
}
