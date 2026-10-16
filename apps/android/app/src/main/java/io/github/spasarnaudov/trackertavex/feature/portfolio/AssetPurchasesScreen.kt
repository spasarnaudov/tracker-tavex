package io.github.spasarnaudov.trackertavex.feature.portfolio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.model.AssetPurchase
import io.github.spasarnaudov.trackertavex.core.ui.components.ConfirmDialog
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.formatMoney
import io.github.spasarnaudov.trackertavex.ui.theme.AppSpacing
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPurchasesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AssetPurchasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val genericErrorMessage = stringResource(R.string.common_error_generic)
    var editingPurchase by remember { mutableStateOf<AssetPurchase?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var pendingReceiptPurchaseId by remember { mutableStateOf<Long?>(null) }
    var showReceiptChooser by remember { mutableStateOf(false) }
    var pendingDeleteReceiptId by remember { mutableStateOf<Long?>(null) }
    var previewReceiptPurchaseId by remember { mutableStateOf<Long?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val purchaseId = pendingReceiptPurchaseId
        val uri = cameraPhotoUri
        if (success && purchaseId != null && uri != null) {
            viewModel.uploadReceipt(purchaseId, uri)
        }
        cameraPhotoUri = null
        pendingReceiptPurchaseId = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val purchaseId = pendingReceiptPurchaseId
        if (uri != null && purchaseId != null) {
            viewModel.uploadReceipt(purchaseId, uri)
        }
        pendingReceiptPurchaseId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.assetSymbol} · ${state.assetName}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_navigation_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.screen_asset_purchases_add_description))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.status) {
                LoadStatus.LOADING -> FullScreenLoading()
                LoadStatus.ERROR -> FullScreenError(
                    message = state.errorMessage ?: genericErrorMessage,
                    onRetry = viewModel::load,
                )

                LoadStatus.EMPTY -> EmptyState(message = stringResource(R.string.screen_asset_purchases_empty_message))

                LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.Medium)) {
                    items(state.purchases, key = { it.id }) { purchase ->
                        PurchaseRow(
                            purchase = purchase,
                            imageLoader = viewModel.imageLoader,
                            receiptUrl = viewModel.receiptUrl(purchase.id),
                            onEdit = { editingPurchase = purchase },
                            onDelete = { pendingDeleteId = purchase.id },
                            onAddReceipt = { pendingReceiptPurchaseId = purchase.id; showReceiptChooser = true },
                            onViewReceipt = { previewReceiptPurchaseId = purchase.id },
                            onRemoveReceipt = { pendingDeleteReceiptId = purchase.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PurchaseFormDialog(
            title = stringResource(R.string.screen_asset_purchases_dialog_title_add),
            existing = null,
            isSaving = state.isSaving,
            formError = state.formError,
            onDismiss = { showAddDialog = false; viewModel.clearFormError() },
            onSave = { quantity, price, date ->
                viewModel.addPurchase(quantity, price, date) { showAddDialog = false }
            },
        )
    }

    editingPurchase?.let { purchase ->
        PurchaseFormDialog(
            title = stringResource(R.string.screen_asset_purchases_dialog_title_edit),
            existing = purchase,
            isSaving = state.isSaving,
            formError = state.formError,
            onDismiss = { editingPurchase = null; viewModel.clearFormError() },
            onSave = { quantity, price, date ->
                viewModel.updatePurchase(purchase.id, quantity, price, date) { editingPurchase = null }
            },
        )
    }

    pendingDeleteId?.let { purchaseId ->
        ConfirmDialog(
            title = stringResource(R.string.screen_asset_purchases_delete_confirm_title),
            text = stringResource(R.string.screen_asset_purchases_delete_confirm_message),
            confirmLabel = stringResource(R.string.common_action_delete),
            destructive = true,
            onConfirm = { viewModel.deletePurchase(purchaseId); pendingDeleteId = null },
            onDismiss = { pendingDeleteId = null },
        )
    }

    if (showReceiptChooser) {
        AlertDialog(
            onDismissRequest = { showReceiptChooser = false; pendingReceiptPurchaseId = null },
            title = { Text(stringResource(R.string.screen_asset_purchases_add_receipt_title)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showReceiptChooser = false
                        val uri = createTempPhotoUri(context)
                        cameraPhotoUri = uri
                        cameraLauncher.launch(uri)
                    }) { Text(stringResource(R.string.screen_asset_purchases_take_photo)) }
                    TextButton(onClick = {
                        showReceiptChooser = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.screen_asset_purchases_choose_from_gallery)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReceiptChooser = false; pendingReceiptPurchaseId = null }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    pendingDeleteReceiptId?.let { purchaseId ->
        ConfirmDialog(
            title = stringResource(R.string.screen_asset_purchases_delete_receipt_confirm_title),
            text = stringResource(R.string.screen_asset_purchases_delete_receipt_confirm_message),
            confirmLabel = stringResource(R.string.common_action_delete),
            destructive = true,
            onConfirm = { viewModel.removeReceipt(purchaseId); pendingDeleteReceiptId = null },
            onDismiss = { pendingDeleteReceiptId = null },
        )
    }

    previewReceiptPurchaseId?.let { purchaseId ->
        Dialog(onDismissRequest = { previewReceiptPurchaseId = null }) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = viewModel.receiptUrl(purchaseId),
                    imageLoader = viewModel.imageLoader,
                    contentDescription = stringResource(R.string.screen_asset_purchases_receipt_description),
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(onClick = { previewReceiptPurchaseId = null }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_action_cancel))
                }
            }
        }
    }
}

