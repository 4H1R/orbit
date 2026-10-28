package dev.alializadeh.orbit.lock

import kotlinx.serialization.Serializable

/**
 * App lock's settings. Device-bound (`DeviceState.appLock`): never in a Backup, and a Restore
 * leaves them as they were.
 */
@Serializable
data class AppLockSettings(
    /** Off by default; turning it on or off needs an unlock. */
    val enabled: Boolean = false,
    val relockAfter: RelockAfter = RelockAfter.OneMinute,
    /** While on: full notifications (actions ask for the device unlock) instead of discreet ones. */
    val showDetailsInNotifications: Boolean = false,
)

/** How long Orbit may be in the background before it locks again. Stored by name: never rename. */
enum class RelockAfter(val millis: Long) {
    /** Any time in the background. */
    Immediately(0),
    OneMinute(60_000),
    FiveMinutes(300_000),
}
