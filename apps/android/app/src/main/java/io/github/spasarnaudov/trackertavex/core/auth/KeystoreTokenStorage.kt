package io.github.spasarnaudov.trackertavex.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the Bearer token with an AES-256/GCM key generated inside the Android
 * Keystore — the key material never leaves secure hardware, so the ciphertext stored
 * in SharedPreferences is useless without this device.
 */
@Singleton
class KeystoreTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStorage {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val _tokenFlow = MutableStateFlow<String?>(null)
    override val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    init {
        _tokenFlow.value = readToken()
    }

    override fun getToken(): String? = _tokenFlow.value

    override fun saveToken(token: String, expiresAt: String?) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_TOKEN_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_EXPIRES_AT, expiresAt)
            .apply()
        _tokenFlow.value = token
    }

    override fun clear() {
        prefs.edit().clear().apply()
        _tokenFlow.value = null
    }

    private fun readToken(): String? = runCatching {
        val ivBase64 = prefs.getString(KEY_TOKEN_IV, null) ?: return null
        val ciphertextBase64 = prefs.getString(KEY_TOKEN_CIPHERTEXT, null) ?: return null
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_FILE_NAME = "secure_auth_prefs"
        const val KEY_TOKEN_IV = "bearer_token_iv"
        const val KEY_TOKEN_CIPHERTEXT = "bearer_token_ciphertext"
        const val KEY_EXPIRES_AT = "token_expires_at"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tracker_tavex_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
