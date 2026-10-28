package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.due.KeepInTouch
import java.time.LocalDate

/** Someone with No Cadence: never Due. */
val NeverDue = KeepInTouch(cadence = null, addedOn = LocalDate.of(2026, 1, 1), lastInteraction = null)
