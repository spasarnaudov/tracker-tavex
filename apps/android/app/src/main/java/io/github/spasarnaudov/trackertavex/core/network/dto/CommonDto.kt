package io.github.spasarnaudov.trackertavex.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val role: String,
)

@Serializable
data class SessionResponseDto(
    val authenticated: Boolean,
    val user: UserDto,
)

@Serializable
data class ErrorEnvelopeDto(
    val error: ErrorBodyDto? = null,
)

@Serializable
data class ErrorBodyDto(
    val code: String? = null,
    val message: String? = null,
    val details: List<String> = emptyList(),
)

@Serializable
data class HealthResponseDto(
    val status: String? = null,
)
