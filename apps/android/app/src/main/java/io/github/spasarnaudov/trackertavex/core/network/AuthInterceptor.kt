package io.github.spasarnaudov.trackertavex.core.network

import io.github.spasarnaudov.trackertavex.core.auth.SessionExpiryNotifier
import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.core.auth.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches `Authorization: Bearer <token>` to every request when a token is stored. */
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStorage.getToken()
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}

/**
 * Detects a `401` on a request that carried a token (i.e. a protected endpoint) and
 * clears the stored token + notifies the app to return to Login. A `401` on a public
 * endpoint (e.g. bad login credentials) is left untouched — there's no token to clear
 * and the caller handles that error itself.
 */
class UnauthorizedInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
    private val sessionExpiryNotifier: SessionExpiryNotifier,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val wasAuthenticated = request.header("Authorization") != null
        if (response.code == 401 && wasAuthenticated) {
            tokenStorage.clear()
            sessionManager.setUser(null)
            sessionExpiryNotifier.notifyExpired()
        }
        return response
    }
}
