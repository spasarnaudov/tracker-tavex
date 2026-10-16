package io.github.spasarnaudov.trackertavex.core.network.dto

import io.github.spasarnaudov.trackertavex.core.network.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.math.BigDecimal

/**
 * `GET /assets` response. API.md only guarantees the `gold_buyback_assets` key exists
 * (it's referenced explicitly for `price_asset_id` linkage); the `assets` key and the
 * per-asset fields beyond id/symbol/name are ASSUMED.
 */
@Serializable
data class AssetsResponseDto(
    val assets: List<AssetDto> = emptyList(),
    val goldBuybackAssets: List<AssetDto> = emptyList(),
)

@Serializable
data class AssetDto(
    val id: Long,
    val symbol: String = "",
    val name: String,
    @SerialName("price")
    @Serializable(with = BigDecimalSerializer::class) val currentPrice: BigDecimal? = null,
    val currency: String? = null,
)

/** ASSUMED shape for `GET /assets/{asset_id}/prices` — a bare array of points. */
@Serializable
data class PricePointDto(
    @SerialName("price_date")
    val timestamp: String,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal,
)

@Serializable
data class AssetPricesResponseDto(
    val assetId: Long,
    val range: String,
    val interval: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val prices: List<PricePointDto> = emptyList(),
)
