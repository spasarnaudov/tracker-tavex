package io.github.spasarnaudov.trackertavex.core.model

/** Domain-level representation of every error the API (or the network) can produce. */
sealed class AppError {
    abstract val message: String?

    data class BadRequest(override val message: String?, val details: List<String> = emptyList()) : AppError()
    data class Unauthorized(override val message: String?) : AppError()
    data class Forbidden(override val message: String?) : AppError()
    data class NotFound(override val message: String?) : AppError()
    data class Conflict(override val message: String?) : AppError()
    data class ActiveSessionConflict(override val message: String?) : AppError()
    data class ValidationFailed(override val message: String?, val details: List<String> = emptyList()) : AppError()
    data class ServerError(override val message: String?) : AppError()
    data class Network(override val message: String?) : AppError()
    data class Unknown(val code: String?, override val message: String?) : AppError()
}
