package io.github.spasarnaudov.trackertavex.testutil

import io.github.spasarnaudov.trackertavex.core.auth.TokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [TokenStorage] for Compose UI tests — avoids touching the real Android Keystore. */
class FakeTokenStorage(initialToken: String? = null) : TokenStorage {
    private val _tokenFlow = MutableStateFlow(initialToken)
    override val tokenFlow: StateFlow<String?> = _tokenFlow

    override fun getToken(): String? = _tokenFlow.value

    override fun saveToken(token: String, expiresAt: String?) {
        _tokenFlow.value = token
    }

    override fun clear() {
        _tokenFlow.value = null
    }
}
