package io.github.spasarnaudov.trackertavex.core.auth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.model.User
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes [SessionExpiryNotifier] to Compose so the nav host can react to a forced logout, and
 * [SessionManager.currentUser] so the nav host can pick the home destination / bottom-nav tabs
 * for admins vs. regular users.
 */
@HiltViewModel
class SessionExpiryViewModel @Inject constructor(
    notifier: SessionExpiryNotifier,
    sessionManager: SessionManager,
) : ViewModel() {
    val currentUser: StateFlow<User?> = sessionManager.currentUser
    val events: SharedFlow<Unit> = notifier.events
}
