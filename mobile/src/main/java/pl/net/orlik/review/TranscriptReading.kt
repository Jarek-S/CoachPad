package pl.net.orlik.review

import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

/** What a recognised recording says, as far as it could be read; null where it is not known. */
data class TranscriptReading(
    val type: EventType? = null,
    val team: Team? = null,
    val player: String? = null,
    val assist: String? = null,
)

/**
 * Reads the type, the team and the players from a recognised recording (design doc, section 4,
 * level 3), e.g. "Gol Adam, asysta Łukasz" or just "Adam, asysta Łukasza".
 *
 * - The type comes from its words ("gol", "żółta kartka", "nieuznany", "notatka"…); without one,
 *   a recording naming a player or an assist is a goal.
 * - Names are matched to the [lineup] of both teams, forgiving typos and Polish endings (Levenshtein
 *   distance, "Łukasza" → "Łukasz"); a shirt number said as digits also finds the player. A name
 *   matching players of both teams equally is left out.
 * - Names after "asysta" are the assist, the others the scorer (or the player of a card). The first
 *   name in a role stays, unless a correction follows: "Adam, nie, Bartek" → Bartek.
 * - The team is the scorer's (else the assist's) team in the lineup, else a team name that was said.
 *   An assist from the other team is dropped.
 * - With no lineup at all, capitalised words are taken as names, as Whisper writes them.
 */
fun readTranscript(text: String, lineup: List<LineupPlayer>, homeTeam: String?, awayTeam: String?): TranscriptReading {
    val words = Regex("""[\p{L}\p{N}]+""").findAll(text).map { it.value }.toList()
    val folded = words.map(::fold)
    val type = typeOf(folded.toSet())

    var role = Role.PLAYER
    var corrected = false
    val chosen = mutableMapOf<Role, Named>()
    var index = 0
    while (index < words.size) {
        val word = folded[index]
        when {
            word in assistWords -> role = Role.ASSIST.also { corrected = false }
            word in scorerWords -> role = Role.PLAYER.also { corrected = false }
            word in correctionWords -> corrected = true
            else -> {
                val found = if (lineup.isEmpty()) capitalisedName(words[index]) else lineupName(folded, index, lineup)
                if (found != null) {
                    if (role !in chosen || corrected) chosen[role] = found
                    corrected = false
                    index += found.words
                    continue
                }
            }
        }
        index++
    }

    val player = chosen[Role.PLAYER]
    val assist = chosen[Role.ASSIST]?.takeIf { player?.team == null || it.team == null || it.team == player.team }
    val readType = type ?: if (player != null || assist != null || folded.any { it in assistWords }) EventType.GOAL else null
    val team = player?.team ?: assist?.team ?: teamNamed(folded, homeTeam, awayTeam)
    return TranscriptReading(
        type = readType,
        team = team.takeIf { readType?.needsTeam == true },
        player = player?.name.takeIf { readType?.hasPlayer == true },
        assist = assist?.name.takeIf { readType?.hasAssist == true },
    )
}

/** Fills only what the review has not set yet; nothing already chosen by hand is replaced. */
fun EventDraft.filledFrom(reading: TranscriptReading): EventDraft {
    val type = type ?: reading.type ?: return this
    return copy(
        type = type,
        team = (team ?: reading.team).takeIf { type.needsTeam },
        player = player.ifBlank { reading.player.orEmpty() },
        assist = assist.ifBlank { reading.assist.orEmpty() },
    )
}

private enum class Role { PLAYER, ASSIST }

/** A name found in the text, the lineup player's team, and how many words it took. */
private data class Named(val name: String, val team: Team?, val words: Int = 1)

private fun typeOf(words: Set<String>): EventType? = when {
    words.any { it in disallowedWords } -> EventType.GOAL_DISALLOWED
    words.any { it in redWords } -> EventType.RED_CARD
    words.any { it in yellowWords || it in cardWords } -> EventType.YELLOW_CARD
    words.any { it in noteWords } -> EventType.NOTE
    words.any { it in goalWords } -> EventType.GOAL
    else -> null
}

/**
 * The lineup player the word at [index] names, by the whole name ("Kuba S.") before the first word
 * alone ("Kuba"); null when none, or when the best match is a tie between players.
 */
