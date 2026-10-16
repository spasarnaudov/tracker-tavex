package io.github.spasarnaudov.trackertavex.feature.portfolio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualItemEditScreen(
    clientKey: String?,
    portfolioViewModel: PortfolioViewModel,
    onDone: () -> Unit,
) {
    val portfolioState by portfolioViewModel.uiState.collectAsState()
    val existing = clientKey?.let { key -> portfolioState.manualItems.firstOrNull { it.clientKey == key } }
    val isNew = existing == null

    var name by remember(clientKey) { mutableStateOf(existing?.name ?: "") }
    var quantityText by remember(clientKey) { mutableStateOf(existing?.quantityText ?: "") }
    var unitPriceText by remember(clientKey) { mutableStateOf(existing?.unitPriceText ?: "") }
    var priceAssetId by remember(clientKey) { mutableStateOf(existing?.priceAssetId) }
    var includeInChart by remember(clientKey) { mutableStateOf(existing?.includeInChart ?: true) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val goldBuybackAssets = portfolioState.goldBuybackAssets
    val manualPriceLabel = stringResource(R.string.screen_manual_item_edit_manual_price_option)
    val selectedAssetLabel = goldBuybackAssets.firstOrNull { it.id == priceAssetId }?.let { "${it.symbol} · ${it.name}" }
        ?: manualPriceLabel
    val nameRequiredError = stringResource(R.string.screen_manual_item_edit_name_required_error)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) {
                            stringResource(R.string.screen_portfolio_add_manual_item)
                        } else {
                            stringResource(R.string.screen_manual_item_edit_title_edit)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_navigation_back))
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = {
                            clientKey?.let { portfolioViewModel.markManualItemForDeletion(it) }
                            onDone()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(AppSpacing.Large)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text(stringResource(R.string.screen_manual_item_edit_name_label)) },
                singleLine = true,
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = { Text(stringResource(R.string.screen_manual_item_edit_quantity_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.padding(top = AppSpacing.Small))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedAssetLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.screen_manual_item_edit_price_source_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(manualPriceLabel) }, onClick = { priceAssetId = null; expanded = false })
                    goldBuybackAssets.forEach { asset ->
                        DropdownMenuItem(
                            text = { Text("${asset.symbol} · ${asset.name}") },
                            onClick = { priceAssetId = asset.id; expanded = false },
                        )
                    }
                }
            }

            if (priceAssetId == null) {
                Spacer(modifier = Modifier.padding(top = AppSpacing.Small))
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = { Text(stringResource(R.string.screen_manual_item_edit_unit_price_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeInChart, onCheckedChange = { includeInChart = it })
                Text(stringResource(R.string.screen_manual_item_edit_include_in_chart_label))
            }

            Spacer(modifier = Modifier.padding(top = AppSpacing.Medium))
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = nameRequiredError
                        return@Button
                    }
                    portfolioViewModel.upsertManualItem(
                        (existing ?: ManualItemDraft.blank().copy(clientKey = clientKey ?: UUID.randomUUID().toString())).copy(
                            name = name.trim(),
                            quantityText = quantityText,
                            unitPriceText = unitPriceText,
                            priceAssetId = priceAssetId,
                            includeInChart = includeInChart,
                        ),
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_action_save))
            }
        }
    }
}
