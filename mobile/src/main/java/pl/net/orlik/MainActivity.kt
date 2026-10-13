package pl.net.orlik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        setContent {
            MatchNotesTheme {
                // Screens by route: the match list is the start, sync and a match's review open from it
                val navController = rememberNavController()
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
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val MATCHES = "matches"
        const val SYNC = "sync"
        const val MATCH = "match"
        const val MATCH_ID = "matchId"
    }
}
