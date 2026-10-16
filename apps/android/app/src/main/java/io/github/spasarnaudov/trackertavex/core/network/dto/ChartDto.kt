package io.github.spasarnaudov.trackertavex.core.network.dto

import kotlinx.serialization.Serializable

/**
 * ASSUMED shape (API.md only says chart layout configuration is read/written via
 * `GET`/`PUT /charts/configuration`, without a field list). Modeled as a named list of
 * chart definitions, each pinned to an asset or the portfolio, with a range/interval.
 */
@Serializable
data class ChartConfigurationDto(
    val charts: List<ChartDefinitionDto> = emptyList(),
)

@Serializable
data class ChartDefinitionDto(
    val id: String? = null,
    val title: String? = null,
    val assetId: Long? = null,
    val range: String,
    val interval: String,
    val startDate: String? = null,
    val endDate: String? = null,
)
