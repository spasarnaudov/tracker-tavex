package io.github.spasarnaudov.trackertavex.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Everything in this file is an ASSUMED endpoint/shape. API.md only says admin
 * dashboards exist ("user list", "login statistics", "login history", "log files")
 * without documenting paths or payloads; these follow the same versioned-JSON,
 * REST-conventions style as the documented endpoints. Verify against the real server
 * and the (missing) openapi.yaml before relying on this in production.
 */
@Serializable
data class AdminUserDto(
    val id: Long,
    val username: String,
    val role: String,
    val active: Boolean? = null,
    val createdAt: String? = null,
)

@Serializable
data class LogFileDto(
    val name: String,
    val sizeBytes: Long? = null,
    val modifiedAt: String? = null,
)
