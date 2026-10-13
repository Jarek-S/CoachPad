package pl.net.orlik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import pl.net.orlik.sync.QueueSummarySource
import pl.net.orlik.ui.EditEffect
import pl.net.orlik.ui.EventEditScreen
import pl.net.orlik.ui.EventEditViewModel
import pl.net.orlik.ui.MatchReviewScreen
import pl.net.orlik.ui.MatchReviewViewModel
import pl.net.orlik.ui.MatchesScreen
import pl.net.orlik.ui.MatchesViewModel
import pl.net.orlik.ui.SyncScreen
import pl.net.orlik.ui.SyncViewModel
import pl.net.orlik.ui.theme.MatchNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val edits = EventEdits(PhoneDatabase.get(this).matchEventDao(), recordingsDir(this))
        // Audio of recordings deleted while the app last closed with "Cofnij" still offered
        lifecycleScope.launch { edits.purgeAll() }
        setContent {
            MatchNotesTheme {
                // Above all screens, so "Cofnij" stays when the edit screen returns to the list
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val undoMessage = stringResource(R.string.recording_deleted)
                val undoLabel = stringResource(R.string.undo)
                val offerUndo: (DeletedRecording) -> Unit = { deleted ->
                    // A previous offer ends (and its audio goes) when another recording is deleted
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launch {
                        val result = snackbar.showSnackbar(undoMessage, undoLabel, duration = SnackbarDuration.Short)
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
                Box(Modifier.fillMaxSize()) {
                    NavHost(navController, startDestination = MATCHES) {
                        composable(MATCHES) {
                            val viewModel: MatchesViewModel = viewModel(factory = MatchesViewModel.factory(application))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            MatchesScreen(
                                state = state,
                                onOpenMatch = { navController.navigate("$MATCH/$it") },
                                onOpenSync = { navController.navigate(SYNC) },
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
                                onTeamNames = viewModel::setTeamNames,
                            )
                        }
                        composable("$MATCH/{$MATCH_ID}/$EVENT/{$EVENT_ID}") { entry ->
                            val matchId = checkNotNull(entry.arguments?.getString(MATCH_ID))
                            val eventId = checkNotNull(entry.arguments?.getString(EVENT_ID))
                            val viewModel: EventEditViewModel =
                                viewModel(factory = EventEditViewModel.factory(application, matchId, eventId))
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
                                onLinkedGoal = viewModel::setLinkedGoal,
                                onSkip = viewModel::skip,
                                onConfirm = viewModel::confirm,
                            )
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

    private companion object {
        const val MATCHES = "matches"
        const val SYNC = "sync"
        const val MATCH = "match"
        const val MATCH_ID = "matchId"
        const val EVENT = "event"
        const val EVENT_ID = "eventId"

        /** The summary is stored on the phone, so it normally comes at once. */
        const val SUMMARY_WAIT_MILLIS = 2_000L
    }
}
