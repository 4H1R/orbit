package dev.alializadeh.orbit.ui.navigation

import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.orbitview.OrbitViewKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.upcoming.UpcomingKey

/** The four bottom tabs, in bar order. Each keeps its own back stack, starting at [rootKey]. */
enum class TopLevelTab(val rootKey: NavKey) {
    Upcoming(UpcomingKey),
    People(PeopleKey),
    Orbit(OrbitViewKey),
    Settings(SettingsKey),
}
