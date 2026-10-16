package io.github.spasarnaudov.trackertavex.feature.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AssetsRepository
import io.github.spasarnaudov.trackertavex.core.data.ChartsRepository
import io.github.spasarnaudov.trackertavex.core.data.PortfolioRepository
import io.github.spasarnaudov.trackertavex.core.model.Asset
import io.github.spasarnaudov.trackertavex.core.model.AssetPriceInterval
import io.github.spasarnaudov.trackertavex.core.model.ChartConfiguration
import io.github.spasarnaudov.trackertavex.core.model.ChartDefinition
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryInterval
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.ChartPoint
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChartsUiState(
    val status: LoadStatus = LoadStatus.LOADING,
    val charts: List<ChartDefinition> = emptyList(),
    val chartData: Map<String, List<ChartPoint>> = emptyMap(),
    val chartLoading: Set<String> = emptySet(),
    val assets: List<Asset> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val chartsRepository: ChartsRepository,
    private val assetsRepository: AssetsRepository,
    private val portfolioRepository: PortfolioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun addChart(chart: ChartDefinition) {
        _uiState.update { it.copy(charts = it.charts + chart) }
        loadChartData(chart)
        persist()
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            val assets = when (val result = assetsRepository.getAssets()) {
                is ApiResult.Success -> result.data.assets
                is ApiResult.Error -> emptyList()
            }
            when (val result = chartsRepository.getConfiguration()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            status = if (result.data.charts.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT,
                            charts = result.data.charts,
                            assets = assets,
                        )
                    }
                    result.data.charts.forEach { loadChartData(it) }
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage(), assets = assets)
                }
            }
        }
    }

    fun removeChart(chartId: String) {
        _uiState.update { state ->
            state.copy(
                charts = state.charts.filterNot { it.id == chartId },
                chartData = state.chartData - chartId,
            )
        }
        persist()
    }

    fun updateChart(chart: ChartDefinition) {
        _uiState.update { state -> state.copy(charts = state.charts.map { if (it.id == chart.id) chart else it }) }
        loadChartData(chart)
        persist()
    }

    private fun loadChartData(chart: ChartDefinition) {
        _uiState.update { it.copy(chartLoading = it.chartLoading + chart.id) }
        viewModelScope.launch {
            val points = if (chart.assetId != null) {
                when (val result = assetsRepository.getAssetPrices(chart.assetId, chart.range, chart.interval, chart.startDate, chart.endDate)) {
                    is ApiResult.Success -> result.data.map { ChartPoint(it.timestamp, it.price) }
                    is ApiResult.Error -> emptyList()
                }
            } else {
                val historyRange = if (chart.range == ChartRange.CUSTOM) ChartRange.ALL else chart.range
                val historyInterval = when (chart.interval) {
                    AssetPriceInterval.HOURLY -> PortfolioHistoryInterval.HOURLY
                    AssetPriceInterval.WEEKLY -> PortfolioHistoryInterval.WEEKLY
                    else -> PortfolioHistoryInterval.DAILY
                }
                when (val result = portfolioRepository.getPortfolioHistory(historyRange, historyInterval)) {
                    is ApiResult.Success -> result.data.map { ChartPoint(it.timestamp, it.value) }
                    is ApiResult.Error -> emptyList()
                }
            }
            _uiState.update {
                it.copy(
                    chartData = it.chartData + (chart.id to points),
                    chartLoading = it.chartLoading - chart.id,
                )
            }
        }
    }

    private fun persist() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = chartsRepository.updateConfiguration(ChartConfiguration(state.charts))) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSaving = false, status = if (it.charts.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT)
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.error.toUserMessage())
                }
            }
        }
    }
}
