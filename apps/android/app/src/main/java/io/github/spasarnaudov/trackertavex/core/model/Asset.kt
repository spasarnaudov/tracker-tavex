package io.github.spasarnaudov.trackertavex.core.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class Asset(
    val id: Long,
    val symbol: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val currency: String?,
    val isGoldBuyback: Boolean,
)

data class PricePoint(
    val timestamp: LocalDateTime,
    val price: BigDecimal,
)

data class AssetsCatalog(
    val assets: List<Asset>,
    val goldBuybackAssets: List<Asset>,
)

