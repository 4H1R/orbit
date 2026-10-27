package dev.alializadeh.orbit.format

private const val FIRST_STRONG_ISOLATE = '\u2068'
private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

/**
 * Wraps a user-supplied value (a name, Tag, Ring name, custom date label) in a first-strong isolate
 * (FSI … PDI) before it goes into a sentence, so a Persian name in English text, or a Latin one in
 * Persian text, can't reorder the punctuation and numbers around it.
 */
fun bidiIsolate(value: String): String = "$FIRST_STRONG_ISOLATE$value$POP_DIRECTIONAL_ISOLATE"

/** For values that always read left to right (phone numbers, emails, handles): LRI … PDI. */
fun bidiIsolateLtr(value: String): String = "$LEFT_TO_RIGHT_ISOLATE$value$POP_DIRECTIONAL_ISOLATE"