private fun createTempPhotoUri(context: Context): Uri {
    val directory = File(context.cacheDir, "receipt_photos").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private val purchaseDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
private fun PurchaseRow(
    purchase: AssetPurchase,
    imageLoader: ImageLoader,
    receiptUrl: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddReceipt: () -> Unit,
    onViewReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(purchase.purchaseDate.format(purchaseDateFormatter), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.screen_asset_purchases_row_summary,
                    purchase.quantity.toBigInteger().toString(),
                    purchase.purchasePrice.formatMoney(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.screen_asset_purchases_cost_value,
                    (purchase.quantity * purchase.purchasePrice).formatMoney(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            purchase.profit?.let { profit ->
                val profitColor = if (profit.signum() >= 0) PortfolioScreenColors.Positive else PortfolioScreenColors.Negative
                Text(
                    text = purchase.profitPercent?.let {
                        stringResource(R.string.screen_portfolio_profit_value, profit.formatMoney(), it.stripTrailingZeros().toPlainString())
                    } ?: stringResource(R.string.screen_portfolio_profit_value_no_percent, profit.formatMoney()),
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor,
                )
            }
        }
        if (purchase.hasReceipt) {
            AsyncImage(
                model = receiptUrl,
                imageLoader = imageLoader,
                contentDescription = stringResource(R.string.screen_asset_purchases_receipt_description),
                modifier = Modifier.size(48.dp).clickable(onClick = onViewReceipt),
            )
            IconButton(onClick = onRemoveReceipt) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screen_asset_purchases_delete_receipt_description))
            }
        } else {
            IconButton(onClick = onAddReceipt) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = stringResource(R.string.screen_asset_purchases_add_receipt_description))
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.screen_asset_purchases_edit_description))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screen_asset_purchases_delete_description))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseFormDialog(
    title: String,
    existing: AssetPurchase?,
    isSaving: Boolean,
    formError: String?,
    onDismiss: () -> Unit,
    onSave: (BigDecimal?, BigDecimal?, LocalDate?) -> Unit,
) {
    var quantityText by remember(existing) { mutableStateOf(existing?.quantity?.toBigInteger()?.toString() ?: "") }
    var priceText by remember(existing) { mutableStateOf(existing?.purchasePrice?.toPlainString() ?: "") }
    var date by remember(existing) { mutableStateOf(existing?.purchaseDate ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(stringResource(R.string.screen_asset_purchases_quantity_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.screen_asset_purchases_price_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.Small),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.Small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${stringResource(R.string.screen_asset_purchases_date_label)}: ${date.format(purchaseDateFormatter)}")
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.screen_asset_purchases_select_date_button))
                    }
                }
                formError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = { onSave(quantityText.toBigDecimalOrNull(), priceText.toBigDecimalOrNull(), date) },
            ) {
                Text(stringResource(R.string.common_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) }
        },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = millisToLocalDate(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (isBlank()) null else BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
