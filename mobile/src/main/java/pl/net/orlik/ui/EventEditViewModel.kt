package pl.net.orlik.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.net.orlik.audio.AudioDurations
import pl.net.orlik.audio.AudioPlayer
import pl.net.orlik.data.DeletedRecording
import pl.net.orlik.data.EventEdits
import pl.net.orlik.data.LineupDao
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.Team
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.review.EventDraft
import pl.net.orlik.review.ReviewPosition
import pl.net.orlik.review.ReviewRow
import pl.net.orlik.review.draftOf
import pl.net.orlik.review.goalCandidates
import pl.net.orlik.review.matchReviewOf
import pl.net.orlik.review.nextToReview
import pl.net.orlik.review.positionOf
import pl.net.orlik.review.relinked
import pl.net.orlik.review.withDraftMinute
import pl.net.orlik.shared.EventType

data class EventEditUiState(
    val match: Match? = null,
    /** The edited event as the review list shows it; null while loading. */
    val row: ReviewRow? = null,
    val recordedAt: Long? = null,
    /** Null for an event that is already confirmed. */
    val position: ReviewPosition? = null,
    /** Null while loading. */
    val draft: EventDraft? = null,
    val durationMillis: Long? = null,
    val playing: Boolean = false,
    /** For a disallowed goal: earlier goals of its team, to choose from on "Zmień". */
    val goalCandidates: List<ReviewRow> = emptyList(),
    /** The match lineup, offered in the player fields of the chosen team. */
    val lineup: Map<Team, List<LineupPlayer>> = emptyMap(),
    /** Added by hand, without a time: the minute can be typed (design doc, section 3.3). */
    val addedByHand: Boolean = false,
    /** Just added with "Dodaj zdarzenie": saved with "Zapisz" or "Dodaj kolejne", dropped if left empty. */
    val isNew: Boolean = false,
)

/** What the screen does once an action is saved. */
sealed interface EditEffect {
    /** Back to the review list; [deleted] offers "Cofnij" there. */
    data class Finished(val deleted: DeletedRecording? = null) : EditEffect

    /** The next event is open; offer "Cofnij" for the one just deleted. */
    data class Deleted(val deleted: DeletedRecording) : EditEffect
}

/**
 * One event at a time; "Pomiń", "Zatwierdź i dalej" and the bin move on to the next one. Without
 * [startEventId] it adds a new event to the match ("Dodaj zdarzenie").
 */
