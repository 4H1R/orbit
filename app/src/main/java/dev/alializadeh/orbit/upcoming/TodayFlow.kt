package dev.alializadeh.orbit.upcoming

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Today's date per [clock], again whenever it changes: at each midnight while collected, and on
 * every [checks] emission (the screen resuming, which also catches a midnight that passed while
 * the phone slept, since coroutine delays don't count deep sleep).
 */
fun todayFlow(clock: Clock, checks: Flow<Unit>): Flow<LocalDate> =
    merge(checks, midnights(clock))
        .onStart { emit(Unit) }
        .map { LocalDate.now(clock) }
        .distinctUntilChanged()

/** Ticks just after each midnight of [clock]'s zone. */
private fun midnights(clock: Clock): Flow<Unit> = flow {
    while (true) {
        delay(untilNextMidnight(clock).toMillis().coerceAtLeast(MIN_WAIT_MILLIS))
        emit(Unit)
    }
}

/** How long from [clock]'s now until the next midnight in its zone (a moment past it). */
internal fun untilNextMidnight(clock: Clock): Duration {
    val now = ZonedDateTime.now(clock)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
    return Duration.between(now, midnight).plusMillis(MIDNIGHT_MARGIN_MILLIS)
}

/** Lands safely in the new day. */
private const val MIDNIGHT_MARGIN_MILLIS = 500L

/** Never spin: a clock that doesn't move (tests) waits at least this long between checks. */
private const val MIN_WAIT_MILLIS = 1_000L
