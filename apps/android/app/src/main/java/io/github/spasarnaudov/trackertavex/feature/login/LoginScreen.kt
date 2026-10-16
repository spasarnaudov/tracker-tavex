package io.github.spasarnaudov.trackertavex.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.BuildConfig
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.ui.components.ConfirmDialog
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToConnectionSettings: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collect { onLoginSuccess() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.Large),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(text = stringResource(R.string.screen_login_subtitle), style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.padding(top = AppSpacing.Large))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text(stringResource(R.string.common_field_username_label)) },
                    singleLine = true,
                    isError = state.usernameError != null,
                    supportingText = { state.usernameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.common_field_password_label)) },
                    singleLine = true,
                    isError = state.passwordError != null,
                    supportingText = { state.passwordError?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.errorMessage?.let {
                    Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.padding(top = AppSpacing.Medium))

                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(AppSpacing.Tiny),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.screen_login_button_sign_in))
                    }
                }

                TextButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.screen_login_button_create_account))
                }

                TextButton(onClick = onNavigateToConnectionSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_connection_settings_label))
                }
            }

            Text(
                text = stringResource(R.string.screen_login_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            )
        }
    }

    if (state.showActiveSessionDialog) {
        ConfirmDialog(
            title = stringResource(R.string.screen_login_active_session_title),
            text = stringResource(R.string.screen_login_active_session_message),
            confirmLabel = stringResource(R.string.common_action_continue),
            onConfirm = viewModel::confirmForceLogin,
            onDismiss = viewModel::dismissActiveSessionDialog,
        )
    }
}
