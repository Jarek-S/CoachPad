package pl.net.orlik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.net.orlik.ui.SyncScreen
import pl.net.orlik.ui.SyncViewModel
import pl.net.orlik.ui.theme.MatchNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MatchNotesTheme {
                val viewModel: SyncViewModel = viewModel(factory = SyncViewModel.factory(application))
                val state by viewModel.state.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SyncScreen(state = state, onSync = viewModel::sync, contentPadding = innerPadding)
                }
            }
        }
    }
}
