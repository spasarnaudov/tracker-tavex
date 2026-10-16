package io.github.spasarnaudov.trackertavex.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Shared JSON configuration: tolerant of fields the mobile app doesn't know about yet
 * and of optional/missing fields, so a server-side addition never crashes parsing.
 * DTO properties are declared camelCase in Kotlin and mapped to the API's snake_case
 * keys (e.g. `token_type`, `include_in_chart`) automatically via [JsonNamingStrategy].
 */
@OptIn(ExperimentalSerializationApi::class)
val networkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    // API.md's PUT /portfolio example sends `"id": null` explicitly for a new manual
    // item, so request bodies must always write nulls rather than omit the field.
    explicitNulls = true
    encodeDefaults = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}
