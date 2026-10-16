package io.github.spasarnaudov.trackertavex.core.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.spasarnaudov.trackertavex.core.network.ApiService
import io.github.spasarnaudov.trackertavex.core.network.AuthInterceptor
import io.github.spasarnaudov.trackertavex.core.network.DynamicApiServiceHolder
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Intentionally unscoped: every injection site should ask for `Provider<ApiService>`
     * and call `.get()` per request, so a base-URL change (which rebuilds the client
     * inside [DynamicApiServiceHolder]) is picked up immediately instead of being
     * frozen at the first injection.
     */
    @Provides
    fun provideApiService(holder: DynamicApiServiceHolder): ApiService = holder.current

    /**
     * A standalone Coil [ImageLoader] carrying the same [AuthInterceptor] as the REST
     * client, so receipt-photo requests (which Coil issues directly, outside Retrofit)
     * are authenticated the same way. Deliberately separate from [DynamicApiServiceHolder]
     * — Coil never needs to rebuild on a base-URL change since callers always pass a
     * full absolute URL.
     */
    @Provides
    @Singleton
    fun provideReceiptImageLoader(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
    ): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