class EventEditViewModel(
    private val matchId: String,
    startEventId: String?,
    private val dao: MatchEventDao,
    private val lineup: LineupDao,
    private val edits: EventEdits,
    private val recordingsDir: File,
) : ViewModel() {

    private val player = AudioPlayer(viewModelScope)

    /** The event added on this screen and not saved yet; null when editing an existing one. */
    private val newId = MutableStateFlow(if (startEventId == null) UUID.randomUUID().toString() else null)

    private val eventId = MutableStateFlow(startEventId ?: checkNotNull(newId.value))

    /** Loaded once per event, so typing is not overwritten by database updates. */
    private val draft = MutableStateFlow<EventDraft?>(null)

    private val durationMillis = MutableStateFlow<Long?>(null)

    private val events = dao.eventsOf(matchId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val effects = Channel<EditEffect>(Channel.BUFFERED)
    val effect: Flow<EditEffect> = effects.receiveAsFlow()

    val state: StateFlow<EventEditUiState> = combine(
        combine(eventId, draft, durationMillis, newId) { id, draft, duration, new -> EditKey(id, draft, duration, id == new) },
        events,
        dao.match(matchId),
        player.playingId,
        lineup.lineupOf(matchId),
    ) { (id, draft, duration, isNew), stored, match, playingId, players ->
        val list = stored.orEmpty().let { if (draft != null) withDraftMinute(it, id, draft) else it }
        val review = matchReviewOf(list)
        val rows = (review.toReview + review.confirmed).associateBy { it.eventId }
        val event = list.firstOrNull { it.id == id }
        val team = draft?.team
        EventEditUiState(
            match = match,
            row = rows[id],
            recordedAt = list.firstOrNull { it.id == id }?.recordedAt,
            position = positionOf(review, id),
            draft = draft,
            durationMillis = duration,
            playing = playingId == id,
            goalCandidates = if (event != null && team != null && draft.type == EventType.GOAL_DISALLOWED) {
                goalCandidates(list, event.id, team).mapNotNull { rows[it.id] }
            } else {
                emptyList()
            },
            lineup = players.groupBy { it.team },
            addedByHand = event != null && event.recordedAt == null && event.type?.isStructural != true,
            isNew = isNew,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventEditUiState())

    init {
        viewModelScope.launch {
            newId.value?.let { edits.add(matchId, it) }
            load(eventId.value)
        }
    }

    fun setType(type: EventType) {
        if (draft.value?.type != type) changeAndRelink { it.withType(type) }
    }

    fun setTeam(team: Team) {
        if (draft.value?.team != team) changeAndRelink { it.copy(team = team) }
    }

    /** "Zmień": a goal chosen by hand, or null for none. Kept until the type or team changes. */
    fun setLinkedGoal(goalId: String?) = draft.update { it.copy(linkedGoalId = goalId) }

    /** The minute moves an event added by hand among the others, so its goal is found again too. */
    fun setMinute(minute: String) {
        if (draft.value?.withMinute(minute) != draft.value) changeAndRelink { it.withMinute(minute) }
    }

    private fun changeAndRelink(change: (EventDraft) -> EventDraft) {
        val id = eventId.value
        val event = events.value.orEmpty().firstOrNull { it.id == id } ?: return
        draft.update {
            val changed = change(it)
            relinked(changed, event, withDraftMinute(events.value.orEmpty(), id, changed))
        }
    }

    fun setNote(note: String) = draft.update { it.copy(note = note) }

    fun setPlayer(player: String) = draft.update { it.copy(player = player) }

    fun setAssist(assist: String) = draft.update { it.copy(assist = assist) }

    fun togglePlayback() {
        val audio = state.value.row?.audioFileRef ?: return
        player.toggle(eventId.value, File(recordingsDir, audio))
    }

    fun positionMillis(): Long = player.positionMillis()

    /** Keeps what was typed; the event stays under "Do przejrzenia". */
    fun skip() = saveAndMoveOn(confirm = false)

    fun confirm() {
        if (draft.value?.canConfirm != true) return
        if (newId.value == eventId.value) saveNew(another = false) else saveAndMoveOn(confirm = true)
    }

    /** "Dodaj kolejne": saves the new event and opens another empty one, as when copying from notes. */
    fun addAnother() {
        if (draft.value?.canConfirm == true && newId.value == eventId.value) saveNew(another = true)
    }

    /**
     * Back arrow: keeps what was typed without confirming. A new event left empty is dropped, so
     * "Dodaj zdarzenie" opened by mistake leaves nothing behind.
     */
    fun exit() {
        viewModelScope.launch {
            val id = eventId.value
            val current = draft.value
            if (id == newId.value && (current == null || current.isEmpty)) {
                edits.discard(id)
            } else {
                current?.let { edits.save(id, it, confirm = false) }
            }
            effects.send(EditEffect.Finished())
        }
    }

    private fun saveNew(another: Boolean) {
        viewModelScope.launch {
            draft.value?.let { edits.save(eventId.value, it, confirm = true) }
            if (another) {
                val id = UUID.randomUUID().toString()
                edits.add(matchId, id)
                newId.value = id
                load(id)
            } else {
                newId.value = null
                effects.send(EditEffect.Finished())
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            val id = eventId.value
            val next = nextToReview(matchReviewOf(events.filterNotNull().first()), id)
            val deleted = edits.delete(id) ?: return@launch
            if (next != null) {
                load(next)
                effects.send(EditEffect.Deleted(deleted))
            } else {
                effects.send(EditEffect.Finished(deleted))
            }
        }
    }

    private fun saveAndMoveOn(confirm: Boolean) {
        viewModelScope.launch {
            val id = eventId.value
            // Worked out before saving, while a confirmed event is still in the list
            val next = nextToReview(matchReviewOf(events.filterNotNull().first()), id)
            draft.value?.let { edits.save(id, it, confirm) }
            if (next != null) load(next) else effects.send(EditEffect.Finished())
        }
    }

    private fun load(id: String) {
        player.stop()
        eventId.value = id
        draft.value = null
        durationMillis.value = null
        viewModelScope.launch {
            val event = dao.event(id) ?: return@launch
            if (eventId.value != id) return@launch
            draft.value = draftOf(event)
            val duration = event.audioFileRef?.let { AudioDurations.of(File(recordingsDir, it)) }
            if (eventId.value == id) durationMillis.value = duration
        }
    }

    private data class EditKey(val id: String, val draft: EventDraft?, val durationMillis: Long?, val isNew: Boolean)

    private fun MutableStateFlow<EventDraft?>.update(change: (EventDraft) -> EventDraft) {
        value = value?.let(change)
    }

    override fun onCleared() {
        player.stop()
    }

    companion object {
        /** Without [eventId]: "Dodaj zdarzenie". */
        fun factory(app: Application, matchId: String, eventId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = PhoneDatabase.get(app)
                val dao = db.matchEventDao()
                EventEditViewModel(matchId, eventId, dao, db.lineupDao(), EventEdits(dao, recordingsDir(app)), recordingsDir(app))
            }
        }
    }
}
