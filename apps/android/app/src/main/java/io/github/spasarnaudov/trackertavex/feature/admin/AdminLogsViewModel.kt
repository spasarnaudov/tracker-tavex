package io.github.spasarnaudov.trackertavex.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AdminRepository
import io.github.spasarnaudov.trackertavex.core.model.LogFile
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminLogsUiState(
    val status: LoadStatus = LoadStatus.LOADING,
    val files: List<LogFile> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminLogsViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminLogsUiState())
    val uiState: StateFlow<AdminLogsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            when (val result = adminRepository.getLogFiles()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(status = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT, files = result.data)
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
