package io.github.spasarnaudov.trackertavex.feature.portfolio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.spasarnaudov.trackertavex.core.data.PortfolioRepository
import io.github.spasarnaudov.trackertavex.core.data.PurchaseValidator
import io.github.spasarnaudov.trackertavex.core.model.AssetPurchase
import io.github.spasarnaudov.trackertavex.core.network.ApiConfigProvider
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import io.github.spasarnaudov.trackertavex.navigation.Destinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.net.URLDecoder
import java.time.LocalDate
import javax.inject.Inject

data class AssetPurchasesUiState(
    val assetId: Long,
    val assetSymbol: String,
    val assetName: String,
    val status: LoadStatus = LoadStatus.LOADING,
    val purchases: List<AssetPurchase> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val formError: String? = null,
)

@HiltViewModel
class AssetPurchasesViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val apiConfigProvider: ApiConfigProvider,
    @ApplicationContext private val context: Context,
    val imageLoader: ImageLoader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private companion object {
        fun decode(value: String?): String = value?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
    }

    private val _uiState = MutableStateFlow(
        AssetPurchasesUiState(
            assetId = checkNotNull(savedStateHandle.get<String>(Destinations.ASSET_PURCHASES_ID_ARG)).toLong(),
            assetSymbol = decode(savedStateHandle.get<String>(Destinations.ASSET_PURCHASES_SYMBOL_ARG)),
            assetName = decode(savedStateHandle.get<String>(Destinations.ASSET_PURCHASES_NAME_ARG)),
        ),
    )
    val uiState: StateFlow<AssetPurchasesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = LoadStatus.LOADING, errorMessage = null) }
        viewModelScope.launch {
            when (val result = portfolioRepository.getAssetPurchases(_uiState.value.assetId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        status = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT,
                        purchases = result.data.sortedByDescending { purchase -> purchase.purchaseDate },
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(status = LoadStatus.ERROR, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearFormError() {
        _uiState.update { it.copy(formError = null) }
    }

    fun addPurchase(quantity: BigDecimal?, purchasePrice: BigDecimal?, purchaseDate: LocalDate?, onSaved: () -> Unit) {
        val errors = PurchaseValidator.validate(quantity, purchasePrice, purchaseDate)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(formError = errors.first()) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val result = portfolioRepository.addAssetPurchase(_uiState.value.assetId, quantity!!, purchasePrice!!, purchaseDate!!)
            handleSaveResult(result, onSaved)
        }
    }

    fun updatePurchase(
        purchaseId: Long,
        quantity: BigDecimal?,
        purchasePrice: BigDecimal?,
        purchaseDate: LocalDate?,
        onSaved: () -> Unit,
    ) {
        val errors = PurchaseValidator.validate(quantity, purchasePrice, purchaseDate)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(formError = errors.first()) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val result = portfolioRepository.updateAssetPurchase(purchaseId, quantity!!, purchasePrice!!, purchaseDate!!)
            handleSaveResult(result, onSaved)
        }
    }

    fun deletePurchase(purchaseId: Long) {
        viewModelScope.launch {
            when (val result = portfolioRepository.deleteAssetPurchase(purchaseId)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    /** Absolute URL a Coil `AsyncImage` can load directly (authenticated via [imageLoader]). */
    fun receiptUrl(purchaseId: Long): String = "${apiConfigProvider.currentBaseUrl}portfolio/purchases/$purchaseId/receipt"

    fun uploadReceipt(purchaseId: Long, uri: Uri) {
        viewModelScope.launch {
            when (val result = portfolioRepository.uploadPurchaseReceipt(purchaseId, uri, context.contentResolver)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun removeReceipt(purchaseId: Long) {
        viewModelScope.launch {
            when (val result = portfolioRepository.deletePurchaseReceipt(purchaseId)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    private fun handleSaveResult(result: ApiResult<AssetPurchase>, onSaved: () -> Unit) {
        when (result) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(isSaving = false) }
                load()
                onSaved()
            }

            is ApiResult.Error -> _uiState.update {
                it.copy(isSaving = false, formError = result.error.toUserMessage())
            }
        }
    }
}
