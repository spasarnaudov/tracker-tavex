package io.github.spasarnaudov.trackertavex.feature.portfolio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryInterval
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryPoint
import io.github.spasarnaudov.trackertavex.core.ui.components.ChartPoint
import io.github.spasarnaudov.trackertavex.core.ui.components.ConfirmDialog
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LineChart
import io.github.spasarnaudov.trackertavex.core.ui.components.LineChartDefaults
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.formatMoneyOrDash
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing
import java.math.BigDecimal

private object PortfolioScreenDimens {

    val BottomSpacerHeight = 80.dp
}

/** Shared with [AssetPurchasesScreen] for consistent profit/loss coloring. */
object PortfolioScreenColors {

    val Positive = Color(0xFF157A3D)
    val Negative = Color(0xFFB3261E)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onAddManualItem: () -> Unit,
    onEditManualItem: (String) -> Unit,
    onOpenPurchases: (Long, String, String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showEmptyHoldings by remember { mutableStateOf(true) }
    val genericErrorMessage = stringResource(R.string.common_error_generic)
    val addManualItemLabel = stringResource(R.string.screen_portfolio_add_manual_item)

    LaunchedEffect(state.saveError) {
        state.saveError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.status) {
        if (state.status != LoadStatus.LOADING) isRefreshing = false
    }

    BackHandler(enabled = state.hasUnsavedChanges) { showDiscardConfirm = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.screen_portfolio_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddManualItem) {
                Icon(Icons.Filled.Add, contentDescription = addManualItemLabel)
            }
        },
    ) { padding ->
        // Chart + filters render unconditionally above the holdings list — mirrors the web
        // portfolio page, where the chart panel is always visible regardless of table state.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PortfolioChartSection(
                range = state.historyRange,
                interval = state.historyInterval,
                status = state.historyStatus,
                points = state.historyPoints,
                errorMessage = state.historyErrorMessage,
                showEmptyHoldings = showEmptyHoldings,
                onRangeSelected = viewModel::setHistoryRange,
                onIntervalSelected = viewModel::setHistoryInterval,
                onShowEmptyHoldingsChange = { showEmptyHoldings = it },
                onRetry = viewModel::retryHistory,
                modifier = Modifier.padding(horizontal = AppSpacing.Medium),
            )
            HorizontalDivider(modifier = Modifier.padding(top = AppSpacing.MediumSmall))

            Box(modifier = Modifier.weight(1f)) {
                when (state.status) {
                    LoadStatus.LOADING -> FullScreenLoading()
                    LoadStatus.ERROR -> FullScreenError(
                        message = state.errorMessage ?: genericErrorMessage,
                        onRetry = viewModel::load,
                    )

                    LoadStatus.EMPTY -> EmptyState(
                        message = stringResource(R.string.screen_portfolio_empty_message),
                        actionLabel = addManualItemLabel,
                        onAction = onAddManualItem,
                    )

                    LoadStatus.CONTENT -> PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { isRefreshing = true; viewModel.load() },
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.Medium)) {
                            item {
                                Text(
                                    text = stringResource(R.string.screen_portfolio_total_value, state.totalValue.formatMoneyOrDash()),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(vertical = AppSpacing.MediumSmall),
                                )
                            }
                            val visibleHoldings = if (showEmptyHoldings) {
                                state.holdings
                            } else {
                                state.holdings.filter { it.quantity > BigDecimal.ZERO }
                            }
                            if (visibleHoldings.isNotEmpty()) {
                                item { Text(stringResource(R.string.screen_portfolio_holdings_header), style = MaterialTheme.typography.titleMedium) }
                                items(visibleHoldings, key = { it.assetId }) { holding ->
                                    HoldingRow(
                                        holding = holding,
                                        onToggleChart = { viewModel.toggleHoldingIncludeInChart(holding.assetId) },
                                        onOpenPurchases = { onOpenPurchases(holding.assetId, holding.assetSymbol, holding.assetName) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                            if (state.manualItems.any { !it.markedForDeletion }) {
                                item {
                                    Text(
                                        stringResource(R.string.screen_portfolio_manual_items_header),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = AppSpacing.Medium),
                                    )
                                }
                                items(
                                    state.manualItems.filterNot { it.markedForDeletion },
                                    key = { it.clientKey },
                                ) { item ->
                                    ManualItemRow(
                                        item = item,
                                        onEdit = { onEditManualItem(item.clientKey) },
                                        onDelete = { viewModel.markManualItemForDeletion(item.clientKey) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                            item { Spacer(modifier = Modifier.padding(bottom = PortfolioScreenDimens.BottomSpacerHeight)) }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.screen_portfolio_discard_title),
            text = stringResource(R.string.screen_portfolio_discard_message),
            confirmLabel = stringResource(R.string.screen_portfolio_discard_confirm_label),
            destructive = true,
            onConfirm = { showDiscardConfirm = false; viewModel.discardChanges() },
            onDismiss = { showDiscardConfirm = false },
        )
    }
}

@Composable
private fun PortfolioChartSection(
    range: ChartRange,
    interval: PortfolioHistoryInterval,
    status: LoadStatus,
    points: List<PortfolioHistoryPoint>,
    errorMessage: String?,
    showEmptyHoldings: Boolean,
    onRangeSelected: (ChartRange) -> Unit,
    onIntervalSelected: (PortfolioHistoryInterval) -> Unit,
    onShowEmptyHoldingsChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val genericErrorMessage = stringResource(R.string.common_error_generic)

    Column(modifier = modifier.fillMaxWidth().padding(top = AppSpacing.Small)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterDropdown(
                selectedLabel = range.label,
                options = PortfolioHistoryRanges,
                optionLabel = { it.label },
                onSelected = onRangeSelected,
            )
            FilterDropdown(
                selectedLabel = interval.label,
                options = PortfolioHistoryInterval.entries,
                optionLabel = { it.label },
                onSelected = onIntervalSelected,
            )
            Checkbox(checked = showEmptyHoldings, onCheckedChange = onShowEmptyHoldingsChange)
            Text(stringResource(R.string.screen_portfolio_show_empty_label), style = MaterialTheme.typography.bodyMedium)
        }

        when (status) {
            LoadStatus.LOADING -> Box(
                modifier = Modifier.fillMaxWidth().height(LineChartDefaults.ChartHeight).padding(top = AppSpacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            LoadStatus.ERROR -> Box(
                modifier = Modifier.fillMaxWidth().height(LineChartDefaults.ChartHeight).padding(top = AppSpacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Small),
                ) {
                    Text(errorMessage ?: genericErrorMessage, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.common_action_retry)) }
                }
            }

            LoadStatus.EMPTY -> Box(
                modifier = Modifier.fillMaxWidth().height(LineChartDefaults.ChartHeight).padding(top = AppSpacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.screen_portfolio_history_empty_message), style = MaterialTheme.typography.bodyMedium)
            }

            LoadStatus.CONTENT -> LineChart(
                points = points.map { ChartPoint(it.timestamp, it.value) },
                showTimeInLabels = interval == PortfolioHistoryInterval.HOURLY,
                scrollable = false,
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.Medium),
            )
        }
    }
}

