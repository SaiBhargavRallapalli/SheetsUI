package com.rsb.sheetsui.presentation.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rsb.sheetsui.domain.model.FieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRowScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddRowViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showScanReceipt by remember { mutableStateOf(false) }
    var showBarcodeScan by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AddRowUiState.Success) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add Row", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            viewModel.spreadsheetName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is AddRowUiState.Editing) {
                        IconButton(onClick = { showScanReceipt = true }) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = "Scan receipt")
                        }
                        IconButton(onClick = { showBarcodeScan = true }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            val active = state is AddRowUiState.Editing || state is AddRowUiState.Saving
            if (active) {
                FloatingActionButton(
                    onClick = { viewModel.save() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (state is AddRowUiState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Add row")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val current = state) {
                is AddRowUiState.Loading -> LoadingContent()
                is AddRowUiState.Editing -> AddRowForm(
                    fields = current.fields,
                    onFieldChange = viewModel::updateField,
                    enabled = true
                )
                is AddRowUiState.Saving -> {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        AddRowForm(
                            fields = current.fields,
                            onFieldChange = { _, _ -> },
                            enabled = false
                        )
                    }
                }
                is AddRowUiState.Success -> Unit
                is AddRowUiState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = { viewModel.retryLoad() }
                )
            }
        }
    }

    if (showScanReceipt) {
        com.rsb.sheetsui.presentation.scan.ScanReceiptOverlay(
            onResult = {
                viewModel.applyReceiptData(it)
                showScanReceipt = false
            },
            onDismiss = { showScanReceipt = false }
        )
    }
    if (showBarcodeScan) {
        com.rsb.sheetsui.presentation.scan.ScanBarcodeOverlay(
            onResult = {
                viewModel.applyBarcodeId(it)
                showBarcodeScan = false
            },
            onDismiss = { showBarcodeScan = false }
        )
    }
}

@Composable
private fun AddRowForm(
    fields: List<EditableField>,
    onFieldChange: (Int, String) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        fields.forEach { field ->
            when (field.type) {
                FieldType.DATE -> SmartDateField(
                    label = field.header, value = field.value,
                    onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                )
                FieldType.FORMULA -> SmartFormulaField(
                    label = field.header,
                    formulaValue = field.value,
                    displayValue = field.displayValue ?: field.value,
                    onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                )
                FieldType.CURRENCY -> SmartCurrencyField(
                    label = field.header,
                    value = field.value,
                    onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                )
                FieldType.NUMBER -> SmartNumberField(
                    label = field.header, value = field.value,
                    onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                )
                FieldType.BOOLEAN -> SmartBooleanField(
                    label = field.header, value = field.value,
                    onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                )
                FieldType.TEXT -> SmartTextField(
                    label = field.header, value = field.value,
                    onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                )
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading fields…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
