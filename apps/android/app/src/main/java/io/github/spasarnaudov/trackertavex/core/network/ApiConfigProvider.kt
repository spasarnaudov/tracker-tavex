package io.github.spasarnaudov.trackertavex.core.network

import io.github.spasarnaudov.trackertavex.BuildConfig
import io.github.spasarnaudov.trackertavex.core.di.ApplicationScope
import io.github.spasarnaudov.trackertavex.core.storage.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the API base URL to use: a saved [SettingsDataStore] override takes
 * priority, falling back to `BuildConfig.API_BASE_URL`. Exposed synchronously because
 * [AuthInterceptor] and [DynamicApiServiceHolder] both need it off the main thread
 * without suspending.
 */
@Singleton
class ApiConfigProvider @Inject constructor(
    settingsDataStore: SettingsDataStore,
    @ApplicationScope appScope: CoroutineScope,
) {
    @Volatile
    var currentBaseUrl: String = BuildConfig.API_BASE_URL
        private set

    init {
        settingsDataStore.baseUrlOverride
            .onEach { override -> currentBaseUrl = normalize(override) ?: BuildConfig.API_BASE_URL }
            .launchIn(appScope)
    }

    companion object {
        fun normalize(url: String?): String? {
            val trimmed = url?.trim()
            if (trimmed.isNullOrEmpty()) return null
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
