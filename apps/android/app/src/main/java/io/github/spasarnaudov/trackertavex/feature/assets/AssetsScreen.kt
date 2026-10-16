package io.github.spasarnaudov.trackertavex.feature.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import io.github.spasarnaudov.trackertavex.core.model.Asset
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.formatMoneyOrDash
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    onAssetClick: (Asset) -> Unit,
    viewModel: AssetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val genericErrorMessage = stringResource(R.string.common_error_generic)
    val assetsHeader = stringResource(R.string.screen_assets_title)

    LaunchedEffect(state.status) {
        if (state.status != LoadStatus.LOADING) isRefreshing = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text(assetsHeader) }) }) { padding ->
        when (state.status) {
            LoadStatus.LOADING -> FullScreenLoading(modifier = Modifier.padding(padding))
            LoadStatus.ERROR -> FullScreenError(
                message = state.errorMessage ?: genericErrorMessage,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            LoadStatus.EMPTY -> EmptyState(
                message = stringResource(R.string.screen_assets_empty_message),
                modifier = Modifier.padding(padding),
            )

            LoadStatus.CONTENT -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true; viewModel.load() },
                modifier = Modifier.padding(padding),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.assets.isNotEmpty()) {
                        item {
                            Text(
                                assetsHeader,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(AppSpacing.Medium),
                            )
                        }
                        items(state.assets, key = { "asset_${it.id}" }) { asset ->
                            AssetRow(asset, onClick = { onAssetClick(asset) })
                            HorizontalDivider()
                        }
                    }
                    if (state.goldBuybackAssets.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.screen_assets_gold_buyback_header),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(AppSpacing.Medium),
                            )
                        }
                        items(state.goldBuybackAssets, key = { "gold_${it.id}" }) { asset ->
                            AssetRow(asset, onClick = { onAssetClick(asset) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: Asset, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("${asset.symbol} · ${asset.name}") },
        supportingContent = {
            asset.currentPrice?.let { Text(stringResource(R.string.screen_assets_price, it.formatMoneyOrDash())) }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
