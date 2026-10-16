package io.github.spasarnaudov.trackertavex.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AssetsRepository
import io.github.spasarnaudov.trackertavex.core.data.PortfolioRepository
import io.github.spasarnaudov.trackertavex.core.model.Asset
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.Holding
import io.github.spasarnaudov.trackertavex.core.model.ManualItem
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryInterval
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryPoint
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class PortfolioUiState(
    val status: LoadStatus = LoadStatus.LOADING,
    val holdings: List<HoldingRowState> = emptyList(),
    val manualItems: List<ManualItemDraft> = emptyList(),
    val totalValue: BigDecimal? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val goldBuybackAssets: List<Asset> = emptyList(),
    val originalManualItemsById: Map<Long, ManualItemDraft> = emptyMap(),
    val historyStatus: LoadStatus = LoadStatus.LOADING,
    val historyRange: ChartRange = ChartRange.ONE_MONTH,
    val historyInterval: PortfolioHistoryInterval = PortfolioHistoryInterval.DAILY,
    val historyPoints: List<PortfolioHistoryPoint> = emptyList(),
    val historyErrorMessage: String? = null,
) {
    val hasUnsavedChanges: Boolean
        get() = holdings.any { it.isDirty } || manualItems.any { it.markedForDeletion } ||
            manualItems.any { draft -> draft.id == null } ||
            manualItems.any { draft ->
                draft.id != null && originalManualItemsById[draft.id]?.let {
                    it.name != draft.name || it.quantityText != draft.quantityText ||
                        it.unitPriceText != draft.unitPriceText || it.priceAssetId != draft.priceAssetId ||
                        it.includeInChart != draft.includeInChart
                } ?: true
            }
}

/** API.md: portfolio history does not support the `custom` range, unlike asset prices. */
val PortfolioHistoryRanges = ChartRange.entries.filterNot { it == ChartRange.CUSTOM }

