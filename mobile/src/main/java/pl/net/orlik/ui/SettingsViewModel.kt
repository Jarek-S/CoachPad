package pl.net.orlik.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.net.orlik.stt.SttError
import pl.net.orlik.stt.SttSettings
import pl.net.orlik.stt.checkOpenAiKey
import pl.net.orlik.stt.cleanApiKey
import pl.net.orlik.stt.maskedApiKey

/** What checking the key found; shown under the key. */
enum class KeyMessage { WORKS, REJECTED, RATE_LIMITED, NOT_CHECKED, SERVICE_ERROR }

data class SettingsUiState(
    val sttEnabled: Boolean = false,
    /** The saved key as "sk-…a1B2"; null when there is none. */
    val savedKey: String? = null,
    /** The paste field is shown: no key saved yet, or "Zmień". */
    val editingKey: Boolean = false,
    val draft: String = "",
    val checking: Boolean = false,
    val message: KeyMessage? = null,
)

class SettingsViewModel(private val settings: SttSettings) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(sttEnabled = settings.enabled))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val key = settings.apiKey()
            _state.update { it.copy(savedKey = key?.let(::maskedApiKey), editingKey = key == null) }
        }
    }

    fun setSttEnabled(enabled: Boolean) {
        settings.enabled = enabled
        _state.update { it.copy(sttEnabled = enabled) }
    }

    fun setDraft(text: String) = _state.update { it.copy(draft = text, message = null) }

    fun changeKey() = _state.update { it.copy(editingKey = true, draft = "", message = null) }

    fun cancelChange() = _state.update { it.copy(editingKey = it.savedKey == null, draft = "", message = null) }

    fun deleteKey() {
        settings.clearApiKey()
        _state.update { it.copy(savedKey = null, editingKey = true, draft = "", message = null) }
    }

    /** Checked first: a key OpenAI rejects is not saved; without a connection it is saved unchecked. */
    fun saveKey() {
        val key = cleanApiKey(_state.value.draft)
        if (key.isEmpty() || _state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val error = checkOpenAiKey(key)
            if (error == SttError.UNAUTHORIZED) {
                _state.update { it.copy(checking = false, message = KeyMessage.REJECTED) }
                return@launch
            }
            settings.setApiKey(key)
            _state.update {
                it.copy(checking = false, savedKey = maskedApiKey(key), editingKey = false, draft = "", message = messageOf(error))
            }
        }
    }

    /** Checks the saved key again, e.g. after topping up the account. */
    fun checkKey() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val key = settings.apiKey()
            val message = if (key == null) KeyMessage.REJECTED else messageOf(checkOpenAiKey(key))
            _state.update { it.copy(checking = false, message = message) }
        }
    }

    private fun messageOf(error: SttError?): KeyMessage = when (error) {
        null -> KeyMessage.WORKS
        SttError.UNAUTHORIZED -> KeyMessage.REJECTED
        SttError.RATE_LIMITED -> KeyMessage.RATE_LIMITED
        SttError.NETWORK -> KeyMessage.NOT_CHECKED
        SttError.SERVICE -> KeyMessage.SERVICE_ERROR
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(SttSettings(app)) }
        }
    }
}
