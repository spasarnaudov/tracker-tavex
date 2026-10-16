package io.github.spasarnaudov.trackertavex.feature.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.model.Asset
import io.github.spasarnaudov.trackertavex.core.model.AssetPriceInterval
import io.github.spasarnaudov.trackertavex.core.model.ChartDefinition
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LineChart
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing

private object ChartsScreenDimens {

    val DialogMaxHeight = 420.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(viewModel: ChartsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editingChart by remember { mutableStateOf<ChartDefinition?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val genericErrorMessage = stringResource(R.string.common_error_generic)
    val addChartLabel = stringResource(R.string.screen_charts_add_chart_label)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_charts_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = addChartLabel)
            }
        },
    ) { padding ->
        when (state.status) {
            LoadStatus.LOADING -> FullScreenLoading(modifier = Modifier.padding(padding))
            LoadStatus.ERROR -> FullScreenError(
                message = state.errorMessage ?: genericErrorMessage,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            LoadStatus.EMPTY -> EmptyState(
                message = stringResource(R.string.screen_charts_empty_message),
                modifier = Modifier.padding(padding),
                actionLabel = addChartLabel,
                onAction = { showAddDialog = true },
            )

            LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.charts, key = { it.id }) { chart ->
                    Card(modifier = Modifier.fillMaxWidth().padding(AppSpacing.Medium)) {
                        Column(modifier = Modifier.padding(AppSpacing.MediumSmall)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(chart.title, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { editingChart = chart }) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.screen_charts_edit_description, chart.title),
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeChart(chart.id) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.screen_charts_remove_description, chart.title),
                                        )
                                    }
                                }
                            }
                            if (chart.id in state.chartLoading) {
                                CircularProgressIndicator(modifier = Modifier.padding(AppSpacing.Large))
                            } else {
                                LineChart(
                                    points = state.chartData[chart.id] ?: emptyList(),
                                    scrollable = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ChartEditDialog(
            assets = state.assets,
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { viewModel.addChart(it); showAddDialog = false },
        )
    }

    editingChart?.let { chart ->
        ChartEditDialog(
            assets = state.assets,
            initial = chart,
            onDismiss = { editingChart = null },
            onSave = { viewModel.updateChart(it); editingChart = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartEditDialog(
    assets: List<Asset>,
    initial: ChartDefinition?,
    onDismiss: () -> Unit,
    onSave: (ChartDefinition) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var assetId by remember { mutableStateOf(initial?.assetId) }
    var range by remember { mutableStateOf(initial?.range ?: ChartRange.ONE_MONTH) }
    var interval by remember { mutableStateOf(initial?.interval ?: AssetPriceInterval.DAILY) }
    var expanded by remember { mutableStateOf(false) }
    val portfolioValueLabel = stringResource(R.string.screen_charts_portfolio_value_option)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    stringResource(R.string.screen_charts_add_chart_label)
                } else {
                    stringResource(R.string.screen_charts_edit_chart_title)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = ChartsScreenDimens.DialogMaxHeight).verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.screen_charts_field_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = assets.firstOrNull { it.id == assetId }?.let { "${it.symbol} · ${it.name}" } ?: portfolioValueLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.screen_charts_data_source_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(portfolioValueLabel) }, onClick = { assetId = null; expanded = false })
                        assets.forEach { asset ->
                            DropdownMenuItem(
                                text = { Text("${asset.symbol} · ${asset.name}") },
                                onClick = { assetId = asset.id; expanded = false },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.ExtraSmall),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChartRange.entries.filter { it != ChartRange.CUSTOM }.forEach {
                        FilterChip(selected = range == it, onClick = { range = it }, label = { Text(it.label) })
                    }
                }
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.ExtraSmall),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AssetPriceInterval.entries.forEach {
                        FilterChip(selected = interval == it, onClick = { interval = it }, label = { Text(it.label) })
                    }
                }
            }
        },
        confirmButton = {
            val defaultChartTitle = stringResource(R.string.screen_charts_default_title)
            TextButton(
                onClick = {
                    onSave(
                        (initial ?: ChartDefinition(title = title, assetId = assetId, range = range, interval = interval)).copy(
                            title = title.ifBlank { defaultChartTitle },
                            assetId = assetId,
                            range = range,
                            interval = interval,
                        ),
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.common_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
    )
}
