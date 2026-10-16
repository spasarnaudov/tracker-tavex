package io.github.spasarnaudov.trackertavex.feature.admin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: LogDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val genericErrorMessage = stringResource(R.string.common_error_generic)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_navigation_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state.status) {
            LoadStatus.LOADING -> FullScreenLoading(modifier = Modifier.padding(padding))
            LoadStatus.ERROR -> FullScreenError(
                message = state.errorMessage ?: genericErrorMessage,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            LoadStatus.EMPTY -> EmptyState(message = stringResource(R.string.screen_log_detail_empty_message), modifier = Modifier.padding(padding))

            LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.lines) { line ->
                    Text(text = line, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = AppSpacing.Small))
                }
            }
        }
    }
}