/** No explicit Save button — edits autosave this long after the last change, like the web app. */
private const val AUTOSAVE_DEBOUNCE_MS = 800L

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val assetsRepository: AssetsRepository,
) : ViewModel() {

    private var autosaveJob: Job? = null

    /** Cancelled before every re-fetch so a slow, superseded range/interval response can never overwrite a newer one. */
    private var historyJob: Job? = null

    private var originalManualItemsSnapshot: Map<Long, ManualItemDraft> = emptyMap()

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun discardChanges() {
        autosaveJob?.cancel()
        val state = _uiState.value
        applyPortfolio(
            holdings = state.holdings.map {
                Holding(
                    it.assetId, it.assetSymbol, it.assetName, it.quantity, it.originalIncludeInChart,
                    it.price, it.value, it.costBasis, it.profit, it.profitPercent,
                )
            },
            manualItems = originalManualItemsSnapshot.values.map {
                ManualItem(it.id, it.name, BigDecimal(it.quantityText.ifBlank { "0" }), it.unitPriceText.toBigDecimalOrNull(), it.priceAssetId, it.includeInChart, it.value)
            },
            totalValue = state.totalValue,
            goldBuyback = state.goldBuybackAssets,
        )
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            val goldBuyback = when (val assets = assetsRepository.getAssets()) {
                is ApiResult.Success -> assets.data.goldBuybackAssets
                is ApiResult.Error -> emptyList()
            }
            when (val result = portfolioRepository.getPortfolio()) {
                is ApiResult.Success -> applyPortfolio(result.data.holdings, result.data.manualItems, result.data.totalValue, goldBuyback)
                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage())
                }
            }
            loadHistory()
        }
    }

    fun markManualItemForDeletion(clientKey: String) {
        _uiState.update { state ->
            val draft = state.manualItems.firstOrNull { it.clientKey == clientKey } ?: return@update state
            val updated = if (draft.id == null) {
                // Never persisted — just drop it locally.
                state.manualItems.filterNot { it.clientKey == clientKey }
            } else {
                state.manualItems.map { if (it.clientKey == clientKey) it.copy(markedForDeletion = true) else it }
            }
            state.copy(manualItems = updated)
        }
        scheduleAutosave()
    }

    fun retryHistory() {
        refreshHistory()
    }

    /**
     * Called directly by the UI (rare — e.g. "retry" after a failed save) and by the debounced
     * autosave triggered from every holding/manual-item edit. If a previous autosave is still
     * in flight, re-schedules rather than dropping the edit, so the latest state always ends up
     * persisted even if several edits land back-to-back.
     */
    fun save() {
        val state = _uiState.value
        if (state.isSaving) {
            scheduleAutosave()
            return
        }
        if (!state.hasUnsavedChanges) return

        val activeManualItems = state.manualItems.filter { !it.markedForDeletion || it.id != null }
        val hasInvalidManualQuantity = activeManualItems.any {
            !it.markedForDeletion && it.quantityText.toBigDecimalOrNull() == null
        }
        if (hasInvalidManualQuantity) return

        val hasInvalidUnitPrice = activeManualItems.any {
            !it.markedForDeletion && it.unitPriceText.isNotBlank() && it.unitPriceText.toBigDecimalOrNull() == null
        }
        if (hasInvalidUnitPrice) return

        val holdingsToSend = state.holdings.map {
            Holding(
                it.assetId, it.assetSymbol, it.assetName, it.quantity, it.includeInChart,
                it.price, it.value, it.costBasis, it.profit, it.profitPercent,
            )
        }
        val manualItemsToSend = activeManualItems.map { draft ->
            ManualItem(
                id = draft.id,
                name = draft.name,
                quantity = draft.quantityText.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                unitPrice = if (draft.priceAssetId == null) draft.unitPriceText.toBigDecimalOrNull() else null,
                priceAssetId = draft.priceAssetId,
                includeInChart = draft.includeInChart,
                value = draft.value,
                markedForDeletion = draft.markedForDeletion,
            )
        }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = portfolioRepository.updatePortfolio(holdingsToSend, manualItemsToSend)) {
                is ApiResult.Success -> {
                    val goldBuyback = state.goldBuybackAssets
                    applyPortfolio(result.data.holdings, result.data.manualItems, result.data.totalValue, goldBuyback)
                    _uiState.update { it.copy(isSaving = false) }
                    loadHistory()
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.error.toUserMessage())
                }
            }
        }
    }

    fun setHistoryInterval(interval: PortfolioHistoryInterval) {
        _uiState.update { it.copy(historyInterval = interval) }
        refreshHistory()
    }

    fun setHistoryRange(range: ChartRange) {
        _uiState.update { it.copy(historyRange = range) }
        refreshHistory()
    }

    fun toggleHoldingIncludeInChart(assetId: Long) {
        _uiState.update { state ->
            state.copy(
                holdings = state.holdings.map {
                    if (it.assetId == assetId) it.copy(includeInChart = !it.includeInChart) else it
                },
            )
        }
        scheduleAutosave()
    }

    fun upsertManualItem(draft: ManualItemDraft) {
        _uiState.update { state ->
            val exists = state.manualItems.any { it.clientKey == draft.clientKey }
            val updated = if (exists) {
                state.manualItems.map { if (it.clientKey == draft.clientKey) draft else it }
            } else {
                state.manualItems + draft
            }
            state.copy(manualItems = updated)
        }
        scheduleAutosave()
    }

    private fun applyPortfolio(
        holdings: List<Holding>,
        manualItems: List<ManualItem>,
        totalValue: BigDecimal?,
        goldBuyback: List<Asset>,
    ) {
        val holdingRows = holdings.map { HoldingRowState.from(it) }
        val manualDrafts = manualItems.map { ManualItemDraft.from(it) }
        originalManualItemsSnapshot = manualDrafts.filter { it.id != null }.associateBy { it.id!! }
        val isEmpty = holdingRows.isEmpty() && manualDrafts.isEmpty()
        _uiState.update { current ->
            PortfolioUiState(
                status = if (isEmpty) LoadStatus.EMPTY else LoadStatus.CONTENT,
                holdings = holdingRows,
                manualItems = manualDrafts,
                totalValue = totalValue,
                goldBuybackAssets = goldBuyback,
                originalManualItemsById = originalManualItemsSnapshot,
                historyStatus = current.historyStatus,
                historyRange = current.historyRange,
                historyInterval = current.historyInterval,
                historyPoints = current.historyPoints,
                historyErrorMessage = current.historyErrorMessage,
            )
        }
    }

    /** Suspends until the portfolio-value chart for the current [PortfolioUiState.historyRange]/[PortfolioUiState.historyInterval] loads. */
    private suspend fun loadHistory() {
        val state = _uiState.value
        _uiState.update { it.copy(historyStatus = LoadStatus.LOADING, historyErrorMessage = null) }
        when (val result = portfolioRepository.getPortfolioHistory(state.historyRange, state.historyInterval)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(
                    historyStatus = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT,
                    historyPoints = result.data,
                )
            }

            is ApiResult.Error -> _uiState.update {
                it.copy(historyStatus = LoadStatus.ERROR, historyErrorMessage = result.error.toUserMessage())
            }
        }
    }

    private fun refreshHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch { loadHistory() }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            save()
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (isBlank()) null else BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
