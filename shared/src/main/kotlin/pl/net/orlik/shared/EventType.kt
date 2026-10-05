package pl.net.orlik.shared

/**
 * Match event type. Stored by name in Room, so values can be added freely,
 * but renaming or removing one requires a database migration.
 *
 * On the watch a voice recording has no type yet (null) — it is set on the phone
 * during review or by STT.
 */
enum class EventType {
    // Structural events: recorded by watch buttons, no audio
    MATCH_START,
    MATCH_END,
    MATCH_PAUSE,
    MATCH_RESUME,

    // Events assigned to voice recordings
    GOAL,
    GOAL_DISALLOWED,
    YELLOW_CARD,
    RED_CARD;

    val isStructural: Boolean
        get() = this in STRUCTURAL

    private companion object {
        val STRUCTURAL = setOf(MATCH_START, MATCH_END, MATCH_PAUSE, MATCH_RESUME)
    }
}
