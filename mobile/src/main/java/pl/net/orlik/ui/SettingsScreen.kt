package pl.net.orlik.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.net.orlik.R
import pl.net.orlik.ui.theme.Disallowed
import pl.net.orlik.ui.theme.GreenDark
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.ReviewText
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

/** Settings: speech recognition on/off and the OpenAI key, pasted (design doc, section 10). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSttEnabled: (Boolean) -> Unit,
    onDraft: (String) -> Unit,
    onSaveKey: () -> Unit,
    onCheckKey: () -> Unit,
    onChangeKey: () -> Unit,
    onCancelChange: () -> Unit,
    onDeleteKey: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = OnStadium,
        topBar = {
            TopAppBar(
                colors = stadiumTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.stt_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.stt_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.sttEnabled, onCheckedChange = onSttEnabled)
                    }
                    if (state.sttEnabled) {
                        HorizontalDivider()
                        ApiKey(state, onDraft, onSaveKey, onCheckKey, onChangeKey, onCancelChange, onDeleteKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKey(
    state: SettingsUiState,
    onDraft: (String) -> Unit,
    onSaveKey: () -> Unit,
    onCheckKey: () -> Unit,
    onChangeKey: () -> Unit,
    onCancelChange: () -> Unit,
    onDeleteKey: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.api_key_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.editingKey) {
            // Hidden like a password; the paste button saves typing a long key by hand
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraft,
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { clipboardText(context)?.let(onDraft) }) {
                        Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.paste))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.api_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSaveKey, enabled = state.draft.isNotBlank() && !state.checking) {
                    Text(stringResource(R.string.save))
                }
                if (state.savedKey != null) {
                    TextButton(onClick = onCancelChange) { Text(stringResource(R.string.cancel)) }
                }
                Checking(state.checking)
            }
        } else {
            Text(stringResource(R.string.api_key_saved, state.savedKey.orEmpty()), fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCheckKey, enabled = !state.checking) { Text(stringResource(R.string.check)) }
                TextButton(onClick = onChangeKey) { Text(stringResource(R.string.change)) }
                TextButton(onClick = onDeleteKey) { Text(stringResource(R.string.delete)) }
                Checking(state.checking)
            }
        }
        state.message?.let { message ->
            val (text, color) = when (message) {
                KeyMessage.WORKS -> R.string.key_works to GreenDark
                KeyMessage.REJECTED -> R.string.key_rejected to Disallowed
                KeyMessage.RATE_LIMITED -> R.string.key_rate_limited to ReviewText
                KeyMessage.NOT_CHECKED -> R.string.key_not_checked to ReviewText
                KeyMessage.SERVICE_ERROR -> R.string.key_service_error to ReviewText
            }
            Text(stringResource(text), color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Checking(checking: Boolean) {
    if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
}

private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

@Preview(showBackground = true, backgroundColor = 0xFF061430, widthDp = 390, heightDp = 600)
@Composable
private fun SettingsScreenPreview() {
    MatchNotesTheme {
        SettingsScreen(
            state = SettingsUiState(sttEnabled = true, savedKey = "sk-…a1B2", message = KeyMessage.WORKS),
            onBack = {},
            onSttEnabled = {},
            onDraft = {},
            onSaveKey = {},
            onCheckKey = {},
            onChangeKey = {},
            onCancelChange = {},
            onDeleteKey = {},
        )
    }
}
