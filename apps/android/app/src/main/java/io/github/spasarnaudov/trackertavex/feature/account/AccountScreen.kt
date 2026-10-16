package io.github.spasarnaudov.trackertavex.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onLoggedOut: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onConnectionSettings: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val genericErrorMessage = stringResource(R.string.common_error_generic)

    LaunchedEffect(Unit) {
        viewModel.loggedOut.collect { onLoggedOut() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_account_title)) }) }) { padding ->
        when (state.status) {
            LoadStatus.LOADING -> FullScreenLoading(modifier = Modifier.padding(padding))
            LoadStatus.ERROR -> FullScreenError(
                message = state.errorMessage ?: genericErrorMessage,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(AppSpacing.Medium)) {
                state.user?.let { user ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(AppSpacing.Medium)) {
                            Text(user.username, style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.common_role_label, user.role),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(top = AppSpacing.Medium))
                OutlinedButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_change_password_label))
                }
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                OutlinedButton(onClick = onConnectionSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_connection_settings_label))
                }

                Spacer(modifier = Modifier.padding(top = AppSpacing.Large))
                Button(
                    onClick = viewModel::logout,
                    enabled = !state.isLoggingOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.isLoggingOut) {
                            stringResource(R.string.screen_account_signing_out_status)
                        } else {
                            stringResource(R.string.screen_account_button_logout)
                        },
                    )
                }
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                Button(
                    onClick = onDeleteAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.common_delete_account_label))
                }
            }
        }
    }
}
