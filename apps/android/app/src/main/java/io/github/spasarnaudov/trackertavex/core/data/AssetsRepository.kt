package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.model.AssetPriceInterval
import io.github.spasarnaudov.trackertavex.core.model.AssetsCatalog
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.PricePoint
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.network.ApiService
import io.github.spasarnaudov.trackertavex.core.network.apiCall
import io.github.spasarnaudov.trackertavex.core.network.toDomain
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AssetsRepository @Inject constructor(
    private val apiServiceProvider: Provider<ApiService>,
) {
    private val apiService: ApiService get() = apiServiceProvider.get()

    suspend fun getAssetPrices(
        assetId: Long,
        range: ChartRange,
        interval: AssetPriceInterval,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): ApiResult<List<PricePoint>> {
        val validationError = DateRangeValidator.validateCustomRange(range, startDate, endDate)
        if (validationError != null) {
            return ApiResult.Error(AppError.BadRequest(validationError))
        }
        val result = apiCall {
            apiService.getAssetPrices(
                assetId = assetId,
                range = range.wireValue,
                interval = interval.wireValue,
                startDate = if (range == ChartRange.CUSTOM) startDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) else null,
                endDate = if (range == ChartRange.CUSTOM) endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) else null,
            )
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.prices.mapNotNull { it.toDomain() })
            is ApiResult.Error -> result
        }
    }

    suspend fun getAssets(): ApiResult<AssetsCatalog> {
        val result = apiCall { apiService.getAssets() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(
                AssetsCatalog(
                    assets = result.data.assets.map { it.toDomain(isGoldBuyback = false) },
                    goldBuybackAssets = result.data.goldBuybackAssets.map { it.toDomain(isGoldBuyback = true) },
                ),
            )

            is ApiResult.Error -> result
        }
    }
}
