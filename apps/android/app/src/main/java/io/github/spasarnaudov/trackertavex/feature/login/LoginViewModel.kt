package io.github.spasarnaudov.trackertavex.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AuthRepository
import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val showActiveSessionDialog: Boolean = false,
) {
    val canSubmit: Boolean get() = username.isNotBlank() && password.isNotBlank() && !isSubmitting
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _loginSuccess = MutableSharedFlow<Unit>()
    val loginSuccess: SharedFlow<Unit> = _loginSuccess.asSharedFlow()

    fun confirmForceLogin() {
        _uiState.update { it.copy(showActiveSessionDialog = false) }
        attemptLogin(force = true)
    }

    fun dismissActiveSessionDialog() {
        _uiState.update { it.copy(showActiveSessionDialog = false) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        val usernameError = if (state.username.isBlank()) "Username is required." else null
        val passwordError = if (state.password.isBlank()) "Password is required." else null
        if (usernameError != null || passwordError != null) {
            _uiState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }
        attemptLogin(force = false)
    }

    private fun attemptLogin(force: Boolean) {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(state.username.trim(), state.password, force)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _loginSuccess.emit(Unit)
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        if (result.error is AppError.ActiveSessionConflict) {
                            it.copy(isSubmitting = false, showActiveSessionDialog = true)
                        } else {
                            it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage())
                        }
                    }
                }
            }
        }
    }
}
