package dev.alializadeh.orbit.data

import androidx.room3.ColumnTypeConverter
import dev.alializadeh.orbit.reminders.LeadTime
import java.time.LocalTime

/** Column types of the backed-up preferences. */
object PreferenceConverters {

    /** A time of day as minutes since midnight. */
    @ColumnTypeConverter
    fun fromMinuteOfDay(value: Int): LocalTime = LocalTime.of(value / 60, value % 60)

    @ColumnTypeConverter
    fun toMinuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    /** Lead times as comma-separated constant names, readable in a Backup ("OneWeek,OnTheDay"). */
    @ColumnTypeConverter
    fun fromLeadTimeNames(value: String): Set<LeadTime> =
        value.split(',').filter(String::isNotEmpty).map(LeadTime::valueOf).toSet()

    @ColumnTypeConverter
    fun toLeadTimeNames(leadTimes: Set<LeadTime>): String =
        leadTimes.sortedByDescending(LeadTime::ordinal).joinToString(",") { it.name }
}
