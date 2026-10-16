package io.github.spasarnaudov.trackertavex.feature.portfolio

import io.github.spasarnaudov.trackertavex.core.model.Holding
import java.math.BigDecimal
import java.util.UUID

/** Read-only row for a holding — quantity/profit are server-computed from purchase lots; only [includeInChart] is editable here. */
data class HoldingRowState(
    val assetId: Long,
    val assetSymbol: String,
    val assetName: String,
    val quantity: BigDecimal,
    val includeInChart: Boolean,
    val price: BigDecimal?,
    val value: BigDecimal?,
    val costBasis: BigDecimal?,
    val profit: BigDecimal?,
    val profitPercent: BigDecimal?,
    val originalIncludeInChart: Boolean,
) {
    val isDirty: Boolean
        get() = includeInChart != originalIncludeInChart

    companion object {
        fun from(holding: Holding) = HoldingRowState(
            assetId = holding.assetId,
            assetSymbol = holding.assetSymbol,
            assetName = holding.assetName,
            quantity = holding.quantity,
            includeInChart = holding.includeInChart,
            price = holding.price,
            value = holding.value,
            costBasis = holding.costBasis,
            profit = holding.profit,
            profitPercent = holding.profitPercent,
            originalIncludeInChart = holding.includeInChart,
        )
    }
}

/**
 * A manual item being edited locally. [clientKey] lets several new (server `id == null`)
 * items coexist in the pending list before save.
 */
data class ManualItemDraft(
    val clientKey: String = UUID.randomUUID().toString(),
    val id: Long?,
    val name: String,
    val quantityText: String,
    val unitPriceText: String,
    val priceAssetId: Long?,
    val includeInChart: Boolean,
    val value: BigDecimal?,
    val markedForDeletion: Boolean = false,
) {
    val usesManualPrice: Boolean get() = priceAssetId == null

    companion object {
        fun from(item: io.github.spasarnaudov.trackertavex.core.model.ManualItem) = ManualItemDraft(
            id = item.id,
            name = item.name,
            quantityText = item.quantity.toPlainString(),
            unitPriceText = item.unitPrice?.toPlainString() ?: "",
            priceAssetId = item.priceAssetId,
            includeInChart = item.includeInChart,
            value = item.value,
        )

        fun blank() = ManualItemDraft(
            id = null,
            name = "",
            quantityText = "",
            unitPriceText = "",
            priceAssetId = null,
            includeInChart = true,
            value = null,
        )
    }
}
