package io.github.spasarnaudov.trackertavex.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the Bearer token. Never store the token in plain SharedPreferences, logs,
 * analytics, or crash reports — see [KeystoreTokenStorage] for the real implementation.
 */
interface TokenStorage {
    val tokenFlow: StateFlow<String?>

    /** Synchronous on purpose: called from [io.github.spasarnaudov.trackertavex.core.network.AuthInterceptor] on the OkHttp dispatcher thread. */
    fun getToken(): String?
    fun saveToken(token: String, expiresAt: String?)
    fun clear()
}
