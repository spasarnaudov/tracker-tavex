package io.github.spasarnaudov.trackertavex.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.spasarnaudov.trackertavex.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds (and rebuilds) the Retrofit/OkHttp stack whenever the configured base URL
 * changes, so switching servers in Connection Settings takes effect without an app
 * restart. Repositories obtain [ApiService] through a `Provider<ApiService>` so each
 * call picks up the latest instance instead of one captured at injection time.
 */
@Singleton
class DynamicApiServiceHolder @Inject constructor(
    private val apiConfigProvider: ApiConfigProvider,
    private val authInterceptor: AuthInterceptor,
    private val unauthorizedInterceptor: UnauthorizedInterceptor,
) {
    private val lock = Any()

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApiService: ApiService? = null

    val current: ApiService
        get() {
            val url = apiConfigProvider.currentBaseUrl
            cachedApiService?.let { if (cachedBaseUrl == url) return it }
            synchronized(lock) {
                val urlNow = apiConfigProvider.currentBaseUrl
                cachedApiService?.let { if (cachedBaseUrl == urlNow) return it }
                val built = buildApiService(urlNow, authInterceptor, unauthorizedInterceptor)
                cachedApiService = built
                cachedBaseUrl = urlNow
                return built
            }
        }

    companion object {
        /**
         * A logging interceptor is only attached in debug builds, at BASIC level (method,
         * URL, response code) — never BODY/HEADERS, which would leak passwords and Bearer
         * tokens into Logcat.
         */
        private fun buildLoggingInterceptor(): HttpLoggingInterceptor =
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                redactHeader("Authorization")
            }

        private fun buildApiService(
            baseUrl: String,
            authInterceptor: AuthInterceptor,
            unauthorizedInterceptor: UnauthorizedInterceptor,
        ): ApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(unauthorizedInterceptor)
                .addInterceptor(buildLoggingInterceptor())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
                .build()
            return retrofit.create(ApiService::class.java)
        }

        /** Builds a throwaway [ApiService] for the "test connection" action in Connection Settings. */
        fun buildAdHoc(baseUrl: String, authInterceptor: AuthInterceptor, unauthorizedInterceptor: UnauthorizedInterceptor): ApiService =
            buildApiService(baseUrl, authInterceptor, unauthorizedInterceptor)
    }
}
