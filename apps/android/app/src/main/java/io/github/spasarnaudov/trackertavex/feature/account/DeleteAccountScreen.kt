package io.github.spasarnaudov.trackertavex.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.ui.components.ConfirmDialog
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    onAccountDeleted: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.accountDeleted.collect { onAccountDeleted() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_delete_account_label)) },
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
                stringResource(R.string.screen_delete_account_warning),
                style = MaterialTheme.typography.bodyLarge,
            )
            state.errorMessage?.let {
                Spacer(modifier = Modifier.padding(top = AppSpacing.Medium))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.padding(top = AppSpacing.Large))
            Button(
                onClick = { showConfirm = true },
                enabled = !state.isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) {
                        stringResource(R.string.screen_delete_account_deleting_status)
                    } else {
                        stringResource(R.string.screen_delete_account_button_delete)
                    },
                )
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.screen_delete_account_confirm_title),
            text = stringResource(R.string.screen_delete_account_confirm_message),
            confirmLabel = stringResource(R.string.common_action_delete),
            destructive = true,
            onConfirm = { showConfirm = false; viewModel.confirmDeletion() },
            onDismiss = { showConfirm = false },
        )
    }
}
