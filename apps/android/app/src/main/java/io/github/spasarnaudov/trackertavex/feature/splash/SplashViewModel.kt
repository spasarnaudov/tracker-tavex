package io.github.spasarnaudov.trackertavex.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AuthRepository
import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashUiState {
    data object Loading : SplashUiState
    data object NeedsLogin : SplashUiState
    data object SessionRestored : SplashUiState
    data class NetworkErrorRetry(val message: String) : SplashUiState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        _uiState.value = SplashUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.restoreSession()) {
                is ApiResult.Success -> _uiState.value = SplashUiState.SessionRestored
                is ApiResult.Error -> _uiState.value = when (result.error) {
                    is AppError.Network -> SplashUiState.NetworkErrorRetry(result.error.toUserMessage())
                    else -> SplashUiState.NeedsLogin
                }
            }
        }
    }
}
