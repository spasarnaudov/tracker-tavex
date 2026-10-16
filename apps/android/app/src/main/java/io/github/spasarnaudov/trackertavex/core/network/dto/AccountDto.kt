package io.github.spasarnaudov.trackertavex.core.network.dto

import kotlinx.serialization.Serializable

/** ASSUMED endpoint (`PUT /account/password`); not specified in API.md. */
@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String,
)
