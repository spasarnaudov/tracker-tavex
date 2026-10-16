package io.github.spasarnaudov.trackertavex.core.network.dto

import io.github.spasarnaudov.trackertavex.core.network.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.math.BigDecimal

/** Request body for `PUT /portfolio`, matching API.md exactly. */
@Serializable
data class PortfolioUpdateRequestDto(
    val holdings: List<HoldingUpdateDto>,
    val manualItems: List<ManualItemUpdateDto>,
)

@Serializable
data class HoldingUpdateDto(
    val assetId: Long,
    val includeInChart: Boolean,
)

@Serializable
data class ManualItemUpdateDto(
    val id: Long? = null,
    val name: String,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val unitPrice: BigDecimal? = null,
    val priceAssetId: Long? = null,
    val includeInChart: Boolean,
    val delete: Boolean = false,
)

/**
 * ASSUMED shape (API.md documents the PUT request but not the GET/PUT response body).
 * Modeled as the editable holdings list plus resolved asset/value information needed
 * to render the portfolio screen.
 */
@Serializable
data class PortfolioResponseDto(
    val holdings: List<HoldingResponseDto> = emptyList(),
    val manualItems: List<ManualItemResponseDto> = emptyList(),
    @Serializable(with = BigDecimalSerializer::class) val totalValue: BigDecimal? = null,
)

@Serializable
data class HoldingResponseDto(
    val assetId: Long,
    @SerialName("symbol")
    val assetSymbol: String? = null,
    @SerialName("name")
    val assetName: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal,
    val includeInChart: Boolean = true,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal? = null,
    @SerialName("current_value")
    @Serializable(with = BigDecimalSerializer::class) val value: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val costBasis: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val profit: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val profitPercent: BigDecimal? = null,
)

/** Request body for creating or editing a single purchase lot behind a holding. */
@Serializable
data class AssetPurchaseRequestDto(
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val purchasePrice: BigDecimal,
    val purchaseDate: String,
)

@Serializable
data class AssetPurchaseDto(
    val id: Long,
    val assetId: Long,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val purchasePrice: BigDecimal,
    val purchaseDate: String,
    @Serializable(with = BigDecimalSerializer::class) val profit: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val profitPercent: BigDecimal? = null,
    val hasReceipt: Boolean = false,
)

@Serializable
data class AssetPurchasesResponseDto(
    val purchases: List<AssetPurchaseDto> = emptyList(),
)

@Serializable
data class AssetPurchaseResponseDto(
    val purchase: AssetPurchaseDto,
)

@Serializable
data class ManualItemResponseDto(
    val id: Long,
    val name: String,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val unitPrice: BigDecimal? = null,
    val priceAssetId: Long? = null,
    val includeInChart: Boolean = true,
    @SerialName("current_value")
    @Serializable(with = BigDecimalSerializer::class) val value: BigDecimal? = null,
)

@Serializable
data class PortfolioHistoryPointDto(
    @SerialName("price_date")
    val timestamp: String,
    @Serializable(with = BigDecimalSerializer::class) val value: BigDecimal,
)

@Serializable
data class PortfolioHistoryResponseDto(
    val range: String,
    val interval: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val points: List<PortfolioHistoryPointDto> = emptyList(),
)