@Composable
private fun <T> FilterDropdown(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedLabel)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun HoldingRow(
    holding: HoldingRowState,
    onToggleChart: () -> Unit,
    onOpenPurchases: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "${holding.assetSymbol} · ${holding.assetName}", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.screen_portfolio_holding_quantity, holding.quantity.stripTrailingZeros().toPlainString()) +
                    " · " + stringResource(R.string.screen_portfolio_holding_value, holding.value.formatMoneyOrDash()),
                style = MaterialTheme.typography.bodySmall,
            )
            if (holding.quantity > BigDecimal.ZERO && holding.profit != null) {
                val profitColor = if (holding.profit.signum() >= 0) PortfolioScreenColors.Positive else PortfolioScreenColors.Negative
                Text(
                    text = holding.profitPercent?.let {
                        stringResource(R.string.screen_portfolio_profit_value, holding.profit.formatMoneyOrDash(), it.stripTrailingZeros().toPlainString())
                    } ?: stringResource(R.string.screen_portfolio_profit_value_no_percent, holding.profit.formatMoneyOrDash()),
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Checkbox(checked = holding.includeInChart, onCheckedChange = { onToggleChart() })
            Text(stringResource(R.string.screen_portfolio_chart_toggle_label), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onOpenPurchases) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = stringResource(R.string.screen_portfolio_purchases_description, holding.assetSymbol))
        }
    }
}

@Composable
private fun ManualItemRow(
    item: ManualItemDraft,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.screen_portfolio_manual_item_summary, item.quantityText, item.value.formatMoneyOrDash()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.screen_portfolio_edit_item_description, item.name))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screen_portfolio_delete_item_description, item.name))
        }
    }
}
