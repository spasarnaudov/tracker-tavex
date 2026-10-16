package io.github.spasarnaudov.trackertavex.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Non-critical app settings. Currently just the developer-configurable API base URL,
 * which takes priority over `BuildConfig.API_BASE_URL` when present.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val baseUrlOverride: Flow<String?> =
        context.settingsDataStore.data.map { it[BASE_URL_KEY]?.takeIf { url -> url.isNotBlank() } }

    suspend fun setBaseUrlOverride(url: String?) {
        context.settingsDataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(BASE_URL_KEY)
            } else {
                prefs[BASE_URL_KEY] = url
            }
        }
    }

    private companion object {
        val BASE_URL_KEY = stringPreferencesKey("api_base_url_override")
    }
}
