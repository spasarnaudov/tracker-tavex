package io.github.spasarnaudov.trackertavex.feature.assets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AssetsRepository
import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.model.AssetPriceInterval
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.PricePoint
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
import java.time.LocalDate
import javax.inject.Inject

data class AssetDetailUiState(
    val assetId: Long,
    val symbol: String,
    val name: String,
    val status: LoadStatus = LoadStatus.LOADING,
    val range: ChartRange = ChartRange.ONE_MONTH,
    val interval: AssetPriceInterval = AssetPriceInterval.DAILY,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val points: List<PricePoint> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val assetsRepository: AssetsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private companion object {
        fun decode(value: String?): String = value?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
    }

    private val _uiState = MutableStateFlow(
        AssetDetailUiState(
            assetId = checkNotNull(savedStateHandle.get<String>(Destinations.ASSET_DETAIL_ID_ARG)).toLong(),
            symbol = decode(savedStateHandle.get<String>(Destinations.ASSET_DETAIL_SYMBOL_ARG)),
            name = decode(savedStateHandle.get<String>(Destinations.ASSET_DETAIL_NAME_ARG)),
        ),
    )
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun applyCustomRange() {
        load()
    }

    fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = assetsRepository.getAssetPrices(
                    assetId = state.assetId,
                    range = state.range,
                    interval = state.interval,
                    startDate = state.customStartDate,
                    endDate = state.customEndDate,
                )
            ) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(status = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT, points = result.data)
                }

                is ApiResult.Error -> _uiState.update {
                    val message = (result.error as? AppError.BadRequest)?.message ?: result.error.toUserMessage()
                    it.copy(status = LoadStatus.ERROR, errorMessage = message)
                }
            }
        }
    }

    fun setCustomDates(start: LocalDate?, end: LocalDate?) {
        _uiState.update { it.copy(customStartDate = start, customEndDate = end) }
    }

    fun setInterval(interval: AssetPriceInterval) {
        _uiState.update { it.copy(interval = interval) }
        load()
    }

    fun setRange(range: ChartRange) {
        _uiState.update { it.copy(range = range) }
        if (range != ChartRange.CUSTOM) load()
    }
}
