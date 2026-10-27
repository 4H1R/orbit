package dev.alializadeh.orbit.backup

/**
 * How old the last Backup may get before Upcoming nudges (when the data has changed since).
 * A backed-up preference, stored by name: never rename a constant.
 */
enum class BackupNudgeInterval(val days: Int?) {
    Days14(14),
    Days30(30),
    Days60(60),
    Days90(90),
    Off(null),
    ;

    companion object {
        val DEFAULT = Days30
    }
}
