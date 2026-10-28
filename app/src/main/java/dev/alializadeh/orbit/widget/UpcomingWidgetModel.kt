package dev.alializadeh.orbit.widget

import dev.alializadeh.orbit.upcoming.UpcomingEntry
import dev.alializadeh.orbit.upcoming.UpcomingFeed
import java.time.LocalDate

/**
 * What the Upcoming widget shows (PRD v1.1): Today's Occasions and dates, then Coming up, as many
 * as [MAX_ROWS], and how many People are Due. [discreet] (App lock, like notifications): counts
 * only, no names or photos.
 */
data class UpcomingWidgetModel(
    val today: LocalDate,
    val rows: List<UpcomingEntry>,
    val todayCount: Int,
    val comingUpCount: Int,
    val dueCount: Int,
    val discreet: Boolean,
) {
    /** Nothing today, nothing coming up, nobody Due: "All caught up". */
    val isEmpty: Boolean get() = todayCount == 0 && comingUpCount == 0 && dueCount == 0

    companion object {
        /** More than a 4×2 widget can show; the rest scroll. */
        const val MAX_ROWS = 8

        fun of(feed: UpcomingFeed, today: LocalDate, discreet: Boolean): UpcomingWidgetModel {
            val todays: List<UpcomingEntry> = feed.todayOccasions + feed.today
            return UpcomingWidgetModel(
                today = today,
                rows = if (discreet) emptyList() else (todays + feed.comingUpEntries).take(MAX_ROWS),
                todayCount = todays.size,
                comingUpCount = feed.comingUpEntries.size,
                dueCount = feed.due.size,
                discreet = discreet,
            )
        }
    }
}

