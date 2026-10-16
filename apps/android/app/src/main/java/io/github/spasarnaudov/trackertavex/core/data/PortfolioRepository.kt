package io.github.spasarnaudov.trackertavex.core.data

import android.content.ContentResolver
import android.net.Uri
import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.model.AssetPurchase
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.Holding
import io.github.spasarnaudov.trackertavex.core.model.ManualItem
import io.github.spasarnaudov.trackertavex.core.model.Portfolio
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryInterval
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryPoint
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.network.ApiService
import io.github.spasarnaudov.trackertavex.core.network.apiCall
import io.github.spasarnaudov.trackertavex.core.network.dto.AssetPurchaseRequestDto
import io.github.spasarnaudov.trackertavex.core.network.dto.AssetPurchaseResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.HoldingUpdateDto
import io.github.spasarnaudov.trackertavex.core.network.dto.ManualItemUpdateDto
import io.github.spasarnaudov.trackertavex.core.network.dto.PortfolioUpdateRequestDto
import io.github.spasarnaudov.trackertavex.core.network.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class PortfolioRepository @Inject constructor(
    private val apiServiceProvider: Provider<ApiService>,
) {
    private val apiService: ApiService get() = apiServiceProvider.get()

    suspend fun getPortfolio(): ApiResult<Portfolio> {
        val result = apiCall { apiService.getPortfolio() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> result
        }
    }

    suspend fun getPortfolioHistory(
        range: ChartRange,
        interval: PortfolioHistoryInterval,
    ): ApiResult<List<PortfolioHistoryPoint>> {
        val result = apiCall { apiService.getPortfolioHistory(range.wireValue, interval.wireValue) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.points.mapNotNull { it.toDomain() })
            is ApiResult.Error -> result
        }
    }

    /** Validates locally first (never sends negative quantities/prices), then calls `PUT /portfolio`. */
    suspend fun updatePortfolio(holdings: List<Holding>, manualItems: List<ManualItem>): ApiResult<Portfolio> {
        val errors = PortfolioValidator.validate(manualItems)
        if (errors.isNotEmpty()) {
            return ApiResult.Error(AppError.ValidationFailed(errors.first(), errors))
        }

        val holdingsDto = holdings.map { HoldingUpdateDto(it.assetId, it.includeInChart) }
        val manualItemsDto = manualItems.map {
            ManualItemUpdateDto(
                id = it.id,
                name = it.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                priceAssetId = it.priceAssetId,
                includeInChart = it.includeInChart,
                delete = it.markedForDeletion,
            )
        }

        val result = apiCall {
            apiService.updatePortfolio(PortfolioUpdateRequestDto(holdingsDto, manualItemsDto))
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> result
        }
    }

    suspend fun getAssetPurchases(assetId: Long): ApiResult<List<AssetPurchase>> {
        val result = apiCall { apiService.getAssetPurchases(assetId) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.purchases.mapNotNull { it.toDomain() })
            is ApiResult.Error -> result
        }
    }

    suspend fun addAssetPurchase(
        assetId: Long,
        quantity: BigDecimal,
        purchasePrice: BigDecimal,
        purchaseDate: LocalDate,
    ): ApiResult<AssetPurchase> = purchaseCall(
        AppError.ValidationFailed("Purchase could not be read back from the server.", emptyList()),
    ) {
        apiService.createAssetPurchase(assetId, purchaseRequestDto(quantity, purchasePrice, purchaseDate))
    }

    suspend fun updateAssetPurchase(
        purchaseId: Long,
        quantity: BigDecimal,
        purchasePrice: BigDecimal,
        purchaseDate: LocalDate,
    ): ApiResult<AssetPurchase> = purchaseCall(
        AppError.ValidationFailed("Purchase could not be read back from the server.", emptyList()),
    ) {
        apiService.updateAssetPurchase(purchaseId, purchaseRequestDto(quantity, purchasePrice, purchaseDate))
    }

    suspend fun deleteAssetPurchase(purchaseId: Long): ApiResult<Unit> {
        return apiCall { apiService.deleteAssetPurchase(purchaseId) }
    }

    suspend fun uploadPurchaseReceipt(purchaseId: Long, uri: Uri, contentResolver: ContentResolver): ApiResult<Unit> {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        } ?: return ApiResult.Error(AppError.Unknown(null, "Could not read the selected photo."))

        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("receipt", "receipt.$extension", body)

        return apiCall { apiService.uploadPurchaseReceipt(purchaseId, part) }
    }

    suspend fun deletePurchaseReceipt(purchaseId: Long): ApiResult<Unit> {
        return apiCall { apiService.deletePurchaseReceipt(purchaseId) }
    }

    private fun purchaseRequestDto(quantity: BigDecimal, purchasePrice: BigDecimal, purchaseDate: LocalDate) =
        AssetPurchaseRequestDto(
            quantity = quantity,
            purchasePrice = purchasePrice,
            purchaseDate = purchaseDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )

    private suspend fun purchaseCall(
        parseError: AppError,
        call: suspend () -> Response<AssetPurchaseResponseDto>,
    ): ApiResult<AssetPurchase> {
        return when (val result = apiCall(call)) {
            is ApiResult.Success -> result.data.purchase.toDomain()?.let { ApiResult.Success(it) } ?: ApiResult.Error(parseError)
            is ApiResult.Error -> result
        }
    }
}
