package io.github.spasarnaudov.trackertavex.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.BuildConfig
import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.core.auth.TokenStorage
import io.github.spasarnaudov.trackertavex.core.data.ConnectionUrlValidator
import io.github.spasarnaudov.trackertavex.core.network.ApiConfigProvider
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.network.AuthInterceptor
import io.github.spasarnaudov.trackertavex.core.network.DynamicApiServiceHolder
import io.github.spasarnaudov.trackertavex.core.network.UnauthorizedInterceptor
import io.github.spasarnaudov.trackertavex.core.network.apiCall
import io.github.spasarnaudov.trackertavex.core.storage.SettingsDataStore
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionSettingsUiState(
    val baseUrlText: String = BuildConfig.API_BASE_URL,
    val isUsingOverride: Boolean = false,
    val validationError: String? = null,
    val isTesting: Boolean = false,
    val testResultMessage: String? = null,
    val testSucceeded: Boolean? = null,
    val pendingSaveUrl: String? = null,
    val showClearSessionConfirm: Boolean = false,
    val isSaved: Boolean = false,
)

@HiltViewModel
class ConnectionSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val apiConfigProvider: ApiConfigProvider,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
    private val authInterceptor: AuthInterceptor,
    private val unauthorizedInterceptor: UnauthorizedInterceptor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionSettingsUiState())
    val uiState: StateFlow<ConnectionSettingsUiState> = _uiState.asStateFlow()

    private val _sessionCleared = MutableSharedFlow<Unit>()
    val sessionCleared: SharedFlow<Unit> = _sessionCleared.asSharedFlow()

    init {
        viewModelScope.launch {
            val override = settingsDataStore.baseUrlOverride.first()
            _uiState.update {
                it.copy(baseUrlText = override ?: BuildConfig.API_BASE_URL, isUsingOverride = override != null)
            }
        }
    }

    fun confirmSaveAndClearSession() {
        val url = _uiState.value.pendingSaveUrl ?: return
        persistUrl(url)
    }

    fun dismissClearSessionConfirm() {
        _uiState.update { it.copy(showClearSessionConfirm = false, pendingSaveUrl = null) }
    }

    fun onUrlChange(value: String) {
        _uiState.update { it.copy(baseUrlText = value, validationError = null, testResultMessage = null, testSucceeded = null) }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            settingsDataStore.setBaseUrlOverride(null)
            _uiState.update {
                it.copy(baseUrlText = BuildConfig.API_BASE_URL, isUsingOverride = false, testResultMessage = null, testSucceeded = null, isSaved = true)
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val error = ConnectionUrlValidator.validate(state.baseUrlText)
        if (error != null) {
            _uiState.update { it.copy(validationError = error) }
            return
        }
        val normalized = ApiConfigProvider.normalize(state.baseUrlText) ?: return
        if (sessionManager.currentUser.value != null) {
            _uiState.update { it.copy(pendingSaveUrl = normalized, showClearSessionConfirm = true) }
        } else {
            persistUrl(normalized)
        }
    }

    fun testConnection() {
        val state = _uiState.value
        val error = ConnectionUrlValidator.validate(state.baseUrlText)
        if (error != null) {
            _uiState.update { it.copy(validationError = error) }
            return
        }
        val normalized = ApiConfigProvider.normalize(state.baseUrlText) ?: return
        _uiState.update { it.copy(isTesting = true, testResultMessage = null, testSucceeded = null) }
        viewModelScope.launch {
            val adHocService = DynamicApiServiceHolder.buildAdHoc(normalized, authInterceptor, unauthorizedInterceptor)
            when (val result = apiCall { adHocService.health() }) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isTesting = false, testSucceeded = true, testResultMessage = "Connected successfully.")
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isTesting = false, testSucceeded = false, testResultMessage = result.error.toUserMessage())
                }
            }
        }
    }

    private fun persistUrl(url: String) {
        viewModelScope.launch {
            // Never send a token from one server to another: clear any local session first.
            val hadSession = sessionManager.currentUser.value != null
            tokenStorage.clear()
            sessionManager.setUser(null)
            settingsDataStore.setBaseUrlOverride(url)
            _uiState.update {
                it.copy(
                    baseUrlText = url,
                    isUsingOverride = true,
                    showClearSessionConfirm = false,
                    pendingSaveUrl = null,
                    isSaved = true,
                )
            }
            if (hadSession) _sessionCleared.emit(Unit)
        }
    }
}
