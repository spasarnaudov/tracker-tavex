package io.github.spasarnaudov.trackertavex.feature.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError

@Composable
fun SplashScreen(
    onSessionRestored: () -> Unit,
    onNeedsLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            SplashUiState.SessionRestored -> onSessionRestored()
            SplashUiState.NeedsLogin -> onNeedsLogin()
            else -> Unit
        }
    }

    when (val current = state) {
        is SplashUiState.NetworkErrorRetry -> FullScreenError(
            message = current.message,
            onRetry = { viewModel.checkSession() },
        )

        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
