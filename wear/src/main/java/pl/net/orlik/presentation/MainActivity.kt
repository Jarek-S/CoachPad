package pl.net.orlik.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import pl.net.orlik.presentation.theme.MatchNotesTheme
import pl.net.orlik.presentation.theme.StadiumBackground
import pl.net.orlik.settings.SettingsScreen
import pl.net.orlik.settings.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnIfAsked(intent)
        setContent {
            WearApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        turnScreenOnIfAsked(intent)
    }

    override fun onStop() {
        super.onStop()
        // Only the alarm turns the screen on; a normal return to the app must not
        setTurnScreenOn(false)
    }

    /** The shift timer alarm opens this screen with the screen off. */
    private fun turnScreenOnIfAsked(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_TURN_SCREEN_ON, false)) setTurnScreenOn(true)
    }

    companion object {
        const val EXTRA_TURN_SCREEN_ON = "turn_screen_on"
    }
}

private const val ROUTE_MATCH = "match"
private const val ROUTE_SETTINGS = "settings"

/** App root: switches between screens by route; swiping right goes back. */
@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    MatchNotesTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_MATCH) {
                composable(ROUTE_MATCH) {
                    StadiumBackground { MatchRoute(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }) }
                }
                composable(ROUTE_SETTINGS) {
                    StadiumBackground { SettingsRoute() }
                }
            }
        }
    }
}

@Composable
private fun MatchRoute(
    onOpenSettings: () -> Unit,
    viewModel: MatchViewModel = viewModel(factory = MatchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // System dialog asking for microphone access; recording starts once granted
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleRecording()
    }
    // Asked once, before the first match: shows the notification of the match service.
    // The match starts either way; without permission the service runs with a hidden notification.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startMatch()
    }
    // Leaving the screen ends a recording in progress (background apps get a muted microphone)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopRecording(StopReason.APP_HIDDEN) }

    ScreenScaffold(
        // During a match the wall-clock time is one tap away on the match clock (docs section 10)
        timeText = if (state is MatchUiState.InProgress) ({}) else null,
    ) {
        MatchScreen(
            state = state,
            onStartMatch = {
                val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.startMatch()
            },
            onOpenSettings = onOpenSettings,
            onConfirmShift = viewModel::confirmShift,
            onToggleShiftTimer = viewModel::toggleShiftTimer,
            onToggleRecording = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.toggleRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onTogglePause = viewModel::togglePause,
            onEndMatch = viewModel::endMatch,
        )
    }
}

@Composable
private fun SettingsRoute(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        onShiftTimerEnabledChange = viewModel::setShiftTimerEnabled,
        onIntervalChange = viewModel::changeInterval,
    )
}
