package io.github.spasarnaudov.trackertavex.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    val force: Boolean? = null,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val tokenType: String? = null,
    val expiresAt: String? = null,
    val user: UserDto,
)

/**
 * ASSUMED endpoint (not specified in API.md): POST /auth/register. Modeled after the
 * documented login request/response shape since no registration contract is given.
 * Verify field names against the real server before relying on this in production.
 */
@Serializable
data class RegisterRequestDto(
    val username: String,
    val password: String,
)
