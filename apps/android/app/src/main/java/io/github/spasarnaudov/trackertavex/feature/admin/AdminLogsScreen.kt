package io.github.spasarnaudov.trackertavex.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.model.LogFile
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.formatDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLogsScreen(
    onLogClick: (LogFile) -> Unit,
    viewModel: AdminLogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val genericErrorMessage = stringResource(R.string.common_error_generic)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_admin_logs_title)) }) }) { padding ->
        when (state.status) {
            LoadStatus.LOADING -> FullScreenLoading(modifier = Modifier.padding(padding))
            LoadStatus.ERROR -> FullScreenError(
                message = state.errorMessage ?: genericErrorMessage,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            LoadStatus.EMPTY -> EmptyState(message = stringResource(R.string.screen_admin_logs_empty_message), modifier = Modifier.padding(padding))

            LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.files, key = { it.name }) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = {
                            val size = file.sizeBytes?.let { stringResource(R.string.screen_admin_logs_size_kb, it / 1024) }
                            val modified = file.modifiedAt?.formatDisplay()
                            Text(listOfNotNull(size, modified).joinToString(" · "))
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onLogClick(file) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
