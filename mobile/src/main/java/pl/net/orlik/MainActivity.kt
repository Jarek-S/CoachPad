package pl.net.orlik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.net.orlik.data.DeletedRecording
import pl.net.orlik.data.EventEdits
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.export.shareExport
import pl.net.orlik.sync.QueueSummarySource
import pl.net.orlik.ui.EditEffect
import pl.net.orlik.ui.EventEditScreen
import pl.net.orlik.ui.EventEditViewModel
import pl.net.orlik.ui.LineupScreen
import pl.net.orlik.ui.LineupViewModel
import pl.net.orlik.ui.MatchReviewScreen
import pl.net.orlik.ui.MatchReviewViewModel
import pl.net.orlik.ui.MatchesScreen
import pl.net.orlik.ui.MatchesViewModel
import pl.net.orlik.ui.SettingsScreen
import pl.net.orlik.ui.SettingsViewModel
import pl.net.orlik.ui.SyncScreen
import pl.net.orlik.ui.SyncViewModel
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.StadiumBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status and navigation bar icons: the stadium behind them is dark
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val edits = EventEdits(PhoneDatabase.get(this).matchEventDao(), recordingsDir(this))
        // Audio of recordings deleted while the app last closed with "Cofnij" still offered
        lifecycleScope.launch { edits.purgeAll() }
        setContent {
            MatchNotesTheme {
                // Above all screens, so "Cofnij" stays when the edit screen returns to the list
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val recordingDeleted = stringResource(R.string.recording_deleted)
                val eventDeleted = stringResource(R.string.event_deleted)
                val undoLabel = stringResource(R.string.undo)
                val offerUndo: (DeletedRecording) -> Unit = { deleted ->
                    // A previous offer ends (and its audio goes) when another recording is deleted
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launch {
                        val message = if (deleted.recording) recordingDeleted else eventDeleted
                        val result = snackbar.showSnackbar(message, undoLabel, duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) edits.undo(deleted) else edits.purge(deleted)
                    }
                }
                // Screens by route: the match list is the start, sync and a match's review open from it
                val navController = rememberNavController()
                if (savedInstanceState == null) {
                    // Matches waiting on the watch: open sync at once, over the list (design doc, section 11)
                    LaunchedEffect(Unit) {
                        val summary = withTimeoutOrNull(SUMMARY_WAIT_MILLIS) { QueueSummarySource(application).summaries.first() }
                        if (summary?.matches?.isNotEmpty() == true) navController.navigate(SYNC)
                    }
                }
                StadiumBackground(Modifier.fillMaxSize()) {
                    NavHost(navController, startDestination = MATCHES) {
                        composable(MATCHES) {
                            val viewModel: MatchesViewModel = viewModel(factory = MatchesViewModel.factory(application))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            MatchesScreen(
                                state = state,
                                onOpenMatch = { navController.navigate("$MATCH/$it") },
                                onOpenPlanned = { navController.navigate("$MATCH/$it/$LINEUP") },
                                onNewMatch = { viewModel.plan { navController.navigate("$MATCH/$it/$LINEUP") } },
                                onOpenSync = { navController.navigate(SYNC) },
                                onOpenSettings = { navController.navigate(SETTINGS) },
                                onDeleteMatch = viewModel::delete,
                            )
                        }
                        composable(SETTINGS) {
                            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(application))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onSttEnabled = viewModel::setSttEnabled,
                                onDraft = viewModel::setDraft,
                                onSaveKey = viewModel::saveKey,
                                onCheckKey = viewModel::checkKey,
                                onChangeKey = viewModel::changeKey,
                                onCancelChange = viewModel::cancelChange,
                                onDeleteKey = viewModel::deleteKey,
                            )
                        }
                        composable(SYNC) {
                            val viewModel: SyncViewModel = viewModel(factory = SyncViewModel.factory(application))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            SyncScreen(
                                state = state,
                                onSync = viewModel::sync,
                                onDelete = viewModel::deleteMatch,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("$MATCH/{$MATCH_ID}") { entry ->
                            val matchId = checkNotNull(entry.arguments?.getString(MATCH_ID))
                            val viewModel: MatchReviewViewModel =
                                viewModel(factory = MatchReviewViewModel.factory(application, matchId))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            MatchReviewScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onTogglePlayback = viewModel::togglePlayback,
                                onOpenEvent = {
                                    viewModel.stopPlayback()
                                    navController.navigate("$MATCH/$matchId/$EVENT/$it")
                                },
                                onTranscribe = viewModel::transcribe,
                                onTakeOverPlanned = viewModel::takeOverPlanned,
                                onOpenLineup = {
                                    viewModel.stopPlayback()
                                    navController.navigate("$MATCH/$matchId/$LINEUP")
                                },
                                onExport = { format ->
                                    scope.launch { shareExport(this@MainActivity, viewModel.export(), format) }
                                },
                                onAddEvent = {
                                    viewModel.stopPlayback()
                                    navController.navigate("$MATCH/$matchId/$NEW_EVENT")
                                },
                            )
                        }
                        composable("$MATCH/{$MATCH_ID}/$LINEUP") { entry ->
                            val matchId = checkNotNull(entry.arguments?.getString(MATCH_ID))
                            val viewModel: LineupViewModel = viewModel(factory = LineupViewModel.factory(application, matchId))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            LineupScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onTeamName = viewModel::setTeamName,
                                onAddPlayer = viewModel::addPlayer,
                                onUpdatePlayer = viewModel::updatePlayer,
                                onRemovePlayer = viewModel::removePlayer,
                                onEnterFromNotes = { day ->
                                    viewModel.enterFromNotes(day) {
                                        // The review takes the lineup's place, with the first event open over it
                                        navController.navigate("$MATCH/$matchId") {
                                            popUpTo("$MATCH/{$MATCH_ID}/$LINEUP") { inclusive = true }
                                        }
                                        navController.navigate("$MATCH/$matchId/$NEW_EVENT")
                                    }
                                },
                            )
                        }
                        // An event to edit, or without one "Dodaj zdarzenie"
                        composable("$MATCH/{$MATCH_ID}/$NEW_EVENT") { entry ->
                            EventEdit(checkNotNull(entry.arguments?.getString(MATCH_ID)), null, navController, offerUndo)
                        }
                        composable("$MATCH/{$MATCH_ID}/$EVENT/{$EVENT_ID}") { entry ->
                            val matchId = checkNotNull(entry.arguments?.getString(MATCH_ID))
                            EventEdit(matchId, checkNotNull(entry.arguments?.getString(EVENT_ID)), navController, offerUndo)
                        }
                    }
                    // Lifted above the edit screen's buttons
                    SnackbarHost(
                        snackbar,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun EventEdit(matchId: String, eventId: String?, navController: NavController, offerUndo: (DeletedRecording) -> Unit) {
        val viewModel: EventEditViewModel = viewModel(factory = EventEditViewModel.factory(application, matchId, eventId))
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    is EditEffect.Deleted -> offerUndo(effect.deleted)
                    is EditEffect.Finished -> {
                        effect.deleted?.let(offerUndo)
                        navController.popBackStack()
                    }
                }
            }
        }
        EventEditScreen(
            state = state,
            onExit = viewModel::exit,
            onDelete = viewModel::delete,
            onTogglePlayback = viewModel::togglePlayback,
            positionMillis = viewModel::positionMillis,
            onType = viewModel::setType,
            onTeam = viewModel::setTeam,
            onNote = viewModel::setNote,
            onPlayer = viewModel::setPlayer,
            onAssist = viewModel::setAssist,
            onLinkedGoal = viewModel::setLinkedGoal,
            onMinute = viewModel::setMinute,
            onSkip = viewModel::skip,
            onConfirm = viewModel::confirm,
            onAddAnother = viewModel::addAnother,
        )
    }

    private companion object {
        const val MATCHES = "matches"
        const val SYNC = "sync"
        const val SETTINGS = "settings"
        const val MATCH = "match"
        const val MATCH_ID = "matchId"
        const val LINEUP = "lineup"
        const val EVENT = "event"
        const val EVENT_ID = "eventId"
        const val NEW_EVENT = "new-event"

        /** The summary is stored on the phone, so it normally comes at once. */
        const val SUMMARY_WAIT_MILLIS = 2_000L
    }
}
