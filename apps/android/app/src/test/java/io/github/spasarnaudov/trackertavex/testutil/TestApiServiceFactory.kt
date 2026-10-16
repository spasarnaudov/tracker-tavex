package io.github.spasarnaudov.trackertavex.testutil

import io.github.spasarnaudov.trackertavex.core.auth.SessionExpiryNotifier
import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.core.auth.TokenStorage
import io.github.spasarnaudov.trackertavex.core.network.ApiService
import io.github.spasarnaudov.trackertavex.core.network.AuthInterceptor
import io.github.spasarnaudov.trackertavex.core.network.UnauthorizedInterceptor
import io.github.spasarnaudov.trackertavex.core.network.networkJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/** Builds a real [ApiService] wired to interceptors, pointed at a MockWebServer instance. */
object TestApiServiceFactory {
    fun create(
        baseUrl: String,
        tokenStorage: TokenStorage,
        sessionManager: SessionManager = SessionManager(),
        sessionExpiryNotifier: SessionExpiryNotifier = SessionExpiryNotifier(),
    ): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStorage))
            .addInterceptor(UnauthorizedInterceptor(tokenStorage, sessionManager, sessionExpiryNotifier))
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(ApiService::class.java)
    }
}
