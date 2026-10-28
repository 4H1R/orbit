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

    /**
     * Back from what may be Orbit's own picker: nothing is drawn until `onResume`, by when its
     * result (and any new intent from the launcher or a notification) has arrived.
     */
    Deciding,

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
 * - Orbit's own pickers ([onPickerLaunched] just before one opens) don't count as leaving, but only
 *   when Orbit comes back **because that picker returned its result** (a cancel too): such a
 *   return is [AppLockState.Deciding] from [onForeground] until [onResumed], and exempt only if
 *   [onPickerResult] came for it and no [onNewIntent] did. Any other way back (the launcher or a
 *   notification, which with `singleTask` also close the picker and so bring its cancel) is
 *   judged by the re-lock setting as usual. The exemption also ends when the screen goes off meanwhile ([onScreenOff],
 *   which may come late to a cached process, or deep sleep seen on [uptime]) and after
 *   [OWN_ACTIVITY_LIMIT_MILLIS] (someone left the phone on a picker).
 * - The screen-lock prompt is a system window on Android 12+: Orbit only pauses under it, so it
 *   isn't time away and needs no exemption. Contact actions (dialer, messenger, browser, maps),
 *   system settings and other apps count as leaving.
 * - Turning App lock on or off happens after an unlock, so it never locks by itself.
 * - With no screen lock on the phone it's [AppLockState.Paused]: open, and it doesn't lock. Once a
 *   screen lock exists again, the next re-lock (or cold start) brings the gate back.
 */
class AppLockMachine(
    private val elapsedRealtime: () -> Long,
    /** Like [elapsedRealtime], but stopped in deep sleep, which only happens with the screen off. */
    private val uptime: () -> Long = elapsedRealtime,
) {

    private var settings: AppLockSettings? = null
    private var locked = true
    private var hasScreenLock = true

    /** When Orbit went to the background; null while it's in the foreground. */
    private var leftAt: Long? = null

    /** Deep sleep since boot when Orbit left (elapsed realtime minus uptime). */
    private var sleptBeforeLeaving = 0L

    /** The picker Orbit last launched, until Orbit leaves for it or its result comes back. */
    private var launched: PickerLaunch? = null

    /** The picker Orbit is away for, while its result may still be what brings Orbit back. */
    private var awayFor: PickerLaunch? = null

    /** Back from what may be a picker's return: decided by [onResumed]. */
    private var deciding: Return? = null

    /** The last return the picker exemption let in, until Orbit leaves again or is unlocked. */
    private var exempted: Return? = null

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
                deciding != null -> AppLockState.Deciding
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

    /** Orbit is about to open its own picker for a result, under [requestCode]. */
    fun onPickerLaunched(requestCode: Int) {
        launched = PickerLaunch(requestCode, elapsedRealtime())
    }

    /** Orbit went to the background (its activity stopped, not for a configuration change). */
    fun onBackground() {
        // Stopped again before it resumed: that return was plain time away.
        deciding?.let { decide(it, picker = false) }
        exempted = null
        if (leftAt != null) return
        val now = elapsedRealtime()
        leftAt = now
        sleptBeforeLeaving = now - uptime()
        awayFor = launched?.takeIf { now - it.at <= OWN_ACTIVITY_WINDOW_MILLIS }
        launched = null
    }

    /**
     * The screen went off: from then on it's time away, even over Orbit's own picker. [screenIsOn]
     * means it's already back on, so this broadcast came late (Android holds broadcasts for a
     * cached process): a picker return it let in is judged as plain time away after all.
     */
    fun onScreenOff(screenIsOn: Boolean = false) {
        launched = null
        if (leftAt != null) {
            if (awayFor != null) {
                awayFor = null
                leftAt = elapsedRealtime()
            }
            return
        }
        deciding?.let { deciding = it.copy(exempt = false) }
        val exempt = exempted
        if (screenIsOn && exempt != null) {
            exempted = null
            if (tooLong(exempt.away, forPicker = false)) locked = true
        }
    }

    /**
     * Orbit is coming back to the foreground (the activity is starting, nothing drawn yet): locks
     * if it was away too long. What may be a picker's return waits in [AppLockState.Deciding].
     * Repeat calls do nothing.
     */
    fun onForeground(hasScreenLock: Boolean) {
        val left = leftAt
        if (left != null) {
            leftAt = null
            arrival++
            val now = elapsedRealtime()
            val away = now - left
            val slept = (now - uptime()) - sleptBeforeLeaving > SLEEP_TOLERANCE_MILLIS
            val picker = awayFor?.takeIf { !slept && away <= OWN_ACTIVITY_LIMIT_MILLIS }
            awayFor = null
            val back = Return(away, picker?.requestCode, exempt = picker != null)
            if (back.exempt && !locked && settings?.enabled == true) deciding = back else decide(back, picker = false)
        }
        onScreenLock(hasScreenLock)
    }

    /**
     * A result came back for [requestCode] (after `onStart`, before `onResume`): if it's for the
     * picker Orbit left for, that may be why it's back, unless a new intent comes too.
     */
    fun onPickerResult(requestCode: Int) {
        if (launched?.requestCode == requestCode) launched = null
        val back = deciding ?: return
        if (back.requestCode == requestCode) deciding = back.copy(resulted = true)
    }

    /** A new intent: the launcher or a notification brought Orbit back, not a picker. */
    fun onNewIntent() {
        awayFor = null
        deciding?.let { deciding = it.copy(exempt = false) }
    }

    /** The activity resumed: the picker exemption holds only for its own result's return. */
    fun onResumed() {
        deciding?.let { decide(it, picker = it.exempt && it.resulted) }
    }

    /** The user unlocked with the phone's screen lock: any pending exemption is over too. */
    fun onUnlocked() {
        locked = false
        launched = null
        awayFor = null
        deciding = null
        exempted = null
    }

    private fun decide(back: Return, picker: Boolean) {
        deciding = null
        exempted = back.takeIf { picker }
        if (tooLong(back.away, picker) && settings?.enabled != false) locked = true
    }

    private fun tooLong(away: Long, forPicker: Boolean): Boolean {
        val relockAfter = settings?.relockAfter ?: RelockAfter.Immediately
        return if (forPicker) {
            away > maxOf(relockAfter.millis, OWN_ACTIVITY_LIMIT_MILLIS)
        } else {
            relockAfter == RelockAfter.Immediately || away > relockAfter.millis
        }
    }

    private data class PickerLaunch(val requestCode: Int, val at: Long)

    /**
     * One return to the foreground: the time away, the picker it may be the return of, whether
     * nothing has ruled that out ([exempt]) and whether that picker's result came ([resulted]).
     */
    private data class Return(val away: Long, val requestCode: Int?, val exempt: Boolean, val resulted: Boolean = false)

    companion object {
        /** Opening a picker stops the activity within this; later, it's not the picker's doing. */
        const val OWN_ACTIVITY_WINDOW_MILLIS = 5_000L

        /** Longer than this on Orbit's own picker counts as time away after all. */
        const val OWN_ACTIVITY_LIMIT_MILLIS = 10 * 60_000L

        /** More deep sleep than this while away means the screen was off. */
        const val SLEEP_TOLERANCE_MILLIS = 1_000L
    }
}
