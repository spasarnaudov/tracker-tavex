package io.github.spasarnaudov.trackertavex.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Holding(
    val assetId: Long,
    val assetSymbol: String,
    val assetName: String,
    val quantity: BigDecimal,
    val includeInChart: Boolean,
    val price: BigDecimal?,
    val value: BigDecimal?,
    val costBasis: BigDecimal? = null,
    val profit: BigDecimal? = null,
    val profitPercent: BigDecimal? = null,
)

/** A single purchase lot behind a [Holding] — how much was bought, at what price, and when. */
data class AssetPurchase(
    val id: Long,
    val assetId: Long,
    val quantity: BigDecimal,
    val purchasePrice: BigDecimal,
    val purchaseDate: LocalDate,
    val profit: BigDecimal? = null,
    val profitPercent: BigDecimal? = null,
    val hasReceipt: Boolean = false,
)

data class ManualItem(
    val id: Long?,
    val name: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal?,
    val priceAssetId: Long?,
    val includeInChart: Boolean,
    val value: BigDecimal?,
    val markedForDeletion: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val usesManualPrice: Boolean get() = priceAssetId == null
}

data class Portfolio(
    val holdings: List<Holding>,
    val manualItems: List<ManualItem>,
    val totalValue: BigDecimal?,
)

data class PortfolioHistoryPoint(
    val timestamp: LocalDateTime,
    val value: BigDecimal,
)

enum class ChartRange(val wireValue: String, val label: String) {
    ONE_DAY("1d", "1D"),
    ONE_WEEK("1w", "1W"),
    ONE_MONTH("1m", "1M"),
    YEAR_TO_DATE("ytd", "YTD"),
    ONE_YEAR("1y", "1Y"),
    ALL("all", "All"),
    CUSTOM("custom", "Custom");
}

/** Intervals supported by `GET /assets/{id}/prices`. */
enum class AssetPriceInterval(val wireValue: String, val label: String) {
    RECORDED("recorded", "Recorded"),
    HOURLY("hourly", "Hourly"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
}

/** Intervals supported by `GET /portfolio/history` — a strict subset of the asset ones. */
enum class PortfolioHistoryInterval(val wireValue: String, val label: String) {
    HOURLY("hourly", "Hourly"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
}
