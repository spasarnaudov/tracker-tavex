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

data class DeleteAccountUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    private val _accountDeleted = MutableSharedFlow<Unit>()
    val accountDeleted: SharedFlow<Unit> = _accountDeleted.asSharedFlow()

    fun confirmDeletion() {
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = accountRepository.deactivateAccount()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _accountDeleted.emit(Unit)
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
