package dev.alializadeh.orbit.lock

/** What App lock's gate shows. */
enum class AppLockState {
    /** The settings haven't been read yet: the splash screen stays up. */
    Unknown,

    /** App lock is off. */
    Off,

    /** On, and unlocked for now. */
    Unlocked,

    /** On and locked: the locked screen replaces the whole UI. */
    Locked,

    /** On, but the phone has no screen lock: Orbit opens without the gate and Upcoming warns. */
    Paused,
}

/** The gate's state, and which arrival in the foreground it's for (the prompt shows once per arrival). */
data class AppLockStatus(val state: AppLockState, val arrival: Int)

/**
 * Every rule of when App lock locks (ticket 10), as a plain class: the Android side
 * ([AppLock]) feeds it events, on the main thread.
 *
 * - Locked on a cold start (a new process), until unlocked.
 * - After time in the background (screen-off counts: the activity stops) longer than
 *   [AppLockSettings.relockAfter]; Immediately is any time at all. Time is measured on
 *   [elapsedRealtime], which ignores changes to the phone's date and time.
 * - Orbit's own pickers and prompts ([onOwnActivity] just before them) don't count as leaving,
 *   unless the screen goes off meanwhile or they stay out longer than [OWN_ACTIVITY_LIMIT_MILLIS]
 *   (someone left the phone on a picker). Anything else that covers Orbit counts: contact actions
 *   (dialer, messenger, browser, maps), system settings, other apps.
 * - Turning App lock on or off happens after an unlock, so it never locks by itself.
 * - With no screen lock on the phone it's [AppLockState.Paused]: open, and it doesn't lock. Once a
 *   screen lock exists again, the next re-lock (or cold start) brings the gate back.
 */
class AppLockMachine(private val elapsedRealtime: () -> Long) {

    private var settings: AppLockSettings? = null
    private var locked = true
    private var hasScreenLock = true

    /** When Orbit went to the background; null while it's in the foreground. */
    private var leftAt: Long? = null

    /** The current time away is for Orbit's own picker or prompt. */
    private var leftForOwnActivity = false

    /** When Orbit last said it's about to open its own picker or prompt. */
    private var ownActivityAt: Long? = null

    /** Counts arrivals in the foreground; the first is the cold start's. */
    var arrival: Int = 1
        private set

    val state: AppLockState
        get() {
            val settings = settings ?: return AppLockState.Unknown
            return when {
                !settings.enabled -> AppLockState.Off
                !hasScreenLock -> AppLockState.Paused
                locked -> AppLockState.Locked
                else -> AppLockState.Unlocked
            }
        }

    val status: AppLockStatus get() = AppLockStatus(state, arrival)

    /** The settings as stored; the first call is the cold start's read. */
    fun onSettings(new: AppLockSettings) {
        val old = settings
        settings = new
        // On → off, or off → on (both after an unlock): nothing to unlock now. The first read
        // keeps the cold start's lock.
        if (old != null && old.enabled != new.enabled) locked = false
        if (!new.enabled) locked = false
    }

    /** Whether the phone has a secure screen lock; read at start and on every arrival. */
    fun onScreenLock(hasScreenLock: Boolean) {
        this.hasScreenLock = hasScreenLock
        // Paused: the user is in without the gate.
        if (!hasScreenLock) locked = false
    }

    /** Orbit is about to open its own picker or the screen-lock prompt. */
    fun onOwnActivity() {
        ownActivityAt = elapsedRealtime()
    }

    /** Orbit went to the background (its activity stopped, not for a configuration change). */
    fun onBackground() {
        if (leftAt != null) return
        val now = elapsedRealtime()
        leftAt = now
        leftForOwnActivity = ownActivityAt?.let { now - it <= OWN_ACTIVITY_WINDOW_MILLIS } == true
        ownActivityAt = null
    }

    /** The screen went off: from now on it's time away, even over Orbit's own picker. */
    fun onScreenOff() {
        ownActivityAt = null
        if (leftAt != null && leftForOwnActivity) {
            leftForOwnActivity = false
            leftAt = elapsedRealtime()
        }
    }

    /** Orbit is back in the foreground; locks if it was away too long. Repeat calls do nothing. */
    fun onForeground(hasScreenLock: Boolean) {
        val left = leftAt
        if (left != null) {
            leftAt = null
            arrival++
            val away = elapsedRealtime() - left
            val relockAfter = settings?.relockAfter ?: RelockAfter.Immediately
            val tooLong = if (leftForOwnActivity) {
                away > maxOf(relockAfter.millis, OWN_ACTIVITY_LIMIT_MILLIS)
            } else {
                relockAfter == RelockAfter.Immediately || away > relockAfter.millis
            }
            if (tooLong && settings?.enabled != false) locked = true
            leftForOwnActivity = false
        }
        ownActivityAt = null
        onScreenLock(hasScreenLock)
    }

    /** The user unlocked with the phone's screen lock. */
    fun onUnlocked() {
        locked = false
    }

    companion object {
        /** Opening a picker stops the activity within this; later, it's not the picker's doing. */
        const val OWN_ACTIVITY_WINDOW_MILLIS = 5_000L

        /** Longer than this on Orbit's own picker or prompt counts as time away after all. */
        const val OWN_ACTIVITY_LIMIT_MILLIS = 10 * 60_000L
    }
}
