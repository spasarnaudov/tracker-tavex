package io.github.spasarnaudov.trackertavex.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AssetsRepository
import io.github.spasarnaudov.trackertavex.core.model.Asset
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetsUiState(
    val status: LoadStatus = LoadStatus.LOADING,
    val assets: List<Asset> = emptyList(),
    val goldBuybackAssets: List<Asset> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val assetsRepository: AssetsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState: StateFlow<AssetsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            when (val result = assetsRepository.getAssets()) {
                is ApiResult.Success -> _uiState.update {
                    val empty = result.data.assets.isEmpty() && result.data.goldBuybackAssets.isEmpty()
                    it.copy(
                        status = if (empty) LoadStatus.EMPTY else LoadStatus.CONTENT,
                        assets = result.data.assets,
                        goldBuybackAssets = result.data.goldBuybackAssets,
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
