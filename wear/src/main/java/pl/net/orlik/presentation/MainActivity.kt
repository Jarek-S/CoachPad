package pl.net.orlik.presentation

import android.Manifest
import android.content.pm.PackageManager
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
import pl.net.orlik.presentation.theme.MatchNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp(viewModel: MatchViewModel = viewModel(factory = MatchViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // System dialog asking for microphone access; recording starts once granted
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleRecording()
    }
    // Leaving the screen ends a recording in progress (background apps get a muted microphone)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopRecording() }

    MatchNotesTheme {
        AppScaffold {
            ScreenScaffold(
                // During a match the wall-clock time is one tap away on the match clock (docs section 10)
                timeText = if (state is MatchUiState.InProgress) ({}) else null,
            ) {
                MatchScreen(
                    state = state,
                    onStartMatch = viewModel::startMatch,
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
    }
}