private fun lineupName(folded: List<String>, index: Int, lineup: List<LineupPlayer>): Named? {
    val word = folded[index]
    if (word in commonWords) return null
    if (word.all { it.isDigit() }) {
        val numbered = lineup.filter { it.number?.toString() == word }
        return numbered.singleOrNull()?.let { Named(it.name, it.team) }
    }
    if (word.length < 3) return null
    // Whole names of several words, said in full
    lineup.filter { player ->
        val parts = nameWords(player.name)
        parts.size > 1 && parts.indices.all { i -> folded.getOrNull(index + i)?.let { matches(it, parts[i]) != null } == true }
    }.let { full ->
        if (full.size == 1) return Named(full[0].name, full[0].team, nameWords(full[0].name).size)
        if (full.size > 1) return null
    }
    // First names; one that is also the whole name of a player wins over "Kuba" in "Kuba S."
    val scored = lineup.mapNotNull { player -> matches(word, nameWords(player.name).first())?.let { player to it } }
    if (scored.isEmpty()) return null
    val best = scored.minOf { it.second }
    val top = scored.filter { it.second == best }.map { it.first }
    val pick = top.singleOrNull() ?: top.filter { nameWords(it.name).size == 1 }.singleOrNull()
    return pick?.let { Named(it.name, it.team) }
}

/** How far [word] is from a name word: 0 the same, else the distance; null when too far to be it. */
private fun matches(word: String, name: String): Int? {
    if (word == name) return 0
    // Polish endings: "Łukasza", "Adamem", "Marcinowi"
    if (name.length >= 3 && word.startsWith(name) && word.length - name.length <= 3) return 1
    val allowed = when {
        name.length <= 3 -> 0
        name.length <= 5 -> 1
        else -> 2
    }
    if (allowed == 0) return null
    return levenshtein(word, name).takeIf { it <= allowed }?.let { it + 1 }
}

private fun capitalisedName(word: String): Named? =
    word.takeIf { it.length >= 2 && it.first().isUpperCase() && fold(it) !in allKeywords && fold(it) !in commonWords }
        ?.let { Named(it, null) }

private fun teamNamed(folded: List<String>, homeTeam: String?, awayTeam: String?): Team? {
    fun said(name: String?): Boolean {
        val parts = name?.let(::nameWords)?.filter { it.length >= 3 } ?: return false
        return parts.isNotEmpty() && parts.all { part -> folded.any { matches(it, part) != null } }
    }
    val home = said(homeTeam)
    val away = said(awayTeam)
    return when {
        home && !away -> Team.HOME
        away && !home -> Team.AWAY
        else -> null
    }
}

private fun nameWords(name: String): List<String> =
    Regex("""[\p{L}\p{N}]+""").findAll(name).map { fold(it.value) }.toList()

/** Lower case without Polish marks, so "Łukasz", "lukasz" and "ŁUKASZ" compare equal. */
fun fold(text: String): String = buildString {
    for (c in text.lowercase()) {
        append(
            when (c) {
                'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'
                'ó' -> 'o'; 'ś' -> 's'; 'ź' -> 'z'; 'ż' -> 'z'
                else -> c
            },
        )
    }
}

/** Edits (insert, delete, replace one letter) turning [a] into [b]. */
fun levenshtein(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

// Words compared after fold(): Polish first, English for an app set to English
private val goalWords = setOf("gol", "gola", "bramka", "bramke", "bramki", "goal")
private val disallowedWords = setOf("nieuznany", "nieuznana", "nieuznanego", "nieuznanej", "disallowed")
private val yellowWords = setOf("zolta", "zolto", "zoltej", "zolte", "yellow")
private val redWords = setOf("czerwona", "czerwonej", "czerwien", "red")
private val cardWords = setOf("kartka", "kartke", "kartki", "card")
private val noteWords = setOf("notatka", "uwaga", "zmiana", "note")
private val assistWords = setOf("asysta", "asysty", "asyste", "asystowal", "asystujacy", "podanie", "podal", "assist", "assisted")
private val scorerWords = setOf("strzelil", "strzelila", "strzelec", "scored", "scorer") + goalWords
private val correctionWords = setOf("nie", "poprawka", "korekta", "przepraszam", "znaczy", "sorry", "no", "not", "correction")
private val allKeywords = goalWords + disallowedWords + yellowWords + redWords + cardWords + noteWords + assistWords + scorerWords + correctionWords

/** Words never taken as a name, even when one is close to them. */
private val commonWords = setOf(
    "tak", "ale", "jest", "byl", "bylo", "dla", "albo", "oraz", "to", "ten", "ta", "po", "na", "do", "od", "za",
    "and", "the", "was", "for", "with",
)
