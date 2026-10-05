package pl.net.orlik.presentation

/** Formats play time as minutes:seconds, e.g. 95:07. Minutes are not wrapped at 60. */
fun formatPlayTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
