package dev.alializadeh.orbit.ring

import java.time.LocalDate

/** Stored by name: never rename a constant. */
enum class CadenceUnit {
    Days,
    Weeks,
    Months,
}

/**
 * How often the user wants to be in touch: every [count] [unit]s. "No Cadence" is a null
 * `Cadence?`, never a special value. A Person's next due date is the last-contact date plus this
 * period (slice 4 builds Due on it).
 */
data class Cadence(val count: Int, val unit: CadenceUnit) {
    init {
        require(count >= 1) { "A Cadence needs a positive count, not $count" }
    }

    /** The day someone last contacted on [lastContact] becomes due. */
    fun dueDate(lastContact: LocalDate): LocalDate = when (unit) {
        CadenceUnit.Days -> lastContact.plusDays(count.toLong())
        CadenceUnit.Weeks -> lastContact.plusWeeks(count.toLong())
        CadenceUnit.Months -> lastContact.plusMonths(count.toLong())
    }

    companion object {
        fun days(count: Int) = Cadence(count, CadenceUnit.Days)
        fun weeks(count: Int) = Cadence(count, CadenceUnit.Weeks)
        fun months(count: Int) = Cadence(count, CadenceUnit.Months)
    }
}
