package io.github.spasarnaudov.trackertavex.core.model

import java.time.LocalDate
import java.util.UUID

data class ChartDefinition(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** Null means "portfolio value" rather than a specific asset. */
    val assetId: Long?,
    val range: ChartRange,
    val interval: AssetPriceInterval,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

data class ChartConfiguration(
    val charts: List<ChartDefinition>,
)
