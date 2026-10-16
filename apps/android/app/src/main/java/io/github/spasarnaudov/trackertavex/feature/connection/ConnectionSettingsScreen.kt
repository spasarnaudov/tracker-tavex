package io.github.spasarnaudov.trackertavex.feature.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.ui.components.ConfirmDialog
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    onNavigateBack: () -> Unit,
    onSessionCleared: () -> Unit,
    viewModel: ConnectionSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.screen_connection_saved_message)

    LaunchedEffect(Unit) {
        viewModel.sessionCleared.collect { onSessionCleared() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) snackbarHostState.showSnackbar(savedMessage)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_connection_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_navigation_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(AppSpacing.Large)) {
            Text(
                stringResource(R.string.screen_connection_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.padding(top = AppSpacing.Medium))
            OutlinedTextField(
                value = state.baseUrlText,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.screen_connection_base_url_label)) },
                singleLine = true,
                isError = state.validationError != null,
                supportingText = { state.validationError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isUsingOverride) {
                Spacer(modifier = Modifier.padding(top = AppSpacing.ExtraSmall))
                Text(stringResource(R.string.screen_connection_custom_address_notice), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.padding(top = AppSpacing.Medium))
            Row {
                OutlinedButton(onClick = viewModel::testConnection, enabled = !state.isTesting) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.padding(AppSpacing.Tiny))
                    } else {
                        Text(stringResource(R.string.screen_connection_button_test))
                    }
                }
                Spacer(modifier = Modifier.padding(start = AppSpacing.Small))
                Button(onClick = viewModel::save) { Text(stringResource(R.string.common_action_save)) }
            }

            state.testResultMessage?.let {
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                Text(
                    text = it,
                    color = if (state.testSucceeded == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(modifier = Modifier.padding(top = AppSpacing.Large))
            OutlinedButton(onClick = viewModel::resetToDefault, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.screen_connection_button_reset))
            }
        }
    }

    if (state.showClearSessionConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.screen_connection_switch_server_title),
            text = stringResource(R.string.screen_connection_switch_server_message),
            confirmLabel = stringResource(R.string.common_action_continue),
            destructive = true,
            onConfirm = viewModel::confirmSaveAndClearSession,
            onDismiss = viewModel::dismissClearSessionConfirm,
        )
    }
}
