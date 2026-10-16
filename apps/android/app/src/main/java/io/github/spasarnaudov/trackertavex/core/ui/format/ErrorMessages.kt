package io.github.spasarnaudov.trackertavex.core.ui.format

import io.github.spasarnaudov.trackertavex.core.model.AppError

/** Maps every [AppError] to a message safe and clear to show directly in the UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.BadRequest -> message ?: "This request was invalid."
    is AppError.Unauthorized -> message ?: "Your session has expired. Please sign in again."
    is AppError.Forbidden -> message ?: "You don't have permission to do that."
    is AppError.NotFound -> message ?: "That item could not be found."
    is AppError.Conflict -> message ?: "This action conflicts with the current state."
    is AppError.ActiveSessionConflict -> message
        ?: "This account already has an active session. Do you want to end it?"

    is AppError.ValidationFailed -> details.firstOrNull() ?: message ?: "Please check the highlighted fields."
    is AppError.ServerError -> message ?: "Something went wrong on the server. Please try again later."
    is AppError.Network -> message ?: "Couldn't reach the server. Check your connection and try again."
    is AppError.Unknown -> message ?: "An unexpected error occurred."
}

fun AppError.details(): List<String> = when (this) {
    is AppError.BadRequest -> details
    is AppError.ValidationFailed -> details
    else -> emptyList()
}
