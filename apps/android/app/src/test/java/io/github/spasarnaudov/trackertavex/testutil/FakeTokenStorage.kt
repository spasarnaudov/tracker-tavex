package io.github.spasarnaudov.trackertavex.testutil

import io.github.spasarnaudov.trackertavex.core.auth.TokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [TokenStorage] for JVM unit tests — the real implementation needs AndroidKeyStore. */
class FakeTokenStorage(initialToken: String? = null) : TokenStorage {
    private val _tokenFlow = MutableStateFlow(initialToken)
    override val tokenFlow: StateFlow<String?> = _tokenFlow

    var lastSavedExpiresAt: String? = null
        private set
    var clearCallCount: Int = 0
        private set

    override fun getToken(): String? = _tokenFlow.value

    override fun saveToken(token: String, expiresAt: String?) {
        _tokenFlow.value = token
        lastSavedExpiresAt = expiresAt
    }

    override fun clear() {
        _tokenFlow.value = null
        clearCallCount++
    }
}
