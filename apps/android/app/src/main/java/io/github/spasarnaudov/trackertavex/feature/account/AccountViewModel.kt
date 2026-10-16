package io.github.spasarnaudov.trackertavex.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AccountRepository
import io.github.spasarnaudov.trackertavex.core.data.AuthRepository
import io.github.spasarnaudov.trackertavex.core.model.User
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
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

data class AccountUiState(
    val status: LoadStatus = LoadStatus.LOADING,
    val user: User? = null,
    val errorMessage: String? = null,
    val isLoggingOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _loggedOut = MutableSharedFlow<Unit>()
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            when (val result = accountRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update { it.copy(status = LoadStatus.CONTENT, user = result.data) }
                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun logout() {
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggingOut = false) }
            _loggedOut.emit(Unit)
        }
    }
}
