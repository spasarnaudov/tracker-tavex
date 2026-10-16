package io.github.spasarnaudov.trackertavex.core.network

import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.network.dto.ErrorEnvelopeDto
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val error: AppError) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onError(action: (AppError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(error)
    return this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

/**
 * Executes a Retrofit call, mapping HTTP/network failures onto [AppError] instead of
 * letting exceptions escape into repositories or ViewModels.
 */
suspend fun <T> apiCall(block: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            val body = response.body() ?: (Unit as T)
            ApiResult.Success(body)
        } else {
            ApiResult.Error(parseHttpError(response.code(), response.errorBody()?.string()))
        }
    } catch (e: UnknownHostException) {
        ApiResult.Error(AppError.Network("Could not resolve the server address. Check the connection settings."))
    } catch (e: SocketTimeoutException) {
        ApiResult.Error(AppError.Network("The server did not respond in time."))
    } catch (e: IOException) {
        ApiResult.Error(AppError.Network(e.message ?: "The server could not be reached."))
    } catch (e: Exception) {
        ApiResult.Error(AppError.Unknown(null, e.message))
    }
}

fun parseHttpError(httpCode: Int, rawBody: String?): AppError {
    val envelope = rawBody?.takeIf { it.isNotBlank() }?.let {
        runCatching { networkJson.decodeFromString(ErrorEnvelopeDto.serializer(), it) }.getOrNull()
    }
    val code = envelope?.error?.code
    val message = envelope?.error?.message
    val details = envelope?.error?.details ?: emptyList()
    return when (httpCode) {
        400 -> AppError.BadRequest(message, details)
        401 -> AppError.Unauthorized(message)
        403 -> AppError.Forbidden(message)
        404 -> AppError.NotFound(message)
        409 -> if (code == "active_session") {
            AppError.ActiveSessionConflict(message)
        } else {
            AppError.Conflict(message)
        }

        422 -> AppError.ValidationFailed(message, details)
        500 -> AppError.ServerError(message)
        else -> AppError.Unknown(code, message)
    }
}
