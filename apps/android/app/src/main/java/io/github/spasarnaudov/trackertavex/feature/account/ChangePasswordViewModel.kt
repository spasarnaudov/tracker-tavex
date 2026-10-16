package io.github.spasarnaudov.trackertavex.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AccountRepository
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

private const val MIN_PASSWORD_LENGTH = 8

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val currentPasswordError: String? = null,
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
) {
    val canSubmit: Boolean
        get() = currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val _success = MutableSharedFlow<Unit>()
    val success: SharedFlow<Unit> = _success.asSharedFlow()

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, errorMessage = null) }
    }

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, currentPasswordError = null, errorMessage = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, newPasswordError = null, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        val newPasswordError = when {
            state.newPassword.isBlank() -> "New password is required."
            state.newPassword.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
            state.newPassword == state.currentPassword -> "New password must be different from the current one."
            else -> null
        }
        val confirmError = if (state.confirmPassword != state.newPassword) "Passwords do not match." else null
        val currentError = if (state.currentPassword.isBlank()) "Current password is required." else null

        if (newPasswordError != null || confirmError != null || currentError != null) {
            _uiState.update {
                it.copy(newPasswordError = newPasswordError, confirmPasswordError = confirmError, currentPasswordError = currentError)
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = accountRepository.changePassword(state.currentPassword, state.newPassword)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _success.emit(Unit)
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
