package pl.net.orlik.shared

/**
 * A match pause, as recorded by MATCH_PAUSE / MATCH_RESUME events.
 * Timestamps are epoch milliseconds. [resumedAt] is null while the pause is still open.
 */
data class Pause(
    val pausedAt: Long,
    val resumedAt: Long? = null,
)

/**
 * Play time at moment [at]: time elapsed since [matchStart] minus the time spent in pauses.
 *
 * A pause still ongoing at [at] (open, or resumed later) counts only up to [at].
 * Pauses starting after [at] are ignored. Never returns a negative value.
 *
 * Single implementation used by the watch (live clock) and the phone (event minutes),
 * see docs/dokumentacja-projektowa.md, section 8.
 */
fun playTimeMillis(at: Long, matchStart: Long, pauses: List<Pause>): Long {
    if (at <= matchStart) return 0

    val pausedMillis = pauses.sumOf { pause ->
        val start = maxOf(pause.pausedAt, matchStart)
        val end = minOf(pause.resumedAt ?: at, at)
        (end - start).coerceAtLeast(0)
    }
    return (at - matchStart - pausedMillis).coerceAtLeast(0)
}

/** Full minutes of play time at moment [at] (e.g. 35:59 → 35). */
fun playMinute(at: Long, matchStart: Long, pauses: List<Pause>): Long =
    playTimeMillis(at, matchStart, pauses) / 60_000
