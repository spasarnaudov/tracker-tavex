package io.github.spasarnaudov.trackertavex.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AdminRepository
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import io.github.spasarnaudov.trackertavex.navigation.Destinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class LogDetailUiState(
    val name: String,
    val status: LoadStatus = LoadStatus.LOADING,
    val lines: List<String> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class LogDetailViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val logName: String = URLDecoder.decode(
        checkNotNull(savedStateHandle.get<String>(Destinations.ADMIN_LOG_DETAIL_ARG)),
        "UTF-8",
    )

    private val _uiState = MutableStateFlow(LogDetailUiState(name = logName))
    val uiState: StateFlow<LogDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            when (val result = adminRepository.getLogContent(logName)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(status = if (result.data.lines.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT, lines = result.data.lines)
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
